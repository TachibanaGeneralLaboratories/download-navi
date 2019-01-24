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

import android.app.Application;

import com.tachibana.downloader.MainApplication;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.entity.InfoAndPieces;
import com.tachibana.downloader.core.storage.DataRepository;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import io.reactivex.Completable;
import io.reactivex.Flowable;

public class DownloadsViewModel extends AndroidViewModel
{
    private DataRepository repo;

    public DownloadsViewModel(@NonNull Application application)
    {
        super(application);

        repo = ((MainApplication)getApplication()).getRepository();
    }

    public Flowable<List<InfoAndPieces>> observerAllInfoAndPieces()
    {
        return repo.observeAllInfoAndPieces();
    }

    public Completable deleteDownload(DownloadInfo info, boolean withFile)
    {
        return Completable.fromAction(() -> repo.deleteInfo(getApplication(), info, withFile));
    }

    public Completable deleteDownloads(List<DownloadInfo> infoList, boolean withFile)
    {
        return Completable.fromAction(() -> repo.deleteInfoList(getApplication(), infoList, withFile));
    }
}
