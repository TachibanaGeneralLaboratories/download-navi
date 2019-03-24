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

package com.tachibana.downloader.core.storage.dao;

import com.tachibana.downloader.core.entity.QueuedDownload;

import java.util.List;
import java.util.UUID;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface QueuedDownloadDao
{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void push(QueuedDownload download);

    @Query("SELECT * FROM QueuedDownload WHERE (SELECT MAX(priority) FROM QueuedDownload)")
    QueuedDownload get();

    @Query("SELECT * FROM QueuedDownload WHERE downloadId = :downloadId")
    List<QueuedDownload> getByDownloadId(UUID downloadId);

    @Delete
    void delete(QueuedDownload download);
}
