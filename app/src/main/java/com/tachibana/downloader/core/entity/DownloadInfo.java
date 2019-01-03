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

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.tachibana.downloader.core.StatusCode;
import com.tachibana.downloader.core.storage.converter.UriConverter;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*
 * The class encapsulates information about download.
 */

@Entity
public class DownloadInfo implements Parcelable, Comparable<DownloadInfo>
{
    /* Piece number can't be less or equal zero */
    public static final int MIN_PIECES = 1;

    @PrimaryKey
    @NonNull
    public UUID id;
    /* SAF storage */
    @TypeConverters({UriConverter.class})
    public Uri filePath;
    public String url;
    public String fileName;
    public String description;
    public String mimeType;
    public long totalBytes = -1;
    private int numPieces = MIN_PIECES;
    public int statusCode = StatusCode.STATUS_PENDING;
    public boolean wifiOnly = false;
    public boolean retry = true;
    /* Indicates that server support partial download */
    public boolean partialSupport = true;
    public String statusMsg;
    public long dateAdded;

    public DownloadInfo(Uri filePath, String url,
                        String fileName, String mimeType)
    {
        this.id = UUID.randomUUID();
        this.filePath = filePath;
        this.url = url;
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    @Ignore
    public DownloadInfo(@NonNull Parcel source)
    {
        id = (UUID)source.readSerializable();
        filePath = source.readParcelable(Uri.class.getClassLoader());
        url = source.readString();
        fileName = source.readString();
        description = source.readString();
        mimeType = source.readString();
        totalBytes = source.readLong();
        statusCode = source.readInt();
        wifiOnly = source.readByte() > 0;
        numPieces = source.readInt();
        retry = source.readByte() > 0;
        statusMsg = source.readString();
        dateAdded = source.readLong();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeSerializable(id);
        dest.writeParcelable(filePath, flags);
        dest.writeString(url);
        dest.writeString(fileName);
        dest.writeString(description);
        dest.writeString(mimeType);
        dest.writeLong(totalBytes);
        dest.writeInt(statusCode);
        dest.writeByte((byte)(wifiOnly ? 1 : 0));
        dest.writeInt(numPieces);
        dest.writeByte((byte)(retry ? 1 : 0));
        dest.writeString(statusMsg);
        dest.writeLong(dateAdded);
    }

    public static final Parcelable.Creator<DownloadInfo> CREATOR =
            new Parcelable.Creator<DownloadInfo>()
            {
                @Override
                public DownloadInfo createFromParcel(Parcel source)
                {
                    return new DownloadInfo(source);
                }

                @Override
                public DownloadInfo[] newArray(int size)
                {
                    return new DownloadInfo[size];
                }
            };

    public void setNumPieces(int numPieces)
    {
        if (numPieces <= 0)
            throw new IllegalArgumentException("Piece number can't be less or equal zero");

        if (!partialSupport && numPieces > 1)
            throw new IllegalStateException("The download doesn't support partial download");

        this.numPieces = numPieces;
    }

    public int getNumPieces()
    {
        return numPieces;
    }

    public List<DownloadPiece> makePieces()
    {
        List<DownloadPiece> pieces = new ArrayList<>();
        long piecesSize = -1;
        long lastPieceSize = -1;
        if (totalBytes != -1) {
            piecesSize = totalBytes / numPieces;
            lastPieceSize = piecesSize + totalBytes % numPieces;
        }

        long curBytes = 0;
        for (int i = 0; i < numPieces; i++) {
            long pieceSize = (i == numPieces - 1 ? lastPieceSize : piecesSize);
            pieces.add(new DownloadPiece(id, i, pieceSize, curBytes));
            curBytes += pieceSize;
        }

        return pieces;
    }

    public long pieceStartPos(DownloadPiece piece)
    {
        if (piece == null || totalBytes == -1)
            return 0;

        long pieceSize = (int)(totalBytes / numPieces);

        return piece.index * pieceSize;
    }

    public long pieceEndPos(DownloadPiece piece)
    {
        if (piece == null)
            return 0;

        return (piece.index < 0 ?
                -1 :
                pieceStartPos(piece) + piece.size - 1);
    }

    @Override
    public int compareTo(@NonNull DownloadInfo another)
    {
        return fileName.compareTo(another.fileName);
    }

    @Override
    public int hashCode()
    {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof DownloadInfo && (o == this || id == ((DownloadInfo)o).id);
    }

    @Override
    public String toString()
    {
        return "DownloadInfo{" +
                "id=" + id +
                ", filePath=" + filePath +
                ", url='" + url + '\'' +
                ", fileName='" + fileName + '\'' +
                ", description='" + description + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", totalBytes=" + totalBytes +
                ", numPieces=" + numPieces +
                ", statusCode=" + statusCode +
                ", wifiOnly=" + wifiOnly +
                ", retry=" + retry +
                ", partialSupport=" + partialSupport +
                ", statusMsg='" + statusMsg + '\'' +
                ", dateAdded=" + dateAdded +
                '}';
    }
}