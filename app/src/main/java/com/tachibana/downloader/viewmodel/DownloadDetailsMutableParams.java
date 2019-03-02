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

package com.tachibana.downloader.viewmodel;

import android.net.Uri;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.ObservableField;
import androidx.databinding.library.baseAdapters.BR;

public class DownloadDetailsMutableParams extends BaseObservable
{
    private String url;
    private String fileName;
    private String description;
    private ObservableField<Uri> dirPath = new ObservableField<>();
    private boolean wifiOnly = false;
    private boolean retry = false;

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

    public ObservableField<Uri> getDirPath()
    {
        return dirPath;
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
    public String toString()
    {
        return "DownloadDetailsMutableParams{" +
                "url='" + url + '\'' +
                ", fileName='" + fileName + '\'' +
                ", description='" + description + '\'' +
                ", dirPath=" + dirPath +
                ", wifiOnly=" + wifiOnly +
                ", retry=" + retry +
                '}';
    }
}
