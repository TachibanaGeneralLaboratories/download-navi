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

package com.tachibana.downloader.ui.details;

import com.tachibana.downloader.BR;
import com.tachibana.downloader.core.model.data.entity.DownloadInfo;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

public class DownloadDetailsInfo extends BaseObservable
{
    private DownloadInfo downloadInfo;
    private long downloadedBytes = -1;
    private String dirName;
    private String md5Hash;
    private String sha256Hash;
    private long storageFreeSpace = -1;
    private HashSumState md5State = HashSumState.UNKNOWN;
    private HashSumState sha256State = HashSumState.UNKNOWN;

    public enum HashSumState
    {
        UNKNOWN,
        CALCULATION,
        CALCULATED
    }

    @Bindable
    public DownloadInfo getDownloadInfo()
    {
        return downloadInfo;
    }

    public void setDownloadInfo(DownloadInfo downloadInfo)
    {
        this.downloadInfo = downloadInfo;
        notifyPropertyChanged(BR.downloadInfo);
    }

    @Bindable
    public long getDownloadedBytes()
    {
        return downloadedBytes;
    }

    public void setDownloadedBytes(long downloadedBytes)
    {
        this.downloadedBytes = downloadedBytes;
        notifyPropertyChanged(BR.downloadedBytes);
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
    public String getMd5Hash()
    {
        return md5Hash;
    }

    public void setMd5Hash(String md5Hash)
    {
        this.md5Hash = md5Hash;
        notifyPropertyChanged(BR.md5Hash);
    }

    @Bindable
    public String getSha256Hash()
    {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash)
    {
        this.sha256Hash = sha256Hash;
        notifyPropertyChanged(BR.sha256Hash);
    }

    @Bindable
    public HashSumState getMd5State()
    {
        return md5State;
    }

    public void setMd5State(HashSumState state)
    {
        md5State = state;
        notifyPropertyChanged(BR.md5State);
    }

    @Bindable
    public HashSumState getSha256State()
    {
        return sha256State;
    }

    public void setSha256State(HashSumState state)
    {
        sha256State = state;
        notifyPropertyChanged(BR.sha256State);
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

    @Override
    public String toString()
    {
        return "DownloadDetailsInfo{" +
                "downloadInfo=" + downloadInfo +
                ", downloadedBytes=" + downloadedBytes +
                ", dirName='" + dirName + '\'' +
                ", md5Hash='" + md5Hash + '\'' +
                ", sha256Hash='" + sha256Hash + '\'' +
                ", md5State=" + md5State +
                ", sha256State=" + sha256State +
                '}';
    }
}
