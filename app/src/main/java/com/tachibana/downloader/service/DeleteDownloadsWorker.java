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
import android.util.Log;

import com.tachibana.downloader.MainApplication;
import com.tachibana.downloader.core.model.DownloadEngine;
import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.storage.DataRepository;

import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/*
 * Used only by DownloadEngine.
 */

public class DeleteDownloadsWorker extends Worker
{
    @SuppressWarnings("unused")
    private static final String TAG = DeleteDownloadsWorker.class.getSimpleName();

    public static final String TAG_ID_LIST = "id_list";
    public static final String TAG_WITH_FILE = "with_file";

    public DeleteDownloadsWorker(@NonNull Context context, @NonNull WorkerParameters params)
    {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork()
    {
        Context context = getApplicationContext();
        DownloadEngine engine = ((MainApplication)context).getDownloadEngine();
        DataRepository repo = ((MainApplication)context).getRepository();

        Data data = getInputData();
        String[] idList = data.getStringArray(TAG_ID_LIST);
        boolean withFile = data.getBoolean(TAG_WITH_FILE, false);
        if (idList == null)
            return Result.failure();

        for (String id : idList) {
            if (id == null)
                continue;
            UUID uuid;
            try {
                uuid = UUID.fromString(id);

            } catch (IllegalArgumentException e) {
                continue;
            }

            DownloadInfo info = repo.getInfoById(uuid);
            if (info == null)
                continue;
            try {
                engine.doDeleteDownload(info, withFile);

            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }

        return Result.success();
    }
}
