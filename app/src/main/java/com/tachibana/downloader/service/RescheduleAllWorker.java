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

package com.tachibana.downloader.service;

import android.content.Context;

import com.google.common.util.concurrent.ListenableFuture;
import com.tachibana.downloader.MainApplication;
import com.tachibana.downloader.core.RepositoryHelper;
import com.tachibana.downloader.core.model.DownloadScheduler;
import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.storage.DataRepositoryImpl;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/*
 * Reschedule all RunDownloadWorker's. Used only by DownloadScheduler.
 */

public class RescheduleAllWorker extends Worker
{
    public RescheduleAllWorker(@NonNull Context context, @NonNull WorkerParameters params)
    {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork()
    {
        Context context = getApplicationContext();
        DataRepository repo = RepositoryHelper.getDataRepository(context);

        ListenableFuture<List<WorkInfo>> future = WorkManager.getInstance(context)
                .getWorkInfosByTag(DownloadScheduler.TAG_WORK_RUN_TYPE);
        try {
            for (WorkInfo workInfo : future.get()) {
                if (workInfo.getState().isFinished())
                    continue;

                String runTag = null;
                for (String tag : workInfo.getTags()) {
                    if (!tag.equals(DownloadScheduler.TAG_WORK_RUN_TYPE) &&
                        tag.startsWith(DownloadScheduler.TAG_WORK_RUN_TYPE)) {
                        runTag = tag;
                        /* Get the first tag because it's unique */
                        break;
                    }
                }
                String downloadId = (runTag == null ? null : DownloadScheduler.extractDownloadIdFromTag(runTag));
                if (downloadId == null)
                    continue;

                DownloadInfo info;
                try {
                    info = repo.getInfoById(UUID.fromString(downloadId));

                } catch (Exception e) {
                    continue;
                }
                if (info == null)
                    continue;

                DownloadScheduler.run(context, info);
            }
        } catch (InterruptedException | ExecutionException e) {
            /* Ignore */
        }

        return Result.success();
    }
}
