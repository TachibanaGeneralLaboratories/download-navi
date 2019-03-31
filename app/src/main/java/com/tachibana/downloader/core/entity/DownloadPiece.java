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

package com.tachibana.downloader.core.entity;

import android.os.Parcel;
import android.os.Parcelable;

import com.tachibana.downloader.core.StatusCode;

import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

import static androidx.room.ForeignKey.CASCADE;

/*
 * The class encapsulates information about piece of download.
 * A piece is a HTTP/S connection that downloads a certain part
 * of the data from the server (and currently stores it in a file position
 * specifically reserved for the piece).
 *
 * As a rule, the entire file size is divided equally between all pieces,
 * so the size of the parts is the same, but the last piece may have a larger size.
 * If the file size is unknown, only one download piece is created,
 * which has a negative size (-1)
 *
 * If the server doesn't support HTTP Range Request (TODO: support RANG for FTP),
 * then the entire file is downloaded in only one piece.
 */

@Entity(primaryKeys = {"pieceIndex", "infoId"},
        indices = {@Index(value = "infoId")},
        foreignKeys = @ForeignKey(
                entity = DownloadInfo.class,
                parentColumns = "id",
                childColumns = "infoId",
                onDelete = CASCADE))
public class DownloadPiece implements Parcelable
{
    @ColumnInfo(name = "pieceIndex")
    public int index;
    @NonNull
    public UUID infoId;
    public long size;
    public long curBytes;
    public int statusCode = StatusCode.STATUS_PENDING;
    public int numFailed = 0;
    public String statusMsg;
    public long speed;

    public DownloadPiece(@NonNull UUID infoId, int index, long size, long curBytes)
    {
        this.infoId = infoId;
        this.index = index;
        this.size = size;
        this.curBytes = curBytes;
    }

    @Ignore
    public DownloadPiece(@NonNull Parcel source)
    {
        infoId = (UUID)source.readSerializable();
        size = source.readLong();
        index = source.readInt();
        curBytes = source.readLong();
        numFailed = source.readInt();
        statusCode = source.readInt();
        statusMsg = source.readString();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeSerializable(infoId);
        dest.writeLong(size);
        dest.writeInt(index);
        dest.writeLong(curBytes);
        dest.writeInt(numFailed);
        dest.writeInt(statusCode);
        dest.writeString(statusMsg);
    }

    public static final Creator<DownloadPiece> CREATOR =
            new Creator<DownloadPiece>()
            {
                @Override
                public DownloadPiece createFromParcel(Parcel source)
                {
                    return new DownloadPiece(source);
                }

                @Override
                public DownloadPiece[] newArray(int size)
                {
                    return new DownloadPiece[size];
                }
            };


    @Override
    public int hashCode()
    {
        int prime = 31, result = 1;

        result = prime * result + index;
        result = prime * result + infoId.hashCode();

        return result;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof DownloadPiece))
            return false;

        if (o == this)
            return true;

        DownloadPiece piece = (DownloadPiece)o;

        return infoId.equals(piece.infoId) &&
                index == piece.index &&
                size == piece.size &&
                curBytes == piece.curBytes &&
                speed == piece.speed &&
                statusCode == piece.statusCode &&
                numFailed == piece.numFailed &&
                (statusMsg == null || statusMsg.equals(piece.statusMsg));
    }

    @Override
    public String toString()
    {
        return "DownloadPiece{" +
                "index=" + index +
                ", infoId=" + infoId +
                ", size=" + size +
                ", curBytes=" + curBytes +
                ", statusCode=" + statusCode +
                ", numFailed=" + numFailed +
                ", statusMsg='" + statusMsg + '\'' +
                ", speed=" + speed +
                '}';
    }
}