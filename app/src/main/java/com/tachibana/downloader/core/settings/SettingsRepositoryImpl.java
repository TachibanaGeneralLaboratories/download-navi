/*
 * Copyright (C) 2019-2022 Tachibana General Laboratories, LLC
 * Copyright (C) 2019-2022 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.core.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.HttpConnection;
import com.tachibana.downloader.core.system.SystemFacadeHelper;
import com.tachibana.downloader.core.utils.UserAgentUtils;
import com.tachibana.downloader.core.utils.Utils;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposables;

public class SettingsRepositoryImpl implements SettingsRepository
{
    private final Context appContext;
    private final SharedPreferences pref;

    private static class Default
    {
        /* Appearance settings */
        static int theme(@NonNull Context context)
        {
            return Integer.parseInt(context.getString(R.string.pref_theme_light_value));
        }
        static final boolean progressNotify = true;
        static final boolean finishNotify = true;
        static final boolean pendingNotify = true;
        static final String notifySound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString();
        static final boolean playSoundNotify = true;
        static final boolean ledIndicatorNotify = true;
        static final boolean vibrationNotify = true;
        static int ledIndicatorColorNotify(@NonNull Context context)
        {
            return ContextCompat.getColor(context, R.color.primary);
        }
        /* Behavior settings */
        static final boolean unmeteredConnectionsOnly = false;
        static final boolean enableRoaming = true;
        static final boolean autostart = false;
        static final boolean cpuDoNotSleep = false;
        static final boolean onlyCharging = false;
        static final boolean batteryControl = false;
        static final boolean customBatteryControl = false;
        static final int customBatteryControlValue = Utils.getDefaultBatteryLowLevel();
        static final int timeout = HttpConnection.DEFAULT_TIMEOUT;
        static final boolean replaceDuplicateDownloads = true;
        static final boolean autoConnect = true;
        static String userAgent(@NonNull Context context)
        {
            String userAgent = SystemFacadeHelper.getSystemFacade(context).getSystemUserAgent();

            return (userAgent == null ? UserAgentUtils.defaultUserAgents[0].userAgent : userAgent);
        }
        /* Limitation settings */
        static final int maxActiveDownloads = 3;
        static final int maxDownloadRetries = 5;
        // In Kib
        static final int speedLimit = 0;
        /* Storage settings */
        static String saveDownloadsIn(@NonNull Context context)
        {
            return "file://" + SystemFacadeHelper.getFileSystemFacade(context).getDefaultDownloadPath();
        }
        static final boolean moveAfterDownload = false;
        static String moveAfterDownloadIn(@NonNull Context context)
        {
            return "file://" + SystemFacadeHelper.getFileSystemFacade(context).getDefaultDownloadPath();
        }
        static final boolean deleteFileIfError = false;
        static final boolean preallocateDiskSpace = true;
        /* Browser settings */
        static final boolean browserAllowJavaScript = true;
        static final boolean browserAllowPopupWindows = false;
        static final boolean browserLauncherIcon = false;
        static final boolean browserEnableCaching = true;
        static final boolean browserEnableCookies = true;
        static final boolean browserDisableFromSystem = false;
        static final String browserStartPage = "https://duckduckgo.com";
        static final boolean browserBottomAddressBar = true;
        static final boolean browserDoNotTrack = true;
        static final String browserSearchEngine = "https://duckduckgo.com/?q={searchTerms}";
        static final boolean browserHideMenuIcon = false;
        static final boolean askDisableBatteryOptimization = true;
    }

    public SettingsRepositoryImpl(@NonNull Context appContext)
    {
        this.appContext = appContext;
        pref = PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    /*
     * Returns Flowable with key
     */

    @Override
    public Flowable<String> observeSettingsChanged()
    {
        return Flowable.create((emitter) -> {
            SharedPreferences.OnSharedPreferenceChangeListener listener = (sharedPreferences, key) -> {
                if (!emitter.isCancelled())
                    emitter.onNext(key);
            };

            if (!emitter.isCancelled()) {
                pref.registerOnSharedPreferenceChangeListener(listener);
                emitter.setDisposable(Disposables.fromAction(() ->
                        pref.unregisterOnSharedPreferenceChangeListener(listener)));
            }

        }, BackpressureStrategy.LATEST);
    }

    @Override
    public int theme()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_theme),
                Default.theme(appContext));
    }

    @Override
    public void theme(int val) {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_theme), val)
                .apply();
    }

    @Override
    public boolean progressNotify()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_progress_notify),
                Default.progressNotify);
    }

    @Override
    public void progressNotify(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_progress_notify), val)
                .apply();
    }

    @Override
    public boolean finishNotify()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_finish_notify),
                Default.finishNotify);
    }

    @Override
    public void finishNotify(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_finish_notify), val)
                .apply();
    }

    @Override
    public boolean pendingNotify()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_pending_notify),
                Default.pendingNotify);
    }

    @Override
    public void pendingNotify(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_finish_notify), val)
                .apply();
    }

    @Override
    public String notifySound()
    {
        return pref.getString(appContext.getString(R.string.pref_key_notify_sound),
                Default.notifySound);
    }

    @Override
    public void notifySound(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_notify_sound), val)
                .apply();
    }

    @Override
    public boolean playSoundNotify()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_play_sound_notify),
                Default.playSoundNotify);
    }

    @Override
    public void playSoundNotify(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_play_sound_notify), val)
                .apply();
    }

    @Override
    public boolean ledIndicatorNotify()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_led_indicator_notify),
                Default.ledIndicatorNotify);
    }

    @Override
    public void ledIndicatorNotify(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_led_indicator_notify), val)
                .apply();
    }

    @Override
    public boolean vibrationNotify()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_vibration_notify),
                Default.vibrationNotify);
    }

    @Override
    public void vibrationNotify(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_vibration_notify), val)
                .apply();
    }

    @Override
    public int ledIndicatorColorNotify()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_led_indicator_color_notify),
                Default.ledIndicatorColorNotify(appContext));
    }

    @Override
    public void ledIndicatorColorNotify(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_led_indicator_color_notify), val)
                .apply();
    }

    @Override
    public boolean unmeteredConnectionsOnly()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_umnetered_connections_only),
                Default.unmeteredConnectionsOnly);
    }

    @Override
    public void unmeteredConnectionsOnly(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_umnetered_connections_only), val)
                .apply();
    }

    @Override
    public boolean enableRoaming()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_enable_roaming),
                Default.enableRoaming);
    }

    @Override
    public void enableRoaming(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_enable_roaming), val)
                .apply();
    }

    @Override
    public boolean autostart()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_autostart),
                Default.autostart);
    }

    @Override
    public void autostart(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_autostart), val)
                .apply();
    }

    @Override
    public boolean cpuDoNotSleep()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_cpu_do_not_sleep),
                Default.cpuDoNotSleep);
    }

    @Override
    public void cpuDoNotSleep(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_cpu_do_not_sleep), val)
                .apply();
    }

    @Override
    public boolean onlyCharging()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_download_only_when_charging),
                Default.onlyCharging);
    }

    @Override
    public void onlyCharging(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_download_only_when_charging), val)
                .apply();
    }

    @Override
    public boolean batteryControl()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_battery_control),
                Default.batteryControl);
    }

    @Override
    public void batteryControl(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_battery_control), val)
                .apply();
    }

    @Override
    public boolean customBatteryControl()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_custom_battery_control),
                Default.customBatteryControl);
    }

    @Override
    public void customBatteryControl(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_custom_battery_control), val)
                .apply();
    }

    @Override
    public int customBatteryControlValue()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_custom_battery_control_value),
                Default.customBatteryControlValue);
    }

    @Override
    public void customBatteryControlValue(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_custom_battery_control_value), val)
                .apply();
    }

    @Override
    public int maxActiveDownloads()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_max_active_downloads),
                Default.maxActiveDownloads);
    }

    @Override
    public void maxActiveDownloads(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_max_active_downloads), val)
                .apply();
    }

    @Override
    public int maxDownloadRetries()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_max_download_retries),
                Default.maxDownloadRetries);
    }

    @Override
    public void maxDownloadRetries(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_max_download_retries), val)
                .apply();
    }

    @Override
    public int speedLimit() {
        return pref.getInt(appContext.getString(R.string.pref_key_speed_limit),
                Default.speedLimit);
    }

    @Override
    public void speedLimit(int val) {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_speed_limit), val)
                .apply();
    }

    @Override
    public int timeout()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_timeout),
                Default.timeout);
    }

    @Override
    public void timeout(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_timeout), val)
                .apply();
    }

    @Override
    public boolean replaceDuplicateDownloads()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_replace_duplicate_downloads),
                Default.replaceDuplicateDownloads);
    }

    @Override
    public void replaceDuplicateDownloads(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_replace_duplicate_downloads), val)
                .apply();
    }

    @Override
    public boolean autoConnect()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_auto_connect),
                Default.autoConnect);
    }

    @Override
    public void autoConnect(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_auto_connect), val)
                .apply();
    }

    @Override
    public String userAgent()
    {
        return pref.getString(appContext.getString(R.string.pref_key_user_agent),
                Default.userAgent(appContext));
    }

    @Override
    public void userAgent(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_user_agent), val)
                .apply();
    }

    @Override
    public String saveDownloadsIn()
    {
        return pref.getString(appContext.getString(R.string.pref_key_save_downloads_in),
                Default.saveDownloadsIn(appContext));
    }

    @Override
    public void saveDownloadsIn(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_save_downloads_in), val)
                .apply();
    }

    @Override
    public boolean moveAfterDownload()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_move_after_download),
                Default.moveAfterDownload);
    }

    @Override
    public void moveAfterDownload(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_move_after_download), val)
                .apply();
    }

    @Override
    public String moveAfterDownloadIn()
    {
        return pref.getString(appContext.getString(R.string.pref_key_move_after_download_in),
                Default.moveAfterDownloadIn(appContext));
    }

    @Override
    public void moveAfterDownloadIn(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_move_after_download_in), val)
                .apply();
    }

    @Override
    public boolean deleteFileIfError()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_delete_file_if_error),
                Default.deleteFileIfError);
    }

    @Override
    public void deleteFileIfError(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_delete_file_if_error), val)
                .apply();
    }

    @Override
    public boolean preallocateDiskSpace()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_preallocate_disk_space),
                Default.preallocateDiskSpace);
    }

    @Override
    public void preallocateDiskSpace(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_preallocate_disk_space), val)
                .apply();
    }

    @Override
    public boolean browserAllowJavaScript()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_browser_allow_java_script),
                Default.browserAllowJavaScript);
    }

    @Override
    public void browserAllowJavaScript(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_browser_allow_java_script), val)
                .apply();
    }

    @Override
    public boolean browserAllowPopupWindows()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_browser_allow_popup_windows),
                Default.browserAllowPopupWindows);
    }

    @Override
    public void browserAllowPopupWindows(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_browser_allow_popup_windows), val)
                .apply();
    }

    @Override
    public boolean browserLauncherIcon()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_browser_launcher_icon),
                Default.browserLauncherIcon);
    }

    @Override
    public void browserLauncherIcon(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_browser_launcher_icon), val)
                .apply();
    }

    @Override
    public boolean browserEnableCaching()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_browser_enable_caching),
                Default.browserEnableCaching);
    }

    @Override
    public void browserEnableCaching(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_browser_enable_caching), val)
                .apply();
    }

    @Override
    public boolean browserEnableCookies()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_browser_enable_cookies),
                Default.browserEnableCookies);
    }

    @Override
    public void browserEnableCookies(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_browser_enable_cookies), val)
                .apply();
    }

    @Override
    public boolean browserDisableFromSystem()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_browser_disable_from_system),
                Default.browserDisableFromSystem);
    }

    @Override
    public void browserDisableFromSystem(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_browser_disable_from_system), val)
                .apply();
    }

    @Override
    public String browserStartPage()
    {
        return pref.getString(appContext.getString(R.string.pref_key_browser_start_page),
                Default.browserStartPage);
    }

    @Override
    public void browserStartPage(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_browser_start_page), val)
                .apply();
    }

    @Override
    public boolean browserBottomAddressBar()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_browser_bottom_address_bar),
                Default.browserBottomAddressBar);
    }

    @Override
    public void browserBottomAddressBar(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_browser_bottom_address_bar), val)
                .apply();
    }

    @Override
    public boolean browserDoNotTrack()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_browser_do_not_track),
                Default.browserDoNotTrack);
    }

    @Override
    public void browserDoNotTrack(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_browser_do_not_track), val)
                .apply();
    }

    @Override
    public String browserSearchEngine()
    {
        return pref.getString(appContext.getString(R.string.pref_key_browser_search_engine),
                Default.browserSearchEngine);
    }

    @Override
    public void browserSearchEngine(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_browser_search_engine), val)
                .apply();
    }

    @Override
    public boolean browserHideMenuIcon() {
        return pref.getBoolean(appContext.getString(R.string.pref_key_browser_hide_menu_icon),
                Default.browserHideMenuIcon);
    }

    @Override
    public void browserHideMenuIcon(boolean val) {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_browser_hide_menu_icon), val)
                .apply();
    }

    @Override
    public void askDisableBatteryOptimization(boolean val) {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_ask_disable_battery_optimization), val)
                .apply();
    }

    @Override
    public boolean askDisableBatteryOptimization() {
        return pref.getBoolean(appContext.getString(R.string.pref_key_ask_disable_battery_optimization),
                Default.askDisableBatteryOptimization);
    }
}
