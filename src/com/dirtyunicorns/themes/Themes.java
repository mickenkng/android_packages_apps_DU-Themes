/*
 * Copyright (C) 2019 The Dirty Unicorns Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dirtyunicorns.themes;

import static android.os.UserHandle.USER_SYSTEM;

import android.app.ActionBar;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.UiModeManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.om.IOverlayManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.MenuItem;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import com.android.internal.util.pixeldust.PixeldustUtils;
import com.android.internal.util.pixeldust.ThemesUtils;

import com.dirtyunicorns.themes.db.ThemeDatabase;

import java.util.Objects;

public class Themes extends PreferenceFragment implements ThemesListener {

    private static final String PREF_BACKUP_THEMES = "backup_themes";
    private static final String PREF_RESTORE_THEMES = "restore_themes";
    public static final String PREF_ACCENT_PICKER = "accent_picker";
    public static final String PREF_ADAPTIVE_ICON_SHAPE = "adapative_icon_shape";
    public static final String PREF_FONT_PICKER = "font_picker";
    public static final String PREF_STATUSBAR_ICONS = "statusbar_icons";
    public static final String PREF_THEME_SWITCH = "theme_switch";
    private static final String PREF_WP_PREVIEW = "wp_preview";

    private static boolean mUseSharedPrefListener;
    private int mBackupLimit = 10;

    private Activity mActivity;
    private IOverlayManager mOverlayManager;
    private SharedPreferences mSharedPreferences;
    private ThemeDatabase mThemeDatabase;
    private UiModeManager mUiModeManager;

    private ListPreference mAdaptiveIconShape;
    private ListPreference mFontPicker;
    private ListPreference mStatusbarIcons;
    private ListPreference mThemeSwitch;
    private Preference mAccentPicker;
    private Preference mBackupThemes;
    private Preference mRestoreThemes;
    private Preference mWpPreview;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.themes);

        mActivity = getActivity();

        ActionBar actionBar = mActivity.getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mUiModeManager = getContext().getSystemService(UiModeManager.class);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        mSharedPreferences.registerOnSharedPreferenceChangeListener(mSharedPrefListener);
        mThemeDatabase = new ThemeDatabase(mActivity);

        mOverlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));

        mWpPreview = findPreference(PREF_WP_PREVIEW);

        mAccentPicker = findPreference(PREF_ACCENT_PICKER);
        mAccentPicker.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                FragmentManager manager = getFragmentManager();
                Fragment frag = manager.findFragmentByTag(AccentPicker.TAG_ACCENT_PICKER);
                if (frag != null) {
                    manager.beginTransaction().remove(frag).commit();
                }
                AccentPicker accentPickerFragment = new AccentPicker();
                accentPickerFragment.show(manager, AccentPicker.TAG_ACCENT_PICKER);
                return true;
            }
        });

        // Themes backup
        mBackupThemes = (Preference) findPreference(PREF_BACKUP_THEMES);
        assert mBackupThemes != null;
        mBackupThemes.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                FragmentManager manager = getFragmentManager();
                Fragment frag = manager.findFragmentByTag(BackupThemes.TAG_BACKUP_THEMES);
                if (frag != null) {
                    manager.beginTransaction().remove(frag).commit();
                }
                BackupThemes backupThemesFragment = new BackupThemes(Themes.this);
                backupThemesFragment.show(manager, BackupThemes.TAG_BACKUP_THEMES);
                return true;
            }
        });

        // Themes restore
        mRestoreThemes = (Preference) findPreference(PREF_RESTORE_THEMES);
        assert mRestoreThemes != null;
        mRestoreThemes.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(mActivity, RestoreThemes.class);
                if (intent != null) {
                    setSharedPrefListener(true);
                    startActivity(intent);
                }
                return true;
            }
        });

        mThemeSwitch = (ListPreference) findPreference(PREF_THEME_SWITCH);
        if (PixeldustUtils.isThemeEnabled("com.android.theme.solarizeddark.system")) {
            mThemeSwitch.setValue("4");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.pitchblack.system")) {
            mThemeSwitch.setValue("3");
        } else if (mUiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES) {
            mThemeSwitch.setValue("2");
        } else {
            mThemeSwitch.setValue("1");
        }
        mThemeSwitch.setSummary(mThemeSwitch.getEntry());

        mFontPicker = (ListPreference) findPreference(PREF_FONT_PICKER);
        if (PixeldustUtils.isThemeEnabled("com.android.theme.font.notoserifsource")) {
            mFontPicker.setValue("2");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.aclonicasource")) {
            mFontPicker.setValue("3");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.amarantesource")) {
            mFontPicker.setValue("4");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.bariolsource")) {
            mFontPicker.setValue("5");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.cagliostrosource")) {
            mFontPicker.setValue("6");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.comicsanssource")) {
            mFontPicker.setValue("7");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.coolstorysource")) {
            mFontPicker.setValue("8");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.firasans")) {
            mFontPicker.setValue("9");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.googlesans")) {
            mFontPicker.setValue("10");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.lgsmartgothicsource")) {
            mFontPicker.setValue("11");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.oneplusslate")) {
            mFontPicker.setValue("12");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.rosemarysource")) {
            mFontPicker.setValue("13");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.samsungone")) {
            mFontPicker.setValue("14");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.sanfrancisco")) {
            mFontPicker.setValue("15");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.sonysketchsource")) {
            mFontPicker.setValue("16");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.font.surfersource")) {
            mFontPicker.setValue("17");
        } else {
            mFontPicker.setValue("1");
        }
        mFontPicker.setSummary(mFontPicker.getEntry());

        mAdaptiveIconShape = (ListPreference) findPreference(PREF_ADAPTIVE_ICON_SHAPE);
        if (PixeldustUtils.isThemeEnabled("com.android.theme.icon.teardrop")) {
            mAdaptiveIconShape.setValue("2");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.icon.squircle")) {
            mAdaptiveIconShape.setValue("3");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.icon.roundedrect")) {
            mAdaptiveIconShape.setValue("4");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.icon.cylinder")) {
            mAdaptiveIconShape.setValue("5");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.icon.hexagon")) {
            mAdaptiveIconShape.setValue("6");
        } else {
            mAdaptiveIconShape.setValue("1");
        }
        mAdaptiveIconShape.setSummary(mAdaptiveIconShape.getEntry());

        mStatusbarIcons = (ListPreference) findPreference(PREF_STATUSBAR_ICONS);
        if (PixeldustUtils.isThemeEnabled("com.android.theme.icon_pack.filled.android")) {
            mStatusbarIcons.setValue("2");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.icon_pack.rounded.android")) {
            mStatusbarIcons.setValue("3");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.icon_pack.circular.android")) {
            mStatusbarIcons.setValue("4");
        } else {
            mStatusbarIcons.setValue("1");
        }
        mStatusbarIcons.setSummary(mStatusbarIcons.getEntry());

        setWallpaperPreview();
        updateBackupPref();
        updateRestorePref();
    }

    private void setWallpaperPreview() {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(getActivity());
        Drawable wallpaperDrawable = wallpaperManager.getDrawable();
        mWpPreview.setIcon(wallpaperDrawable);
    }

    private void updateBackupPref() {
        mBackupThemes.setEnabled(getThemeCount() < mBackupLimit ? true : false);
        if (getThemeCount() == mBackupLimit) {
            mBackupThemes.setSummary(R.string.theme_backup_reach_limit_summary);
        } else {
            mBackupThemes.setSummary(R.string.theme_backup_summary);
        }
    }

    private void updateRestorePref() {
        mRestoreThemes.setEnabled(getThemeCount() > 0 ? true : false);
        if (getThemeCount() == 0) {
            mRestoreThemes.setSummary(R.string.theme_restore_no_backup_summary);
        } else {
            mRestoreThemes.setSummary(R.string.theme_restore_summary);
        }
    }

    private int getThemeCount() {
        int count = mThemeDatabase.getThemeDbUtilsCount();
        return count;
    }

    public OnSharedPreferenceChangeListener mSharedPrefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            class PrepareData extends AsyncTask<Void, Void, Void> {

                protected Void doInBackground(Void... param) {
                    return null;
                }

                protected void onPostExecute(Void param) {
                }

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    String font_type = sharedPreferences.getString(PREF_FONT_PICKER, "1");
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.notoserifsource")) {
                        handleOverlays("com.android.theme.font.notoserifsource", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.aclonicasource")) {
                        handleOverlays("com.android.theme.font.aclonicasource", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.amarantesource")) {
                        handleOverlays("com.android.theme.font.amarantesource", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.bariolsource")) {
                        handleOverlays("com.android.theme.font.bariolsource", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.cagliostrosource")) {
                        handleOverlays("com.android.theme.font.cagliostrosource", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.comicsanssource")) {
                        handleOverlays("com.android.theme.font.comicsanssource", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.coolstorysource")) {
                        handleOverlays("com.android.theme.font.coolstorysource", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.firasans")) {
                        handleOverlays("com.android.theme.font.firasans", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.googlesans")) {
                        handleOverlays("com.android.theme.font.googlesans", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.lgsmartgothicsource")) {
                        handleOverlays("com.android.theme.font.lgsmartgothicsource", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.oneplusslate")) {
                        handleOverlays("com.android.theme.font.oneplusslate", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.rosemarysource")) {
                        handleOverlays("com.android.theme.font.rosemarysource", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.samsungone")) {
                        handleOverlays("com.android.theme.font.samsungone", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.sanfrancisco")) {
                        handleOverlays("com.android.theme.font.sanfrancisco", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.sonysketchsource")) {
                        handleOverlays("com.android.theme.font.sonysketchsource", false);
                    }
                    if (PixeldustUtils.isThemeEnabled("com.android.theme.font.surfersource")) {
                        handleOverlays("com.android.theme.font.surfersource", false);
                    }
                    switch (font_type) {
                        case "1":
                            for (int i = 0; i < ThemesUtils.FONTS.length; i++) {
                                String fonts = ThemesUtils.FONTS[i];
                                try {
                                    mOverlayManager.setEnabled(fonts, false, USER_SYSTEM);
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        case "2":
                        handleOverlays("com.android.theme.font.notoserifsource", true);
                        break;
                    case "3":
                        handleOverlays("com.android.theme.font.aclonicasource", true);
                        break;
                    case "4":
                        handleOverlays("com.android.theme.font.amarantesource", true);
                        break;
                    case "5":
                        handleOverlays("com.android.theme.font.bariolsource", true);
                        break;
                    case "6":
                        handleOverlays("com.android.theme.font.cagliostrosource", true);
                        break;
                    case "7":
                        handleOverlays("com.android.theme.font.comicsanssource", true);
                        break;
                    case "8":
                        handleOverlays("com.android.theme.font.coolstorysource", true);
                        break;
                    case "9":
                        handleOverlays("com.android.theme.font.firasans", true);
                        break;
                    case "10":
                        handleOverlays("com.android.theme.font.googlesans", true);
                        break;
                    case "11":
                        handleOverlays("com.android.theme.font.lgsmartgothicsource", true);
                        break;
                    case "12":
                        handleOverlays("com.android.theme.font.oneplusslate", true);
                        break;
                    case "13":
                        handleOverlays("com.android.theme.font.rosemarysource", true);
                        break;
                    case "14":
                        handleOverlays("com.android.theme.font.samsungone", true);
                        break;
                    case "15":
                        handleOverlays("com.android.theme.font.sanfrancisco", true);
                        break;
                    case "16":
                        handleOverlays("com.android.theme.font.sonysketchsource", true);
                        break;
                    case "17":
                        handleOverlays("com.android.theme.font.surfersource", true);
                        break;
                    }
                    mFontPicker.setSummary(mFontPicker.getEntry());
                }
            }

            if (key.equals(PREF_FONT_PICKER)) {
                new PrepareData().execute();
            }

            if (key.equals(PREF_ADAPTIVE_ICON_SHAPE)) {
                String adapative_icon_shape = sharedPreferences.getString(PREF_ADAPTIVE_ICON_SHAPE, "1");

                handleOverlays("com.android.theme.icon.teardrop", false);
                handleOverlays("com.android.theme.icon.squircle", false);
                handleOverlays("com.android.theme.icon.roundedrect", false);
                handleOverlays("com.android.theme.icon.cylinder", false);
                handleOverlays("com.android.theme.icon.hexagon", false);

                switch (adapative_icon_shape) {
                    case "2":
                        handleOverlays("com.android.theme.icon.teardrop", true);
                        break;
                    case "3":
                        handleOverlays("com.android.theme.icon.squircle", true);
                        break;
                    case "4":
                        handleOverlays("com.android.theme.icon.roundedrect", true);
                        break;
                    case "5":
                        handleOverlays("com.android.theme.icon.cylinder", true);
                        break;
                    case "6":
                        handleOverlays("com.android.theme.icon.hexagon", true);
                        break;
                }
                mAdaptiveIconShape.setSummary(mAdaptiveIconShape.getEntry());
            }

            if (key.equals(PREF_STATUSBAR_ICONS)) {
                String statusbar_icons = sharedPreferences.getString(PREF_STATUSBAR_ICONS, "1");
                switch (statusbar_icons) {
                    case "1":
                        handleOverlays("com.android.theme.icon_pack.filled.android", false);
                        handleOverlays("com.android.theme.icon_pack.rounded.android", false);
                        handleOverlays("com.android.theme.icon_pack.circular.android", false);
                        break;
                    case "2":
                        handleOverlays("com.android.theme.icon_pack.filled.android", true);
                        handleOverlays("com.android.theme.icon_pack.rounded.android", false);
                        handleOverlays("com.android.theme.icon_pack.circular.android", false);
                        break;
                    case "3":
                        handleOverlays("com.android.theme.icon_pack.filled.android", false);
                        handleOverlays("com.android.theme.icon_pack.rounded.android", true);
                        handleOverlays("com.android.theme.icon_pack.circular.android", false);
                        break;
                    case "4":
                        handleOverlays("com.android.theme.icon_pack.filled.android", false);
                        handleOverlays("com.android.theme.icon_pack.rounded.android", false);
                        handleOverlays("com.android.theme.icon_pack.circular.android", true);
                        break;
                }
                mStatusbarIcons.setSummary(mStatusbarIcons.getEntry());
            }

            if (key.equals(PREF_THEME_SWITCH)) {
                String theme_switch = sharedPreferences.getString(PREF_THEME_SWITCH, "1");
                switch (theme_switch) {
                    case "1":
                        handleBackgrounds(false, mActivity, UiModeManager.MODE_NIGHT_NO, ThemesUtils.PITCH_BLACK);
                        handleBackgrounds(false, mActivity, UiModeManager.MODE_NIGHT_NO, ThemesUtils.SOLARIZED_DARK);
                        break;
                    case "2":
                        handleBackgrounds(false, mActivity, UiModeManager.MODE_NIGHT_YES, ThemesUtils.PITCH_BLACK);
                        handleBackgrounds(false, mActivity, UiModeManager.MODE_NIGHT_YES, ThemesUtils.SOLARIZED_DARK);
                        break;
                    case "3":
                        handleBackgrounds(true, mActivity, UiModeManager.MODE_NIGHT_YES, ThemesUtils.PITCH_BLACK);
                        handleBackgrounds(false, mActivity, UiModeManager.MODE_NIGHT_YES, ThemesUtils.SOLARIZED_DARK);
                        break;
                    case "4":
                        handleBackgrounds(false, mActivity, UiModeManager.MODE_NIGHT_YES, ThemesUtils.PITCH_BLACK);
                        handleBackgrounds(true, mActivity, UiModeManager.MODE_NIGHT_YES, ThemesUtils.SOLARIZED_DARK);
                        break;
                }
                mThemeSwitch.setSummary(mThemeSwitch.getEntry());
            }
        }
    };

    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    }

    public static void setSharedPrefListener(boolean listener) {
        mUseSharedPrefListener = listener;
    }

    @Override
    public void onCloseBackupDialog(DialogFragment dialog) {
        updateBackupPref();
        updateRestorePref();
    }

    @Override
    public void onResume() {
        super.onResume();
        mSharedPreferences.registerOnSharedPreferenceChangeListener(mSharedPrefListener);
        setWallpaperPreview();
        updateBackupPref();
        updateRestorePref();
        updateAccentSummary();
        updateIconShapeSummary();
        updateStatusbarIconsSummary();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!mUseSharedPrefListener) {
            mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mSharedPrefListener);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!mUseSharedPrefListener) {
            mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mSharedPrefListener);
        }
    }

    private void updateAccentSummary() {
        if (PixeldustUtils.isThemeEnabled("com.android.theme.color.space")) {
            mAccentPicker.setSummary("Space");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.purple")) {
            mAccentPicker.setSummary("Purple");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.orchid")) {
            mAccentPicker.setSummary("Orchid");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.ocean")) {
            mAccentPicker.setSummary("Ocean");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.green")) {
            mAccentPicker.setSummary("Green");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.cinnamon")) {
            mAccentPicker.setSummary("Cinnamon");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.amber")) {
            mAccentPicker.setSummary("Amber");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.blue")) {
            mAccentPicker.setSummary("Blue");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.bluegrey")) {
            mAccentPicker.setSummary("Blue Grey");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.brown")) {
            mAccentPicker.setSummary("Brown");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.cyan")) {
            mAccentPicker.setSummary("Cyan");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.deeporange")) {
            mAccentPicker.setSummary("Deep Orange");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.deeppurple")) {
            mAccentPicker.setSummary("Deep Purple");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.grey")) {
            mAccentPicker.setSummary("Grey");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.indigo")) {
            mAccentPicker.setSummary("Indigo");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.lightblue")) {
            mAccentPicker.setSummary("Light Blue");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.lightgreen")) {
            mAccentPicker.setSummary("Light Green");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.lime")) {
            mAccentPicker.setSummary("Lime");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.orange")) {
            mAccentPicker.setSummary("Orange");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.pink")) {
            mAccentPicker.setSummary("Pink");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.red")) {
            mAccentPicker.setSummary("Red");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.teal")) {
            mAccentPicker.setSummary("Teal");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.yellow")) {
            mAccentPicker.setSummary("Yellow");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.androidonegreen")) {
            mAccentPicker.setSummary("AndroidOneGreen");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.cocacolared")) {
            mAccentPicker.setSummary("CocaColaRed");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.discordpurple")) {
            mAccentPicker.setSummary("DiscordPurple");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.facebookblue")) {
            mAccentPicker.setSummary("FacebookBlue");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.instagramcerise")) {
            mAccentPicker.setSummary("InstagramCerise");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.jollibeecrimson")) {
            mAccentPicker.setSummary("JollibeeCrimson");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.monsterenergygreen")) {
            mAccentPicker.setSummary("MonsterEnergyGreen");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.nextbitmint")) {
            mAccentPicker.setSummary("NextbitMint");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.oneplusred")) {
            mAccentPicker.setSummary("OneplusRed");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.pepsiblue")) {
            mAccentPicker.setSummary("PepsiBlue");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.pocophoneyellow")) {
            mAccentPicker.setSummary("PocophoneYellow");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.razergreen")) {
            mAccentPicker.setSummary("RazerGreen");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.samsungblue")) {
            mAccentPicker.setSummary("SamsungBlue");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.spotifygreen")) {
            mAccentPicker.setSummary("SpotifyGreen");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.starbucksgreen")) {
            mAccentPicker.setSummary("StarbucksGreen");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.twitchpurple")) {
            mAccentPicker.setSummary("TwitchPurple");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.twitterblue")) {
            mAccentPicker.setSummary("TwitterBlue");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.xboxgreen")) {
            mAccentPicker.setSummary("XboxGreen");
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.color.xiaomiorange")) {
            mAccentPicker.setSummary("XiaomiOrange");
        } else {
            mAccentPicker.setSummary(getString(R.string.theme_accent_picker_default));
        }
    }

    private void updateIconShapeSummary() {
        if (PixeldustUtils.isThemeEnabled("com.android.theme.icon.teardrop")) {
            mAdaptiveIconShape.setSummary(getString(R.string.adaptive_icon_shape_teardrop));
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.icon.squircle")) {
            mAdaptiveIconShape.setSummary(getString(R.string.adaptive_icon_shape_squircle));
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.icon.roundedrect")) {
            mAdaptiveIconShape.setSummary(getString(R.string.adaptive_icon_shape_roundedrect));
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.icon.cylinder")) {
            mAdaptiveIconShape.setSummary(getString(R.string.adaptive_icon_shape_cylinder));
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.icon.hexagon")) {
            mAdaptiveIconShape.setSummary(getString(R.string.adaptive_icon_shape_hexagon));
        } else {
            mAdaptiveIconShape.setSummary(getString(R.string.adaptive_icon_shape_default));
        }
    }

    private void updateStatusbarIconsSummary() {
        if (PixeldustUtils.isThemeEnabled("com.android.theme.icon_pack.filled.android")) {
            mStatusbarIcons.setSummary(getString(R.string.statusbar_icons_filled));
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.icon_pack.rounded.android")) {
            mStatusbarIcons.setSummary(getString(R.string.statusbar_icons_rounded));
        } else if (PixeldustUtils.isThemeEnabled("com.android.theme.icon_pack.circular.android")) {
            mStatusbarIcons.setSummary(getString(R.string.statusbar_icons_circular));
        } else {
            mStatusbarIcons.setSummary(getString(R.string.statusbar_icons_default));
        }
    }

    private void handleBackgrounds(Boolean state, Context context, int mode, String[] overlays) {
        if (context != null) {
            Objects.requireNonNull(context.getSystemService(UiModeManager.class))
                    .setNightMode(mode);
        }
        for (int i = 0; i < overlays.length; i++) {
            String background = overlays[i];
            try {
                mOverlayManager.setEnabled(background, state, USER_SYSTEM);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleOverlays(String packagename, Boolean state) {
        try {
            mOverlayManager.setEnabled(packagename,
                    state, USER_SYSTEM);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            mActivity.finish();
            return true;
        }
        return false;
    }
}
