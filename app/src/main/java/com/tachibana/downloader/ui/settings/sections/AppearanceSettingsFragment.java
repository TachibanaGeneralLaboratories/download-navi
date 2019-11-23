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
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.snackbar.Snackbar;
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat;
import com.tachibana.downloader.R;
import com.tachibana.downloader.core.RepositoryHelper;
import com.tachibana.downloader.core.settings.SettingsRepository;
import com.tachibana.downloader.ui.main.MainActivity;
import com.takisoft.preferencex.PreferenceFragmentCompat;

public class AppearanceSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = AppearanceSettingsFragment.class.getSimpleName();

    private static final int REQUEST_CODE_ALERT_RINGTONE = 1;

    private SettingsRepository pref;
    private CoordinatorLayout coordinatorLayout;

    public static AppearanceSettingsFragment newInstance()
    {
        AppearanceSettingsFragment fragment = new AppearanceSettingsFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        coordinatorLayout = view.findViewById(R.id.coordinator_layout);
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        pref = RepositoryHelper.getSettingsRepository(getActivity().getApplicationContext());

        String keyTheme = getString(R.string.pref_key_theme);
        ListPreference theme = findPreference(keyTheme);
        if (theme != null) {
            int type = pref.theme();
            theme.setValueIndex(type);
            String[] typesName = getResources().getStringArray(R.array.pref_theme_entries);
            theme.setSummary(typesName[type]);
            bindOnPreferenceChangeListener(theme);
        }

        String keyProgressNotify = getString(R.string.pref_key_progress_notify);
        SwitchPreferenceCompat progressNotify = findPreference(keyProgressNotify);
        if (progressNotify != null) {
            progressNotify.setChecked(pref.progressNotify());
            bindOnPreferenceChangeListener(progressNotify);
        }

        String keyFinishNotify = getString(R.string.pref_key_finish_notify);
        SwitchPreferenceCompat finishNotify = findPreference(keyFinishNotify);
        if (finishNotify != null) {
            finishNotify.setChecked(pref.finishNotify());
            bindOnPreferenceChangeListener(finishNotify);
        }

        String keyPendingNotify = getString(R.string.pref_key_pending_notify);
        SwitchPreferenceCompat pendingNotify = findPreference(keyPendingNotify);
        if (pendingNotify != null) {
            pendingNotify.setChecked(pref.pendingNotify());
            bindOnPreferenceChangeListener(pendingNotify);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            initLegacyNotifySettings(pref);
    }

    /*
     * Note: starting with the version of Android 8.0,
     *       setting notifications from the app preferences isn't working,
     *       you can change them only in the settings of Android 8.0
     */

    private void initLegacyNotifySettings(SettingsRepository pref)
    {
        String keyPlaySound = getString(R.string.pref_key_play_sound_notify);
        SwitchPreferenceCompat playSound = findPreference(keyPlaySound);
        if (playSound != null) {
            playSound.setChecked(pref.playSoundNotify());
            bindOnPreferenceChangeListener(playSound);
        }

        final String keyNotifySound = getString(R.string.pref_key_notify_sound);
        Preference notifySound = findPreference(keyNotifySound);
        String ringtone = pref.notifySound();
        if (notifySound != null) {
            notifySound.setSummary(RingtoneManager.getRingtone(getActivity().getApplicationContext(), Uri.parse(ringtone))
                    .getTitle(getActivity().getApplicationContext()));
            /* See https://code.google.com/p/android/issues/detail?id=183255 */
            notifySound.setOnPreferenceClickListener((preference) -> {
                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);

                String curRingtone = pref.notifySound();
                if (curRingtone != null) {
                    if (curRingtone.length() == 0) {
                        /* Select "Silent" */
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri) null);
                    } else {
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(curRingtone));
                    }

                } else {
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Settings.System.DEFAULT_NOTIFICATION_URI);
                }

                startActivityForResult(intent, REQUEST_CODE_ALERT_RINGTONE);

                return true;
            });
        }

        String keyLedIndicator = getString(R.string.pref_key_led_indicator_notify);
        SwitchPreferenceCompat ledIndicator = findPreference(keyLedIndicator);
        if (ledIndicator != null) {
            ledIndicator.setChecked(pref.ledIndicatorNotify());
            bindOnPreferenceChangeListener(ledIndicator);
        }

        String keyLedIndicatorColor = getString(R.string.pref_key_led_indicator_color_notify);
        ColorPreferenceCompat ledIndicatorColor = findPreference(keyLedIndicatorColor);
        if (ledIndicatorColor != null) {
            ledIndicatorColor.saveValue(pref.ledIndicatorColorNotify());
            bindOnPreferenceChangeListener(ledIndicatorColor);
        }

        String keyVibration = getString(R.string.pref_key_vibration_notify);
        SwitchPreferenceCompat vibration = findPreference(keyVibration);
        if (vibration != null) {
            vibration.setChecked(pref.vibrationNotify());
            bindOnPreferenceChangeListener(vibration);
        }
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
                String keyNotifySound = getString(R.string.pref_key_notify_sound);
                Preference notifySound = findPreference(keyNotifySound);
                if (notifySound != null)
                    notifySound.setSummary(RingtoneManager.getRingtone(getActivity().getApplicationContext(), ringtone)
                            .getTitle(getActivity().getApplicationContext()));
                pref.notifySound(ringtone.toString());
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
        if (preference.getKey().equals(getString(R.string.pref_key_theme))) {
            int type = Integer.parseInt((String)newValue);
            pref.theme(type);
            String[] typesName = getResources().getStringArray(R.array.pref_theme_entries);
            preference.setSummary(typesName[type]);

            Snackbar.make(coordinatorLayout,
                    R.string.theme_settings_apply_after_reboot,
                    Snackbar.LENGTH_LONG)
                    .setAction(R.string.apply, (v) -> restartMainActivity())
                    .show();

        } else if (preference.getKey().equals(getString(R.string.pref_key_finish_notify))) {
            pref.finishNotify((boolean)newValue);

        }  else if (preference.getKey().equals(getString(R.string.pref_key_progress_notify))) {
            pref.progressNotify((boolean)newValue);

        }  else if (preference.getKey().equals(getString(R.string.pref_key_pending_notify))) {
            pref.pendingNotify((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_play_sound_notify))) {
            pref.playSoundNotify((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_led_indicator_notify))) {
            pref.ledIndicatorNotify((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_vibration_notify))) {
            pref.vibrationNotify((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_led_indicator_color_notify))) {
            pref.ledIndicatorColorNotify((int)newValue);
        }

        return true;
    }

    private void restartMainActivity()
    {
        Intent intent = new Intent(getActivity().getApplicationContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
