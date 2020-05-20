/*
 * Copyright (C) 2019, 2020 Tachibana General Laboratories, LLC
 * Copyright (C) 2019, 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.core;

import android.content.Context;

import androidx.annotation.NonNull;

import com.tachibana.downloader.core.settings.SettingsRepository;
import com.tachibana.downloader.core.settings.SettingsRepositoryImpl;
import com.tachibana.downloader.core.storage.AppDatabase;
import com.tachibana.downloader.core.storage.BrowserRepository;
import com.tachibana.downloader.core.storage.BrowserRepositoryImpl;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.storage.DataRepositoryImpl;

public class RepositoryHelper
{
    private static DataRepositoryImpl dataRepo;
    private static SettingsRepositoryImpl settingsRepo;
    private static BrowserRepository browserRepository;

    public synchronized static DataRepository getDataRepository(@NonNull Context appContext)
    {
        if (dataRepo == null)
            dataRepo = new DataRepositoryImpl(appContext,
                    AppDatabase.getInstance(appContext));

        return dataRepo;
    }

    public synchronized static SettingsRepository getSettingsRepository(@NonNull Context appContext)
    {
        if (settingsRepo == null)
            settingsRepo = new SettingsRepositoryImpl(appContext);

        return settingsRepo;
    }

    public synchronized static BrowserRepository getBrowserRepository(@NonNull Context appContext)
    {
        if (browserRepository == null)
            browserRepository = new BrowserRepositoryImpl(AppDatabase.getInstance(appContext));

        return browserRepository;
    }
}
