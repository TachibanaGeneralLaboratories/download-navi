/*
 * Copyright (C) 2019 Tachibana General Laboratories, LLC
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of Download Navi.
 *
 * Download Navi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Download Navi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Download Navi.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tachibana.downloader.ui.settings.sections;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import com.jaredrummler.android.colorpicker.ColorPreferenceCompat;
import com.tachibana.downloader.R;
import com.tachibana.downloader.core.settings.SettingsManager;
import com.takisoft.preferencex.PreferenceFragmentCompat;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

public class AppearanceSettingsFragment extends PreferenceFragmentCompat
        implements
        Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = AppearanceSettingsFragment.class.getSimpleName();

    private static final int REQUEST_CODE_ALERT_RINGTONE = 1;

    public static AppearanceSettingsFragment newInstance()
    {
        AppearanceSettingsFragment fragment = new AppearanceSettingsFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        SharedPreferences pref = SettingsManager.getInstance(getActivity()
                .getApplicationContext()).getPreferences();

        String keyTheme = getString(R.string.pref_key_theme);
        ListPreference theme = (ListPreference)findPreference(keyTheme);
        int type = pref.getInt(keyTheme, SettingsManager.Default.theme(getContext()));
        theme.setValueIndex(type);
        String[] typesName = getResources().getStringArray(R.array.pref_theme_entries);
        theme.setSummary(typesName[type]);
        bindOnPreferenceChangeListener(theme);

        String keyProgressNotify = getString(R.string.pref_key_progress_notify);
        SwitchPreferenceCompat progressNotify = (SwitchPreferenceCompat)findPreference(keyProgressNotify);
        progressNotify.setChecked(pref.getBoolean(keyProgressNotify, SettingsManager.Default.progressNotify));

        String keyFinishNotify = getString(R.string.pref_key_finish_notify);
        SwitchPreferenceCompat finishNotify = (SwitchPreferenceCompat)findPreference(keyFinishNotify);
        finishNotify.setChecked(pref.getBoolean(keyFinishNotify, SettingsManager.Default.finishNotify));

        String keyPendingNotify = getString(R.string.pref_key_pending_notify);
        SwitchPreferenceCompat pendingNotify = (SwitchPreferenceCompat)findPreference(keyPendingNotify);
        pendingNotify.setChecked(pref.getBoolean(keyPendingNotify, SettingsManager.Default.pendingNotify));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            initLegacyNotifySettings(pref);
    }

    /*
     * Note: starting with the version of Android 8.0,
     *       setting notifications from the app preferences isn't working,
     *       you can change them only in the settings of Android 8.0
     */

    private void initLegacyNotifySettings(SharedPreferences pref)
    {
        String keyPlaySound = getString(R.string.pref_key_play_sound_notify);
        SwitchPreferenceCompat playSound = (SwitchPreferenceCompat)findPreference(keyPlaySound);
        playSound.setChecked(pref.getBoolean(keyPlaySound, SettingsManager.Default.playSoundNotify));

        final String keyNotifySound = getString(R.string.pref_key_notify_sound);
        Preference notifySound = findPreference(keyNotifySound);
        String ringtone = pref.getString(keyNotifySound, SettingsManager.Default.notifySound);
        notifySound.setSummary(RingtoneManager.getRingtone(getActivity().getApplicationContext(), Uri.parse(ringtone))
                .getTitle(getActivity().getApplicationContext()));
        /* See https://code.google.com/p/android/issues/detail?id=183255 */
        notifySound.setOnPreferenceClickListener((preference) -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);

            String curRingtone = pref.getString(keyNotifySound, null);
            if (curRingtone != null) {
                if (curRingtone.length() == 0) {
                    /* Select "Silent" */
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri)null);
                } else {
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(curRingtone));
                }

            } else {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Settings.System.DEFAULT_NOTIFICATION_URI);
            }

            startActivityForResult(intent, REQUEST_CODE_ALERT_RINGTONE);

            return true;
        });

        String keyLedIndicator = getString(R.string.pref_key_led_indicator_notify);
        SwitchPreferenceCompat ledIndicator = (SwitchPreferenceCompat)findPreference(keyLedIndicator);
        ledIndicator.setChecked(pref.getBoolean(keyLedIndicator, SettingsManager.Default.ledIndicatorNotify));

        String keyLedIndicatorColor = getString(R.string.pref_key_led_indicator_color_notify);
        ColorPreferenceCompat ledIndicatorColor = (ColorPreferenceCompat)findPreference(keyLedIndicatorColor);
        ledIndicatorColor.saveValue(pref.getInt(keyLedIndicatorColor, SettingsManager.Default.ledIndicatorColorNotify(getContext())));

        String keyVibration = getString(R.string.pref_key_vibration_notify);
        SwitchPreferenceCompat vibration = (SwitchPreferenceCompat)findPreference(keyVibration);
        vibration.setChecked(pref.getBoolean(keyVibration, SettingsManager.Default.vibrationNotify));
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_appearance, rootKey);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_CODE_ALERT_RINGTONE && data != null) {
            Uri ringtone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (ringtone != null) {
                SharedPreferences pref = SettingsManager.getInstance(getActivity()
                        .getApplicationContext()).getPreferences();

                String keyNotifySound = getString(R.string.pref_key_notify_sound);
                Preference notifySound = findPreference(keyNotifySound);
                notifySound.setSummary(RingtoneManager.getRingtone(getActivity().getApplicationContext(), ringtone)
                        .getTitle(getActivity().getApplicationContext()));
                pref.edit().putString(keyNotifySound, ringtone.toString()).apply();
            }

        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        SharedPreferences pref = SettingsManager.getInstance(getActivity()
                .getApplicationContext()).getPreferences();

        if (preference.getKey().equals(getString(R.string.pref_key_theme))) {
            int type = Integer.parseInt((String)newValue);
            pref.edit().putInt(preference.getKey(), type).apply();
            String typesName[] = getResources().getStringArray(R.array.pref_theme_entries);
            preference.setSummary(typesName[type]);

            Toast.makeText(getActivity().getApplicationContext(),
                    R.string.theme_settings_apply_after_reboot,
                    Toast.LENGTH_SHORT)
                    .show();
        }

        return true;
    }
}
