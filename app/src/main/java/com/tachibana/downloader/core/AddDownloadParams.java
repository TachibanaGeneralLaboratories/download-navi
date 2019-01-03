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

package com.tachibana.downloader.core;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.tachibana.downloader.core.entity.DownloadInfo;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.library.baseAdapters.BR;

public class AddDownloadParams extends BaseObservable implements Parcelable
{
    private String url;
    /* SAF storage */
    private Uri filePath;
    private String fileName;
    private String description;
    private String mimeType;
    private String etag;
    private String userAgent;
    private int numPieces = DownloadInfo.MIN_PIECES;
    private long totalBytes = -1;
    private boolean wifiOnly = false;
    private boolean partialSupport = true;
    private boolean retry = true;

    public AddDownloadParams() {}

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
        retry = source.readByte() != 0;
    }

    public DownloadInfo toDownloadInfo()
    {
        DownloadInfo info = new DownloadInfo(filePath, url, fileName, mimeType);
        info.totalBytes = totalBytes;
        info.description = description;
        info.wifiOnly = wifiOnly;
        info.partialSupport = partialSupport;
        info.setNumPieces((partialSupport && totalBytes > 0 ? numPieces : DownloadInfo.MIN_PIECES));
        info.retry = retry;

        return info;
    }

    @Bindable
    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
        notifyPropertyChanged(BR.url);
    }

    public Uri getFilePath()
    {
        return filePath;
    }

    public void setFilePath(Uri filePath)
    {
        this.filePath = filePath;
    }

    @Bindable
    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
        notifyPropertyChanged(BR.fileName);
    }

    @Bindable
    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
        notifyPropertyChanged(BR.description);
    }

    public String getMimeType()
    {
        return mimeType;
    }

    public void setMimeType(String mimeType)
    {
        this.mimeType = mimeType;
    }

    public String getEtag()
    {
        return etag;
    }

    public void setEtag(String etag)
    {
        this.etag = etag;
    }

    public String getUserAgent()
    {
        return userAgent;
    }

    public void setUserAgent(String userAgent)
    {
        this.userAgent = userAgent;
    }

    @Bindable
    public int getNumPieces()
    {
        return numPieces;
    }

    public void setNumPieces(int numPieces)
    {
        this.numPieces = numPieces;
        notifyPropertyChanged(BR.numPieces);
    }

    @Bindable
    public long getTotalBytes()
    {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes)
    {
        this.totalBytes = totalBytes;
        notifyPropertyChanged(BR.totalBytes);
    }

    @Bindable
    public boolean isWifiOnly()
    {
        return wifiOnly;
    }

    public void setWifiOnly(boolean wifiOnly)
    {
        this.wifiOnly = wifiOnly;
        notifyPropertyChanged(BR.wifiOnly);
    }

    public boolean isPartialSupport()
    {
        return partialSupport;
    }

    public void setPartialSupport(boolean partialSupport)
    {
        this.partialSupport = partialSupport;
    }

    @Bindable
    public boolean isRetry()
    {
        return retry;
    }

    public void setRetry(boolean retry)
    {
        this.retry = retry;
        notifyPropertyChanged(BR.retry);
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
        dest.writeByte((byte)(retry ? 1 : 0));
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
                ", retry=" + retry +
                '}';
    }
}
