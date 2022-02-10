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

import io.reactivex.Flowable;

public interface SettingsRepository
{
    /*
     * Returns Flowable with key
     */

    Flowable<String> observeSettingsChanged();

    /*
     * Appearance settings
     */

    int theme();

    void theme(int val);

    boolean progressNotify();

    void progressNotify(boolean val);

    boolean finishNotify();

    void finishNotify(boolean val);

    boolean pendingNotify();

    void pendingNotify(boolean val);

    String notifySound();

    void notifySound(String val);

    boolean playSoundNotify();

    void playSoundNotify(boolean val);

    boolean ledIndicatorNotify();

    void ledIndicatorNotify(boolean val);

    boolean vibrationNotify();

    void vibrationNotify(boolean val);

    int ledIndicatorColorNotify();

    void ledIndicatorColorNotify(int val);

    /*
     * Behavior settings
     */

    boolean unmeteredConnectionsOnly();

    void unmeteredConnectionsOnly(boolean val);

    boolean enableRoaming();

    void enableRoaming(boolean val);

    boolean autostart();

    void autostart(boolean val);

    boolean cpuDoNotSleep();

    void cpuDoNotSleep(boolean val);

    boolean onlyCharging();

    void onlyCharging(boolean val);

    boolean batteryControl();

    void batteryControl(boolean val);

    boolean customBatteryControl();

    void customBatteryControl(boolean val);

    int customBatteryControlValue();

    void customBatteryControlValue(int val);

    int timeout();

    void timeout(int val);

    boolean replaceDuplicateDownloads();

    void replaceDuplicateDownloads(boolean val);

    boolean autoConnect();

    void autoConnect(boolean val);

    String userAgent();

    void userAgent(String val);

    /*
     * Limitation settings
     */

    int maxActiveDownloads();

    void maxActiveDownloads(int val);

    int maxDownloadRetries();

    void maxDownloadRetries(int val);

    int speedLimit();

    void speedLimit(int val);

    /*
     * Storage settings
     */

    String saveDownloadsIn();

    void saveDownloadsIn(String val);

    boolean moveAfterDownload();

    void moveAfterDownload(boolean val);

    String moveAfterDownloadIn();

    void moveAfterDownloadIn(String val);

    boolean deleteFileIfError();

    void deleteFileIfError(boolean val);

    boolean preallocateDiskSpace();

    void preallocateDiskSpace(boolean val);

    /*
     * Browser settings
     */

    boolean browserAllowJavaScript();

    void browserAllowJavaScript(boolean val);

    boolean browserAllowPopupWindows();

    void browserAllowPopupWindows(boolean val);

    boolean browserLauncherIcon();

    void browserLauncherIcon(boolean val);

    boolean browserEnableCaching();

    void browserEnableCaching(boolean val);

    boolean browserEnableCookies();

    void browserEnableCookies(boolean val);

    boolean browserDisableFromSystem();

    void browserDisableFromSystem(boolean val);

    String browserStartPage();

    void browserStartPage(String val);

    boolean browserBottomAddressBar();

    void browserBottomAddressBar(boolean val);

    boolean browserDoNotTrack();

    void browserDoNotTrack(boolean val);

    String browserSearchEngine();

    void browserSearchEngine(String val);

    boolean browserHideMenuIcon();

    void browserHideMenuIcon(boolean val);

    void askDisableBatteryOptimization(boolean val);

    boolean askDisableBatteryOptimization();
}
