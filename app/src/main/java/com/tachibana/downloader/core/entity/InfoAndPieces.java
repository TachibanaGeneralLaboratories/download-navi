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

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Embedded;
import androidx.room.Ignore;
import androidx.room.Relation;

public class InfoAndPieces implements Parcelable
{
    @NonNull
    @Embedded
    public DownloadInfo info;
    @NonNull
    @Relation(parentColumn = "id", entityColumn = "infoId")
    public List<DownloadPiece> pieces;

    /*
     * Do not use, only for DAO
     */
    public InfoAndPieces() { }

    @Ignore
    public InfoAndPieces(Parcel source)
    {
        info = source.readParcelable(DownloadInfo.class.getClassLoader());
        pieces = source.createTypedArrayList(DownloadPiece.CREATOR);
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeParcelable(info, flags);
        dest.writeTypedList(pieces);
    }

    public static final Creator<InfoAndPieces> CREATOR =
            new Creator<InfoAndPieces>()
            {
                @Override
                public InfoAndPieces createFromParcel(Parcel source)
                {
                    return new InfoAndPieces(source);
                }

                @Override
                public InfoAndPieces[] newArray(int size)
                {
                    return new InfoAndPieces[size];
                }
            };

    @Override
    public int hashCode()
    {
        return info.id.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (!(o instanceof InfoAndPieces))
            return false;

        if (o == this)
            return true;

        InfoAndPieces infoAndPieces = (InfoAndPieces)o;

        if (pieces.size() != infoAndPieces.pieces.size())
            return false;

        return info.equals(infoAndPieces.info) &&
                pieces.containsAll(infoAndPieces.pieces);
    }

    @Override
    public String toString()
    {
        return "InfoAndPieces{" +
                "info=" + info +
                ", pieces=" + pieces +
                '}';
    }
}
