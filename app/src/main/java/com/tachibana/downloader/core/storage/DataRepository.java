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

package com.tachibana.downloader.core.storage;

import android.content.Context;
import android.content.Intent;
import android.provider.DocumentsContract;
import android.util.Log;

import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.entity.DownloadPiece;
import com.tachibana.downloader.core.entity.Header;
import com.tachibana.downloader.core.entity.InfoAndPieces;
import com.tachibana.downloader.core.entity.UserAgent;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.UUID;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import io.reactivex.Flowable;
import io.reactivex.Single;

public class DataRepository
{
    @SuppressWarnings("unused")
    private static final String TAG = DataRepository.class.getSimpleName();

    private static DataRepository INSTANCE;

    private AppDatabase db;
    private MediatorLiveData<List<UserAgent>> userAgents;

    public static DataRepository getInstance(AppDatabase db)
    {
        if (INSTANCE == null) {
            synchronized (DataRepository.class) {
                if (INSTANCE == null)
                    INSTANCE = new DataRepository(db);
            }
        }
        return INSTANCE;
    }

    private DataRepository(AppDatabase db) {
        this.db = db;
        userAgents = new MediatorLiveData<>();

        userAgents.addSource(db.userAgentDao().observeAll(),
                productEntities -> {
                    if (db.getDatabaseCreated().getValue() != null)
                        userAgents.postValue(productEntities);
                });
    }

    public void addInfo(Context context, DownloadInfo info, List<Header> headers)
    {
        if (context == null)
            return;
        takeUriPermission(context, info);
        db.downloadDao().addInfo(info, headers);
    }

    public void updateInfo(Context context, DownloadInfo info,
                           boolean filePathChanged, boolean numPiecesChanged)
    {
        if (context == null)
            return;

        if (filePathChanged) {
            DownloadInfo oldInfo = db.downloadDao().getInfoById(info.id);
            if (oldInfo == null)
                return;
            releaseUriPermission(context, oldInfo);
            takeUriPermission(context, info);
        }
        if (numPiecesChanged)
            db.downloadDao().updateInfoWithPieces(info);
        else
            db.downloadDao().updateInfo(info);
    }

    public void deleteInfo(Context context, DownloadInfo info, boolean withFile)
    {
        db.downloadDao().deleteInfo(info);

        if (withFile) {
            try {
                DocumentsContract.deleteDocument(context.getContentResolver(), info.filePath);

            } catch (FileNotFoundException | SecurityException e) {
                Log.w(TAG, Log.getStackTraceString(e));
            }

        } else {
            try {
                releaseUriPermission(context, info);

            } catch (SecurityException e) {
                /* Ignore */
            }
        }
    }

    public Single<List<DownloadInfo>> getAllInfoSingle()
    {
        return db.downloadDao().getAllInfoSingle();
    }

    public Flowable<InfoAndPieces> observeInfoAndPiecesById(UUID id)
    {
        return db.downloadDao().observeInfoAndPiecesById(id);
    }

    public DownloadInfo getInfoById(UUID id)
    {
        return db.downloadDao().getInfoById(id);
    }

    public Single<DownloadInfo> getInfoByIdSingle(UUID id)
    {
        return db.downloadDao().getInfoByIdSingle(id);
    }

    public int updatePiece(DownloadPiece piece)
    {
        return db.downloadDao().updatePiece(piece);
    }

    public List<DownloadPiece> getPiecesById(UUID infoId)
    {
        return db.downloadDao().getPiecesById(infoId);
    }

    public DownloadPiece getPiece(int index, UUID infoId)
    {
        return db.downloadDao().getPiece(index, infoId);
    }

    public List<Header> getHeadersById(UUID infoId)
    {
        return db.downloadDao().getHeadersById(infoId);
    }

    public void addUserAgent(UserAgent agent)
    {
        db.userAgentDao().add(agent);
    }

    public void deleteUserAgent(UserAgent agent)
    {
        db.userAgentDao().delete(agent);
    }

    public LiveData<List<UserAgent>> observeUserAgents()
    {
        return db.userAgentDao().observeAll();
    }

    private void releaseUriPermission(Context context, DownloadInfo info)
    {
        int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        context.getContentResolver().releasePersistableUriPermission(info.filePath, takeFlags);
    }

    private void takeUriPermission(Context context, DownloadInfo info)
    {
        int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        context.getContentResolver().takePersistableUriPermission(info.filePath, takeFlags);
    }
}