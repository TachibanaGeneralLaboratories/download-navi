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

package com.tachibana.downloader.service;

import android.content.Context;
import android.content.Intent;

import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.service.DownloadService;

import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/*
 * Used only by DownloadScheduler.
 */

public class RunDownloadWorker extends Worker
{
    public static final String TAG_ID = "id";

    public RunDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params)
    {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork()
    {
        Data data = getInputData();
        String uuid = data.getString(TAG_ID);
        if (uuid == null)
            return Result.failure();

        UUID id;
        try {
            id = UUID.fromString(uuid);

        } catch (IllegalArgumentException e) {
            return Result.failure();
        }

        runDownloadAction(id);

        return Result.success();
    }

    private void runDownloadAction(UUID id)
    {
        /*
         * Use a foreground service, because WorkManager has a 10 minute work limit,
         * which may be less than the download time
         */
        Intent i = new Intent(getApplicationContext(), DownloadService.class);
        i.setAction(DownloadService.ACTION_RUN_DOWNLOAD);
        i.putExtra(DownloadService.TAG_DOWNLOAD_ID, id);
        Utils.startServiceBackground(getApplicationContext(), i);
    }
}
