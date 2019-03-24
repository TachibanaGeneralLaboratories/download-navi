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

package com.tachibana.downloader.core.storage;

import com.tachibana.downloader.core.entity.QueuedDownload;

import java.util.UUID;

import androidx.annotation.NonNull;

/*
 * The priority queue if we want to defer download for an indefinite period of time,
 * for example, simultaneous downloads.
 */

public class DownloadQueue
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadQueue.class.getSimpleName();

    private static DownloadQueue INSTANCE;

    private AppDatabase db;

    public static DownloadQueue getInstance(AppDatabase db)
    {
        if (INSTANCE == null) {
            synchronized (DownloadQueue.class) {
                if (INSTANCE == null)
                    INSTANCE = new DownloadQueue(db);
            }
        }
        return INSTANCE;
    }

    private DownloadQueue(AppDatabase db)
    {
        this.db = db;
    }

    public void push(@NonNull UUID downloadId)
    {
        if (db.queuedDownloadDao().getByDownloadId(downloadId).isEmpty())
            /* Using time as priority */
            db.queuedDownloadDao().push(new QueuedDownload(downloadId, System.currentTimeMillis()));
    }

    public UUID pop()
    {
        QueuedDownload download = db.queuedDownloadDao().get();
        if (download != null) {
            db.queuedDownloadDao().delete(download);
            return download.downloadId;
        }

        return null;
    }
}