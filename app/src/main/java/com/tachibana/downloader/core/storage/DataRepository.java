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
import android.net.Uri;
import android.util.Log;

import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.model.data.entity.DownloadPiece;
import com.tachibana.downloader.core.model.data.entity.Header;
import com.tachibana.downloader.core.model.data.entity.InfoAndPieces;
import com.tachibana.downloader.core.model.data.entity.UserAgent;
import com.tachibana.downloader.core.utils.FileUtils;

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

    public void addInfo(DownloadInfo info, List<Header> headers)
    {
        db.downloadDao().addInfo(info, headers);
    }

    public void replaceInfoByUrl(DownloadInfo info, List<Header> headers)
    {
        db.downloadDao().replaceInfoByUrl(info, headers);
    }

    public void updateInfo(Context context, DownloadInfo info,
                           boolean filePathChanged, boolean rebuildPieces)
    {
        if (context == null)
            return;

        if (filePathChanged) {
            DownloadInfo oldInfo = db.downloadDao().getInfoById(info.id);
            if (oldInfo == null)
                return;
        }
        if (rebuildPieces)
            db.downloadDao().updateInfoWithPieces(info);
        else
            db.downloadDao().updateInfo(info);
    }

    public void deleteInfo(Context context, DownloadInfo info, boolean withFile)
    {
        db.downloadDao().deleteInfo(info);

        if (withFile) {
            try {
                Uri filePath = FileUtils.getFileUri(context, info.dirPath, info.fileName);
                if (filePath == null)
                    return;
                FileUtils.deleteFile(context, filePath);

            } catch (FileNotFoundException | SecurityException e) {
                Log.w(TAG, Log.getStackTraceString(e));
            }
        }
    }

    public Flowable<List<InfoAndPieces>> observeAllInfoAndPieces()
    {
        return db.downloadDao().observeAllInfoAndPieces();
    }

    public Flowable<InfoAndPieces> observeInfoAndPiecesById(UUID id)
    {
        return db.downloadDao().observeInfoAndPiecesById(id);
    }

    public Single<List<InfoAndPieces>> getAllInfoAndPiecesSingle()
    {
        return db.downloadDao().getAllInfoAndPiecesSingle();
    }

    public List<DownloadInfo> getAllInfo()
    {
        return db.downloadDao().getAllInfo();
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


    /*
     * Sorted by status code
     */

    public List<DownloadPiece> getPiecesByIdSorted(UUID infoId)
    {
        return db.downloadDao().getPiecesByIdSorted(infoId);
    }

    public DownloadPiece getPiece(int index, UUID infoId)
    {
        return db.downloadDao().getPiece(index, infoId);
    }

    public List<Header> getHeadersById(UUID infoId)
    {
        return db.downloadDao().getHeadersById(infoId);
    }

    public void addHeader(Header header)
    {
        db.downloadDao().addHeader(header);
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
}