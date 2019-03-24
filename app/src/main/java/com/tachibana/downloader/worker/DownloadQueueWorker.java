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

package com.tachibana.downloader.worker;

import android.content.Context;

import com.tachibana.downloader.MainApplication;
import com.tachibana.downloader.core.DownloadScheduler;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.storage.DownloadQueue;

import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/*
 * Used only by DownloadScheduler.
 */

public class DownloadQueueWorker extends Worker
{
    public static final String TAG_ID = "id";
    public static final String TAG_ACTION = "action";

    public static final String ACTION_PUSH = "push";
    public static final String ACTION_POP_AND_RUN = "pop_and_run";

    public DownloadQueueWorker(@NonNull Context context, @NonNull WorkerParameters params)
    {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork()
    {
        Data data = getInputData();

        String action = data.getString(TAG_ACTION);
        if (action == null)
            return Result.failure();

        switch (action) {
            case ACTION_PUSH:
                String uuid = data.getString(TAG_ID);
                if (uuid == null)
                    return Result.failure();

                UUID id;
                try {
                    id = UUID.fromString(uuid);

                } catch (IllegalArgumentException e) {
                    return Result.failure();
                }
                actionPush(id);
                return Result.success();
            case ACTION_POP_AND_RUN:
                return actionPopAndRun();
        }

        return Result.failure();
    }

    private void actionPush(UUID id)
    {
        DownloadQueue queue = ((MainApplication)getApplicationContext()).getDownloadQueue();
        queue.push(id);
    }

    private Result actionPopAndRun()
    {
        DownloadQueue queue = ((MainApplication)getApplicationContext()).getDownloadQueue();
        DataRepository repo = ((MainApplication)getApplicationContext()).getRepository();

        DownloadInfo info = null;
        while (info == null) {
            UUID id = queue.pop();
            /* Queue is empty, return */
            if (id == null)
                return Result.success();
            info = repo.getInfoById(id);
        }

        DownloadScheduler.run(getApplicationContext(), info);

        return Result.success();
    }
}
