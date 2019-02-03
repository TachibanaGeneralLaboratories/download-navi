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

package com.tachibana.downloader.core.entity;

import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

@Entity(tableName = "download_info_headers",
        indices = {@Index(value = "infoId")},
        foreignKeys = @ForeignKey(
                entity = DownloadInfo.class,
                parentColumns = "id",
                childColumns = "infoId",
                onDelete = CASCADE))
public class Header
{
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public UUID infoId;
    public String name;
    public String value;

    public Header(@NonNull UUID infoId, String name, String value)
    {
        this.infoId = infoId;
        this.name = name;
        this.value = value;
    }
}