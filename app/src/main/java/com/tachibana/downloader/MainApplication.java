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

package com.tachibana.downloader;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.tachibana.downloader.core.DownloadEngine;
import com.tachibana.downloader.core.storage.AppDatabase;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.utils.Utils;

public class MainApplication extends Application
{
    private DownloadNotifier downloadNotifier;

    @Override
    public void onCreate()
    {
        super.onCreate();

        Utils.makeNotifyChans(this, (NotificationManager)getSystemService(NOTIFICATION_SERVICE));

        downloadNotifier = new DownloadNotifier(this, getRepository());
        downloadNotifier.startUpdate();
    }

    public AppDatabase getDatabase()
    {
        return AppDatabase.getInstance(this);
    }

    public DataRepository getRepository()
    {
        return DataRepository.getInstance(getDatabase());
    }

    public DownloadEngine getDownloadEngine()
    {
        return DownloadEngine.getInstance(this);
    }
}
