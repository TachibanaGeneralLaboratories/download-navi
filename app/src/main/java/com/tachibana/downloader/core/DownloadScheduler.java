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

package com.tachibana.downloader.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.settings.SettingsManager;
import com.tachibana.downloader.worker.GetAndRunDownloadWorker;
import com.tachibana.downloader.worker.RescheduleAllWorker;
import com.tachibana.downloader.worker.RestoreDownloadsWorker;
import com.tachibana.downloader.worker.RunAllWorker;
import com.tachibana.downloader.worker.RunDownloadWorker;

import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class DownloadScheduler
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadScheduler.class.getSimpleName();

    public static final String TAG_WORK_RUN_ALL_TYPE = "run_all";
    public static final String TAG_WORK_RESTORE_DOWNLOADS_TYPE = "restore_downloads";
    public static final String TAG_WORK_RUN_TYPE = "run";
    public static final String TAG_WORK_GET_AND_RUN_TYPE = "get_and_run";
    public static final String TAG_WORK_RESCHEDULE_TYPE = "reschedule";

    /*
     * Run unique work for starting download.
     * If there is existing pending (uncompleted) work, cancel it
     */

    public static void run(@NonNull Context context, @NonNull DownloadInfo info)
    {
        String downloadTag = getDownloadTag(info.id);
        Data data = new Data.Builder()
                .putString(RunDownloadWorker.TAG_ID, info.id.toString())
                .build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(RunDownloadWorker.class)
                .setInputData(data)
                .setConstraints(getConstraints(context, info))
                .addTag(TAG_WORK_RUN_TYPE)
                .addTag(downloadTag)
                .build();
        WorkManager.getInstance().enqueueUniqueWork(downloadTag,
                ExistingWorkPolicy.REPLACE, work);
    }

    public static void run(@NonNull UUID id)
    {
        Data data = new Data.Builder()
                .putString(GetAndRunDownloadWorker.TAG_ID, id.toString())
                .build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(GetAndRunDownloadWorker.class)
                .setInputData(data)
                .addTag(TAG_WORK_GET_AND_RUN_TYPE)
                .build();
        WorkManager.getInstance().enqueue(work);
    }

    public static void undone(@NonNull DownloadInfo info)
    {
        WorkManager.getInstance().cancelAllWorkByTag(getDownloadTag(info.id));
    }

    public static void rescheduleAll()
    {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(RescheduleAllWorker.class)
                .addTag(TAG_WORK_RESCHEDULE_TYPE)
                .build();
        WorkManager.getInstance().enqueue(work);
    }

    public static void runAll(boolean ignorePaused)
    {
        Data data = new Data.Builder()
                .putBoolean(RunAllWorker.TAG_IGNORE_PAUSED, ignorePaused)
                .build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(RunAllWorker.class)
                .setInputData(data)
                .addTag(TAG_WORK_RUN_ALL_TYPE)
                .build();
        WorkManager.getInstance().enqueue(work);
    }

    /*
     * Run stopped (and with running status) downloads after starting app
     */

    public static void restoreDownloads()
    {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(RestoreDownloadsWorker.class)
                .addTag(TAG_WORK_RESTORE_DOWNLOADS_TYPE)
                .build();
        WorkManager.getInstance().enqueue(work);
    }

    public static String getDownloadTag(UUID downloadId)
    {
        return TAG_WORK_RUN_TYPE + ":" + downloadId;
    }

    public static String extractDownloadIdFromTag(String tag)
    {
        return tag.substring(tag.indexOf(":") + 1);
    }

    private static Constraints getConstraints(Context context, DownloadInfo info)
    {
        SharedPreferences pref = SettingsManager.getInstance(context).getPreferences();

        NetworkType netType = NetworkType.CONNECTED;
        boolean onlyCharging = pref.getBoolean(context.getString(R.string.pref_key_download_only_when_charging),
                                               SettingsManager.Default.onlyCharging);
        boolean batteryControl = pref.getBoolean(context.getString(R.string.pref_key_battery_control),
                                                 SettingsManager.Default.batteryControl);
        if (pref.getBoolean(context.getString(R.string.pref_key_enable_roaming),
                            SettingsManager.Default.enableRoaming))
            netType = NetworkType.NOT_ROAMING;
        if (info != null && info.unmeteredConnectionsOnly || pref.getBoolean(context.getString(R.string.pref_key_umnetered_connections_only),
                                                             SettingsManager.Default.unmeteredConnectionsOnly))
            netType = NetworkType.UNMETERED;

        return new Constraints.Builder()
                .setRequiredNetworkType(netType)
                .setRequiresCharging(onlyCharging)
                .setRequiresBatteryNotLow(batteryControl)
                .build();
    }
}
