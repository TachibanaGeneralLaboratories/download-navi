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

package com.tachibana.downloader.ui.adddownload;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class AddInitParams implements Parcelable
{
    public String url;
    public String fileName;
    public String description;
    public String userAgent;
    public Uri dirPath;
    public boolean unmeteredConnectionsOnly = false;
    public boolean retry = true;
    public boolean replaceFile = false;

    public AddInitParams() {}

    public AddInitParams(@NonNull Parcel source)
    {
        dirPath = source.readParcelable(Uri.class.getClassLoader());
        url = source.readString();
        fileName = source.readString();
        description = source.readString();
        userAgent = source.readString();
        unmeteredConnectionsOnly = source.readByte() > 0;
        retry = source.readByte() > 0;
        replaceFile = source.readByte() > 0;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeParcelable(dirPath, flags);
        dest.writeString(url);
        dest.writeString(fileName);
        dest.writeString(description);
        dest.writeString(userAgent);
        dest.writeByte((byte)(unmeteredConnectionsOnly ? 1 : 0));
        dest.writeByte((byte)(retry ? 1 : 0));
        dest.writeByte((byte)(replaceFile ? 1 : 0));
    }

    public static final Parcelable.Creator<AddInitParams> CREATOR =
            new Parcelable.Creator<AddInitParams>()
            {
                @Override
                public AddInitParams createFromParcel(Parcel source)
                {
                    return new AddInitParams(source);
                }

                @Override
                public AddInitParams[] newArray(int size)
                {
                    return new AddInitParams[size];
                }
            };

    @Override
    public String toString()
    {
        return "AddInitParams{" +
                "url='" + url + '\'' +
                ", fileName='" + fileName + '\'' +
                ", description='" + description + '\'' +
                ", userAgent='" + userAgent + '\'' +
                ", dirPath=" + dirPath +
                ", unmeteredConnectionsOnly=" + unmeteredConnectionsOnly +
                ", retry=" + retry +
                ", replaceFile=" + replaceFile +
                '}';
    }
}
