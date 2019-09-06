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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;

import com.tachibana.downloader.core.InputFilterMinMax;
import com.tachibana.downloader.R;
import com.tachibana.downloader.core.settings.SettingsManager;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.ui.BaseAlertDialog;
import com.takisoft.preferencex.EditTextPreference;
import com.takisoft.preferencex.PreferenceFragmentCompat;

import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.Preference;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class BehaviorSettingsFragment extends PreferenceFragmentCompat
        implements
        Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = BehaviorSettingsFragment.class.getSimpleName();

    private static final String TAG_CUSTOM_BATTERY_DIALOG = "custom_battery_dialog";
    private CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog.SharedViewModel dialogViewModel;

    public static BehaviorSettingsFragment newInstance()
    {
        BehaviorSettingsFragment fragment = new BehaviorSettingsFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        dialogViewModel = ViewModelProviders.of(getActivity()).get(BaseAlertDialog.SharedViewModel.class);

        SharedPreferences pref = SettingsManager.getInstance(getActivity().getApplicationContext())
                .getPreferences();

        String keyAutostart = getString(R.string.pref_key_autostart);
        SwitchPreferenceCompat autostart = (SwitchPreferenceCompat)findPreference(keyAutostart);
        autostart.setChecked(pref.getBoolean(keyAutostart, SettingsManager.Default.autostart));
        bindOnPreferenceChangeListener(autostart);

        String keyCpuSleep = getString(R.string.pref_key_cpu_do_not_sleep);
        SwitchPreferenceCompat cpuSleep = (SwitchPreferenceCompat)findPreference(keyCpuSleep);
        cpuSleep.setChecked(pref.getBoolean(keyCpuSleep, SettingsManager.Default.cpuDoNotSleep));

        String keyOnlyCharging = getString(R.string.pref_key_download_only_when_charging);
        SwitchPreferenceCompat onlyCharging = (SwitchPreferenceCompat)findPreference(keyOnlyCharging);
        onlyCharging.setChecked(pref.getBoolean(keyOnlyCharging, SettingsManager.Default.onlyCharging));
        bindOnPreferenceChangeListener(onlyCharging);

        String keyBatteryControl = getString(R.string.pref_key_battery_control);
        SwitchPreferenceCompat batteryControl = (SwitchPreferenceCompat)findPreference(keyBatteryControl);
        batteryControl.setSummary(String.format(getString(R.string.pref_battery_control_summary),
                                  Utils.getDefaultBatteryLowLevel()));
        batteryControl.setChecked(pref.getBoolean(keyBatteryControl, SettingsManager.Default.batteryControl));
        bindOnPreferenceChangeListener(batteryControl);

        String keyCustomBatteryControl = getString(R.string.pref_key_custom_battery_control);
        SwitchPreferenceCompat customBatteryControl = (SwitchPreferenceCompat)findPreference(keyCustomBatteryControl);
        customBatteryControl.setSummary(String.format(getString(R.string.pref_custom_battery_control_summary),
                Utils.getDefaultBatteryLowLevel()));
        customBatteryControl.setChecked(pref.getBoolean(keyCustomBatteryControl, SettingsManager.Default.customBatteryControl));
        bindOnPreferenceChangeListener(customBatteryControl);

        String keyCustomBatteryControlValue = getString(R.string.pref_key_custom_battery_control_value);
        SeekBarPreference customBatteryControlValue = (SeekBarPreference)findPreference(keyCustomBatteryControlValue);
        customBatteryControlValue.setValue(pref.getInt(keyCustomBatteryControlValue, Utils.getDefaultBatteryLowLevel()));
        customBatteryControlValue.setMin(10);
        customBatteryControlValue.setMax(90);

        String keyUnmeteredOnly = getString(R.string.pref_key_umnetered_connections_only);
        SwitchPreferenceCompat unmeteredOnly = (SwitchPreferenceCompat)findPreference(keyUnmeteredOnly);
        unmeteredOnly.setChecked(pref.getBoolean(keyUnmeteredOnly, SettingsManager.Default.unmeteredConnectionsOnly));

        String keyRoaming = getString(R.string.pref_key_enable_roaming);
        SwitchPreferenceCompat roaming = (SwitchPreferenceCompat)findPreference(keyRoaming);
        roaming.setChecked(pref.getBoolean(keyRoaming, SettingsManager.Default.enableRoaming));

        String keyMaxActiveDownloads = getString(R.string.pref_key_max_active_downloads);
        EditTextPreference maxActiveDownloads  = (EditTextPreference)findPreference(keyMaxActiveDownloads);
        String value = Integer.toString(pref.getInt(keyMaxActiveDownloads, SettingsManager.Default.maxActiveDownloads));
        maxActiveDownloads.getEditText().setFilters(new InputFilter[]{ new InputFilterMinMax(1, Integer.MAX_VALUE) });
        maxActiveDownloads.setSummary(value);
        maxActiveDownloads.setText(value);
        bindOnPreferenceChangeListener(maxActiveDownloads);

        String keyMaxDownloadRetries = getString(R.string.pref_key_max_download_retries);
        EditTextPreference maxDownloadRetries  = (EditTextPreference)findPreference(keyMaxDownloadRetries);
        value = Integer.toString(pref.getInt(keyMaxDownloadRetries, SettingsManager.Default.maxDownloadRetries));
        maxDownloadRetries.getEditText().setFilters(new InputFilter[]{ new InputFilterMinMax(0, Integer.MAX_VALUE) });
        maxDownloadRetries.setSummary(value);
        maxDownloadRetries.setText(value);
        maxDownloadRetries.setDialogMessage(R.string.pref_max_download_retries_dialog_msg);
        bindOnPreferenceChangeListener(maxDownloadRetries);

        String keyReplaceDuplicateDownloads = getString(R.string.pref_key_replace_duplicate_downloads);
        SwitchPreferenceCompat replaceDuplicateDownloads = (SwitchPreferenceCompat)findPreference(keyReplaceDuplicateDownloads);
        replaceDuplicateDownloads.setChecked(pref.getBoolean(keyReplaceDuplicateDownloads,
                                                             SettingsManager.Default.replaceDuplicateDownloads));

        String keyAutoConnect = getString(R.string.pref_key_auto_connect);
        SwitchPreferenceCompat autoConnect = (SwitchPreferenceCompat)findPreference(keyAutoConnect);
        autoConnect.setChecked(pref.getBoolean(keyAutoConnect, SettingsManager.Default.autoConnect));
    }

    @Override
    public void onStop()
    {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAlertDialog();
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (!event.dialogTag.equals(TAG_CUSTOM_BATTERY_DIALOG))
                        return;
                    if (event.type == BaseAlertDialog.EventType.NEGATIVE_BUTTON_CLICKED)
                        disableCustomBatteryControl();
                });
        disposables.add(d);
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_behavior, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue)
    {
        SharedPreferences pref = SettingsManager.getInstance(getActivity().getApplicationContext())
                .getPreferences();
        if (preference instanceof SwitchPreferenceCompat) {
            if (preference.getKey().equals(getString(R.string.pref_key_autostart))) {
                Utils.enableBootReceiver(getActivity(), (boolean)newValue);

            } else if(preference.getKey().equals(getString(R.string.pref_key_download_only_when_charging))) {
                if(!((SwitchPreferenceCompat) preference).isChecked())
                    disableBatteryControl();

            } else if(preference.getKey().equals(getString(R.string.pref_key_battery_control))) {
                if(((SwitchPreferenceCompat) preference).isChecked())
                    disableCustomBatteryControl();

            } else if(preference.getKey().equals(getString(R.string.pref_key_custom_battery_control))) {
                if (!((SwitchPreferenceCompat) preference).isChecked())
                    showCustomBatteryDialog();
            }

        } else if (preference.getKey().equals(getString(R.string.pref_key_max_active_downloads))) {
            int value = 1;
            if (!TextUtils.isEmpty((String)newValue))
                value = Integer.parseInt((String)newValue);
            pref.edit().putInt(preference.getKey(), value).apply();
            preference.setSummary(Integer.toString(value));

        } else if (preference.getKey().equals(getString(R.string.pref_key_max_download_retries))) {
            int value = 0;
            if (!TextUtils.isEmpty((String)newValue))
                value = Integer.parseInt((String)newValue);
            pref.edit().putInt(preference.getKey(), value).apply();
            preference.setSummary(Integer.toString(value));
        }

        return true;
    }

    private void showCustomBatteryDialog()
    {
        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_CUSTOM_BATTERY_DIALOG) == null) {
            BaseAlertDialog customBatteryDialog = BaseAlertDialog.newInstance(
                    getString(R.string.warning),
                    getString(R.string.pref_custom_battery_control_dialog_summary),
                    0,
                    getString(R.string.yes),
                    getString(R.string.no),
                    null,
                    true);

            customBatteryDialog.show(fm, TAG_CUSTOM_BATTERY_DIALOG);
        }
    }

    private void disableBatteryControl()
    {
        SharedPreferences pref = SettingsManager.getInstance(getActivity().getApplicationContext())
                .getPreferences();

        String keyBatteryControl = getString(R.string.pref_key_battery_control);
        SwitchPreferenceCompat batteryControl = (SwitchPreferenceCompat)findPreference(keyBatteryControl);
        batteryControl.setChecked(false);
        pref.edit().putBoolean(batteryControl.getKey(), false).apply();
        disableCustomBatteryControl();
    }

    private void disableCustomBatteryControl()
    {
        SharedPreferences pref = SettingsManager.getInstance(getActivity().getApplicationContext())
                .getPreferences();

        String keyCustomBatteryControl = getString(R.string.pref_key_custom_battery_control);
        SwitchPreferenceCompat batteryControl = (SwitchPreferenceCompat)findPreference(keyCustomBatteryControl);
        batteryControl.setChecked(false);
        pref.edit().putBoolean(batteryControl.getKey(), false).apply();
    }
}
