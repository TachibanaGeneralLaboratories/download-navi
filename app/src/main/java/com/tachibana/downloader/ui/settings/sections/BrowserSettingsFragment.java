/*
 * Copyright (C) 2020 Tachibana General Laboratories, LLC
 * Copyright (C) 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebStorage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.snackbar.Snackbar;
import com.tachibana.downloader.R;
import com.tachibana.downloader.core.RepositoryHelper;
import com.tachibana.downloader.core.settings.SettingsRepository;
import com.tachibana.downloader.core.utils.Utils;
import com.takisoft.preferencex.EditTextPreference;
import com.takisoft.preferencex.PreferenceFragmentCompat;

public class BrowserSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = BrowserSettingsFragment.class.getSimpleName();

    private SettingsRepository pref;
    private CoordinatorLayout coordinatorLayout;

    public static BrowserSettingsFragment newInstance()
    {
        BrowserSettingsFragment fragment = new BrowserSettingsFragment();
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

        Context context = getActivity().getApplicationContext();
        pref = RepositoryHelper.getSettingsRepository(context);

        String keyBottomBar = getString(R.string.pref_key_browser_bottom_address_bar);
        SwitchPreferenceCompat bottomBar = findPreference(keyBottomBar);
        if (bottomBar != null) {
            bottomBar.setChecked(pref.browserBottomAddressBar());
            bindOnPreferenceChangeListener(bottomBar);
        }

        String keyStartPage = getString(R.string.pref_key_browser_start_page);
        EditTextPreference startPage = findPreference(keyStartPage);
        if (startPage != null) {
            String value = pref.browserStartPage();
            startPage.setSummary(value);
            startPage.setText(value);
            bindOnPreferenceChangeListener(startPage);
        }

        String keyAllowJavaScript = getString(R.string.pref_key_browser_allow_java_script);
        SwitchPreferenceCompat allowJavaScript = findPreference(keyAllowJavaScript);
        if (allowJavaScript != null) {
            allowJavaScript.setChecked(pref.browserAllowJavaScript());
            bindOnPreferenceChangeListener(allowJavaScript);
        }

        String keyAllowPopupWindows = getString(R.string.pref_key_browser_allow_popup_windows);
        SwitchPreferenceCompat allowPopupWindows = findPreference(keyAllowPopupWindows);
        if (allowPopupWindows != null) {
            allowPopupWindows.setChecked(pref.browserAllowPopupWindows());
            bindOnPreferenceChangeListener(allowPopupWindows);
        }

        String keyDoNotTrack = getString(R.string.pref_key_browser_do_not_track);
        SwitchPreferenceCompat doNotTrack = findPreference(keyDoNotTrack);
        if (doNotTrack != null) {
            doNotTrack.setChecked(pref.browserDoNotTrack());
            bindOnPreferenceChangeListener(doNotTrack);
        }

        String keyEnableCookies = getString(R.string.pref_key_browser_enable_cookies);
        SwitchPreferenceCompat enableCookies = findPreference(keyEnableCookies);
        if (enableCookies != null) {
            enableCookies.setChecked(pref.browserEnableCookies());
            bindOnPreferenceChangeListener(enableCookies);
        }

        Preference deleteCookies = findPreference("delete_cookies");
        if (deleteCookies != null) {
            deleteCookies.setOnPreferenceClickListener(__ -> {
                Utils.deleteCookies();
                Snackbar.make(coordinatorLayout,
                        R.string.pref_browser_delete_cookies_done,
                        Snackbar.LENGTH_SHORT)
                        .show();
                return true;
            });
        }

        String keyEnableCaching = getString(R.string.pref_key_browser_enable_caching);
        SwitchPreferenceCompat enableCaching = findPreference(keyEnableCaching);
        if (enableCaching != null) {
            enableCaching.setChecked(pref.browserEnableCaching());
            bindOnPreferenceChangeListener(enableCaching);
        }

        Preference clearCache = findPreference("clear_cache");
        if (clearCache != null) {
            clearCache.setOnPreferenceClickListener(__ -> {
                WebStorage.getInstance().deleteAllData();
                Snackbar.make(coordinatorLayout,
                        R.string.pref_browser_clear_cache_done,
                        Snackbar.LENGTH_SHORT)
                        .show();
                return true;
            });
        }

        String keyDisableFromSystem = getString(R.string.pref_key_browser_disable_from_system);
        SwitchPreferenceCompat disableFromSystem = findPreference(keyDisableFromSystem);
        if (disableFromSystem != null) {
            disableFromSystem.setChecked(pref.browserDisableFromSystem());
            bindOnPreferenceChangeListener(disableFromSystem);
        }

        String keyLauncherIcon = getString(R.string.pref_key_browser_launcher_icon);
        SwitchPreferenceCompat launcherIcon = findPreference(keyLauncherIcon);
        if (launcherIcon != null) {
            launcherIcon.setChecked(pref.browserLauncherIcon());
            bindOnPreferenceChangeListener(launcherIcon);
        }

        String keySearchEngine = getString(R.string.pref_key_browser_search_engine);
        ListPreference searchEngine = findPreference(keySearchEngine);
        if (searchEngine != null) {
            String searchUrl = pref.browserSearchEngine();
            searchEngine.setValue(searchUrl);
            bindOnPreferenceChangeListener(searchEngine);
        }

        String keyHideMenuIcon = getString(R.string.pref_key_browser_hide_menu_icon);
        SwitchPreferenceCompat hideMenuIcon = findPreference(keyHideMenuIcon);
        if (hideMenuIcon != null) {
            hideMenuIcon.setChecked(pref.browserHideMenuIcon());
            bindOnPreferenceChangeListener(hideMenuIcon);
        }
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_browser, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        if (preference.getKey().equals(getString(R.string.pref_key_browser_bottom_address_bar))) {
            pref.browserBottomAddressBar((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_browser_start_page))) {
            String url = (String)newValue;
            if (TextUtils.isEmpty(url))
                return false;

            if (!url.startsWith(Utils.HTTP_PREFIX))
                url = Utils.HTTP_PREFIX + "://" + url;
            pref.browserStartPage(url);
            preference.setSummary(url);

        } else if (preference.getKey().equals(getString(R.string.pref_key_browser_allow_java_script))) {
            pref.browserAllowJavaScript((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_browser_allow_popup_windows))) {
            pref.browserAllowPopupWindows((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_browser_do_not_track))) {
            pref.browserDoNotTrack((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_browser_enable_cookies))) {
            boolean enable = (boolean)newValue;
            pref.browserEnableCookies(enable);
            if (!enable)
                Utils.deleteCookies();

        } else if (preference.getKey().equals(getString(R.string.pref_key_browser_enable_caching))) {
            boolean enable = (boolean)newValue;
            pref.browserEnableCaching(enable);
            if (!enable)
                WebStorage.getInstance().deleteAllData();

        } else if (preference.getKey().equals(getString(R.string.pref_key_browser_disable_from_system))) {
            boolean disable = (boolean)newValue;
            pref.browserDisableFromSystem(disable);
            Utils.disableBrowserFromSystem(getContext().getApplicationContext(), disable);

        } else if (preference.getKey().equals(getString(R.string.pref_key_browser_launcher_icon))) {
            boolean enable = (boolean)newValue;
            pref.browserLauncherIcon(enable);
            Utils.enableBrowserLauncherIcon(getContext().getApplicationContext(), enable);

        } else if (preference.getKey().equals(getString(R.string.pref_key_browser_search_engine))) {
            pref.browserSearchEngine((String)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_browser_hide_menu_icon))) {
            pref.browserHideMenuIcon((boolean)newValue);
        }

        return true;
    }
}
