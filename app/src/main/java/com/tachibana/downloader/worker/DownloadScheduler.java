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

package com.tachibana.downloader.worker;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.service.DownloadService;
import com.tachibana.downloader.settings.SettingsManager;

import java.util.UUID;

import androidx.annotation.NonNull;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/*
 * TODO: undone work; handle work status (e.g for retry)
 */

public class DownloadScheduler extends Worker
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadScheduler.class.getSimpleName();

    private static final String TAG_ACTION = "action";
    private static final String TAG_ID = "id";

    public DownloadScheduler(@NonNull Context context, @NonNull WorkerParameters params)
    {
        super(context, params);
    }

    public static void runDownload(Context context, DownloadInfo info)
    {
        if (info == null || context == null)
            return;

        Data data = new Data.Builder()
                .putString(TAG_ACTION, DownloadService.ACTION_RUN_DOWNLOAD)
                .putString(TAG_ID, info.id.toString())
                .build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(DownloadScheduler.class)
                .setInputData(data)
                .setConstraints(getConstraints(context, info))
                .build();
        WorkManager.getInstance().enqueue(work);
    }

    private static Constraints getConstraints(Context context, DownloadInfo info)
    {
        NetworkType netType = NetworkType.CONNECTED;
        SharedPreferences pref = SettingsManager.getPreferences(context);

        if (pref.getBoolean(context.getString(R.string.pref_key_wifi_only),
                            SettingsManager.Default.enableRoaming))
            netType = NetworkType.NOT_ROAMING;
        if (info != null && info.wifiOnly || pref.getBoolean(context.getString(R.string.pref_key_wifi_only),
                                                             SettingsManager.Default.wifiOnly))
            netType = NetworkType.UNMETERED;

        return new Constraints.Builder()
                .setRequiredNetworkType(netType)
                .build();
    }

    @NonNull
    @Override
    public Result doWork()
    {
        Data data = getInputData();
        String action = data.getString(TAG_ACTION);
        if (action == null)
            return Result.failure();

        if (action.equals(DownloadService.ACTION_RUN_DOWNLOAD))
            return runDownloadAction(data);

        return Result.failure();
    }

    private Result runDownloadAction(Data data)
    {
        String uuid = data.getString(TAG_ID);
        if (uuid == null)
            return Result.failure();

        UUID id;
        try {
            id = UUID.fromString(uuid);

        } catch (IllegalArgumentException e) {
            return Result.failure();
        }

        Intent i = new Intent(getApplicationContext(), DownloadService.class);
        i.setAction(DownloadService.ACTION_RUN_DOWNLOAD);
        i.putExtra(DownloadService.TAG_DOWNLOAD_ID, id);
        Utils.startServiceBackground(getApplicationContext(), i);

        return Result.success();
    }
}
