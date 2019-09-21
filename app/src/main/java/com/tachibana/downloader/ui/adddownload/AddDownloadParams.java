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

package com.tachibana.downloader.ui.adddownload;

import android.net.Uri;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.library.baseAdapters.BR;

import com.tachibana.downloader.core.model.data.entity.DownloadInfo;

public class AddDownloadParams extends BaseObservable
{
    private String url;
    /* SAF or filesystem storage */
    private Uri dirPath;
    /* Equal with dirPath in case if the path is non-SAF path */
    private String dirName;
    private long storageFreeSpace = -1;
    private String fileName;
    private String description;
    private String mimeType = "application/octet-stream";
    private String etag;
    private String userAgent;
    private int numPieces = DownloadInfo.MIN_PIECES;
    private long totalBytes = -1;
    private boolean unmeteredConnectionsOnly;
    private boolean partialSupport = true;
    private boolean retry;
    private boolean replaceFile;

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

    @Bindable
    public Uri getDirPath()
    {
        return dirPath;
    }

    public void setDirPath(Uri dirPath)
    {
        this.dirPath = dirPath;
        notifyPropertyChanged(BR.dirPath);
    }

    @Bindable
    public String getDirName()
    {
        return dirName;
    }

    public void setDirName(String dirName)
    {
        this.dirName = dirName;
        notifyPropertyChanged(BR.dirName);
    }

    @Bindable
    public long getStorageFreeSpace()
    {
        return storageFreeSpace;
    }

    public void setStorageFreeSpace(long storageFreeSpace)
    {
        this.storageFreeSpace = storageFreeSpace;
        notifyPropertyChanged(BR.storageFreeSpace);
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
    public boolean isUnmeteredConnectionsOnly()
    {
        return unmeteredConnectionsOnly;
    }

    public void setUnmeteredConnectionsOnly(boolean unmeteredConnectionsOnly)
    {
        this.unmeteredConnectionsOnly = unmeteredConnectionsOnly;
        notifyPropertyChanged(BR.unmeteredConnectionsOnly);
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

    @Bindable
    public boolean isReplaceFile()
    {
        return replaceFile;
    }

    public void setReplaceFile(boolean replaceFile)
    {
        this.replaceFile = replaceFile;
        notifyPropertyChanged(BR.replaceFile);
    }

    @Override
    public String toString()
    {
        return "AddDownloadParams{" +
                "url='" + url + '\'' +
                ", dirPath=" + dirPath +
                ", dirName='" + dirName + '\'' +
                ", storageFreeSpace=" + storageFreeSpace +
                ", fileName='" + fileName + '\'' +
                ", description='" + description + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", etag='" + etag + '\'' +
                ", userAgent='" + userAgent + '\'' +
                ", numPieces=" + numPieces +
                ", totalBytes=" + totalBytes +
                ", unmeteredConnectionsOnly=" + unmeteredConnectionsOnly +
                ", partialSupport=" + partialSupport +
                ", retry=" + retry +
                ", replaceFile=" + replaceFile +
                '}';
    }
}
