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

package com.tachibana.downloader.core.model;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/*
 * Bundle of parameters that need to be changed in the download.
 * All parameters nullable.
 */

public class ChangeableParams implements Parcelable
{
    public String url;
    public String fileName;
    public String description;
    public Uri dirPath;
    public Boolean unmeteredConnectionsOnly;
    public Boolean retry;
    public String checksum;

    public ChangeableParams() {}

    public ChangeableParams(@NonNull Parcel source)
    {
        url = source.readString();
        fileName = source.readString();
        description = source.readString();
        dirPath = source.readParcelable(Uri.class.getClassLoader());
        byte unmeteredOnlyVal = source.readByte();
        if (unmeteredOnlyVal != -1)
            unmeteredConnectionsOnly = unmeteredOnlyVal > 0;
        byte retryVal = source.readByte();
        if (retryVal != -1)
            retry = retryVal > 0;
        checksum = source.readString();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(url);
        dest.writeString(fileName);
        dest.writeString(description);
        dest.writeParcelable(dirPath, flags);
        if (unmeteredConnectionsOnly == null)
            dest.writeByte((byte)-1);
        else
            dest.writeByte((byte)(unmeteredConnectionsOnly ? 1 : 0));
        if (retry == null)
            dest.writeByte((byte)-1);
        else
            dest.writeByte((byte)(retry ? 1 : 0));
        dest.writeString(checksum);
    }

    public static final Parcelable.Creator<ChangeableParams> CREATOR = new Parcelable.Creator<>()
            {
                @Override
                public ChangeableParams createFromParcel(Parcel source)
                {
                    return new ChangeableParams(source);
                }

                @Override
                public ChangeableParams[] newArray(int size)
                {
                    return new ChangeableParams[size];
                }
            };

    @Override
    public String toString()
    {
        return "ChangeableParams{" +
                "url='" + url + '\'' +
                ", fileName='" + fileName + '\'' +
                ", description='" + description + '\'' +
                ", dirPath=" + dirPath +
                ", unmeteredConnectionsOnly=" + unmeteredConnectionsOnly +
                ", retry=" + retry +
                ", checksum='" + checksum + '\'' +
                '}';
    }
}
