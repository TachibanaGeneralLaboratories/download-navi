/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of DownloadNavi.
 *
 * DownloadNavi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DownloadNavi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DownloadNavi.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tachibana.downloader.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.tachibana.downloader.R;

public class SettingsManager
{
    public static class Default
    {
        /* Appearance settings */
        public static int theme(Context context) { return Integer.parseInt(context.getString(R.string.pref_theme_light_value)); }
        /* Network settings */
        public static final boolean wifiOnly = false;
        public static final boolean enableRoaming = true;
    }

    public static SharedPreferences getPreferences(Context context)
    {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}