/*
 * Copyright (C) 2019-2021 Tachibana General Laboratories, LLC
 * Copyright (C) 2019-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader;

import android.util.Log;

import androidx.multidex.MultiDexApplication;

import com.tachibana.downloader.core.DownloadNotifier;
import com.tachibana.downloader.ui.errorreport.ErrorReportActivity;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.data.StringFormat;

public class MainApplication extends MultiDexApplication
{
    public static final String TAG = MainApplication.class.getSimpleName();

    @Override
    public void onCreate()
    {
        super.onCreate();

        CoreConfigurationBuilder builder = new CoreConfigurationBuilder();
        builder
                .withBuildConfigClass(BuildConfig.class)
                .withReportFormat(StringFormat.JSON);
        builder.withPluginConfigurations(new MailSenderConfigurationBuilder()
                .withMailTo("proninyaroslav@mail.ru")
                .build());
        builder.withPluginConfigurations(new DialogConfigurationBuilder()
                .withEnabled(true)
                .withReportDialogClass(ErrorReportActivity.class)
                .build());
        // Set stub handler
        if (Thread.getDefaultUncaughtExceptionHandler() == null) {
            Thread.setDefaultUncaughtExceptionHandler((t, e) ->
                    Log.e(TAG, "Uncaught exception in " + t + ": " + Log.getStackTraceString(e))
            );
        }
        ACRA.init(this, builder);

        DownloadNotifier downloadNotifier = DownloadNotifier.getInstance(this);
        downloadNotifier.makeNotifyChans();
        downloadNotifier.startUpdate();
    }
}
