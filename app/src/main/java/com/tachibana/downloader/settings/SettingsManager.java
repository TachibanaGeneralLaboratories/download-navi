/*
 * Copyright (C) 2018, 2019 Tachibana General Laboratories, LLC
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.preference.PreferenceManager;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.utils.FileUtils;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/*
 * Global settings.
 */

public class SettingsManager
{
    public static class Default
    {
        /* Appearance settings */
        public static int theme(Context context) { return Integer.parseInt(context.getString(R.string.pref_theme_light_value)); }
        public static final boolean progressNotify = true;
        public static final boolean finishNotify = true;
        public static final boolean pendingNotify = true;
        public static final String notifySound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString();
        public static final boolean playSoundNotify = true;
        public static final boolean ledIndicatorNotify = true;
        public static final boolean vibrationNotify = true;
        public static int ledIndicatorColorNotify(Context context) { return ContextCompat.getColor(context, R.color.primary); }
        /* Behavior settings */
        public static final boolean wifiOnly = false;
        public static final boolean enableRoaming = true;
        public static final boolean autostart = false;
        public static final boolean autostartStoppedDownloads = true;
        public static final boolean cpuDoNotSleep = false;
        public static final boolean onlyCharging = false;
        public static final boolean batteryControl = false;
        public static final boolean customBatteryControl = false;
        public static final int maxActiveDownloads = 3;
        /* Filemanager settings */
        public static final String fileManagerLastDir = FileUtils.getDefaultDownloadPath();
        /* Storage settings */
        public static final String saveDownloadsIn = "file://" + FileUtils.getDefaultDownloadPath();
        public static final boolean moveAfterDownload = false;
        public static final String moveAfterDownloadIn = "file://" + FileUtils.getDefaultDownloadPath();
        public static final boolean deleteFileIfError = true;
        public static final boolean preallocateDiskSpace = true;
    }

    private static SettingsManager INSTANCE;
    private SharedPreferences pref;

    public static SettingsManager getInstance(@NonNull Context appContext)
    {
        if (INSTANCE == null) {
            synchronized (SettingsManager.class) {
                if (INSTANCE == null)
                    INSTANCE = new SettingsManager(appContext);
            }
        }
        return INSTANCE;
    }

    private SettingsManager(@NonNull Context appContext)
    {
        pref = PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    public SharedPreferences getPreferences()
    {
        return pref;
    }
}