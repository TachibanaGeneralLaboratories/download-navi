/*
 * Copyright (C) 2018 Tachibana General Laboratories, LLC
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.os.Parcel;
import android.os.Parcelable;

import java.util.UUID;

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

public class DownloadPiece implements Parcelable
{
    private UUID infoId;
    private int index;
    private long size;
    private long curBytes;
    private int statusCode = DownloadInfo.STATUS_PENDING;
    private int numFailed = 0;
    private String statusMsg;

    public static final int MAX_RETRIES = 5;

    public DownloadPiece(UUID infoId, int index, long size, long curBytes)
    {
        this.infoId = infoId;
        this.index = index;
        this.size = size;
        this.curBytes = curBytes;
    }

    public DownloadPiece(Parcel source)
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

    public UUID getInfoId()
    {
        return infoId;
    }

    public int getIndex()
    {
        return index;
    }

    public long getCurBytes()
    {
        return curBytes;
    }

    public long getSize()
    {
        return size;
    }

    public void setSize(long size)
    {
        this.size = size;
    }

    public void setCurBytes(long bytes)
    {
        this.curBytes = bytes;
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    public int getNumFailed()
    {
        return numFailed;
    }

    public void setNumFailed(int numFailed)
    {
        this.numFailed = numFailed;
    }

    public void incNumFailed()
    {
        this.numFailed++;
    }

    public String getStatusMsg()
    {
        return statusMsg;
    }

    public void setStatusMsg(String statusMsg)
    {
        this.statusMsg = statusMsg;
    }

    public void setStatusCode(int statusCode)
    {
        this.statusCode = statusCode;
    }

    @Override
    public int hashCode()
    {
        int prime = 31, result = 1;

        result = prime * result + ((infoId == null) ? 0 : infoId.hashCode());
        result = prime * result + index;

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

        return (infoId == null || infoId.equals(piece.infoId)) && index == piece.index;
    }

    @Override
    public String toString()
    {
        return "DownloadPiece{" +
                "infoId=" + infoId +
                ", index=" + index +
                ", size=" + size +
                ", curBytes=" + curBytes +
                ", statusCode=" + statusCode +
                ", numFailed=" + numFailed +
                ", statusMsg='" + statusMsg + '\'' +
                '}';
    }
}
