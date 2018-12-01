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

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;

public class AddDownloadParams implements Parcelable
{
    public String url;
    /* SAF storage */
    public Uri filePath;
    public String fileName;
    public String description;
    public String mimeType;
    public String etag;
    public String userAgent;
    public int numPieces = DownloadInfo.MIN_PIECES;
    public long totalBytes = -1;
    public boolean wifiOnly = false;
    public boolean partialSupport = true;

    public AddDownloadParams(String url)
    {
        this.url = url;
    }

    public AddDownloadParams(Parcel source)
    {
        filePath = source.readParcelable(Uri.class.getClassLoader());
        url = source.readString();
        fileName = source.readString();
        description = source.readString();
        mimeType = source.readString();
        etag = source.readString();
        userAgent = source.readString();
        totalBytes = source.readLong();
        partialSupport = source.readByte() != 0;
        wifiOnly = source.readByte() != 0;
        numPieces = source.readInt();
    }

    public DownloadInfo toDownloadInfo()
    {
        DownloadInfo info = new DownloadInfo(filePath, url, fileName, mimeType);
        info.setTotalBytes(totalBytes);
        info.setDescription(description);
        info.setWiFiOnly(wifiOnly);
        info.setPartialSupport(partialSupport);
        info.setNumPieces((partialSupport && totalBytes > 0 ? numPieces : DownloadInfo.MIN_PIECES));

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("ETag", etag);
        info.setHeaders(headers);

        return info;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeParcelable(filePath, flags);
        dest.writeString(url);
        dest.writeString(fileName);
        dest.writeString(description);
        dest.writeString(mimeType);
        dest.writeString(etag);
        dest.writeString(userAgent);
        dest.writeLong(totalBytes);
        dest.writeByte((byte)(partialSupport ? 1 : 0));
        dest.writeByte((byte)(wifiOnly ? 1 : 0));
        dest.writeInt(numPieces);
    }

    public static final Creator<AddDownloadParams> CREATOR =
            new Creator<AddDownloadParams>()
            {
                @Override
                public AddDownloadParams createFromParcel(Parcel source)
                {
                    return new AddDownloadParams(source);
                }

                @Override
                public AddDownloadParams[] newArray(int size)
                {
                    return new AddDownloadParams[size];
                }
            };

    @Override
    public String toString()
    {
        return "AddDownloadParams{" +
                "url='" + url + '\'' +
                ", filePath=" + filePath +
                ", fileName='" + fileName + '\'' +
                ", description='" + description + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", etag='" + etag + '\'' +
                ", userAgent='" + userAgent + '\'' +
                ", numPieces=" + numPieces +
                ", totalBytes=" + totalBytes +
                ", wifiOnly=" + wifiOnly +
                ", partialSupport=" + partialSupport +
                '}';
    }
}
