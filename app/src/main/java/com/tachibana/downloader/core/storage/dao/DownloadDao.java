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

package com.tachibana.downloader.core.storage.dao;

import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.entity.DownloadPiece;
import com.tachibana.downloader.core.entity.Header;
import com.tachibana.downloader.core.entity.InfoAndPieces;

import java.util.List;
import java.util.UUID;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import io.reactivex.Flowable;
import io.reactivex.Single;

@Dao
public abstract class DownloadDao
{
    private static final String QUERY_GET_ALL_INFO = "SELECT * FROM DownloadInfo";
    private static final String QUERY_GET_INFO_BY_ID = "SELECT * FROM DownloadInfo WHERE id = :id";
    private static final String QUERY_DELETE_PIECES = "DELETE FROM DownloadPiece WHERE infoId = :infoId";
    private static final String QUERY_GET_PIECES_BY_ID = "SELECT * FROM DownloadPiece WHERE infoId = :infoId";
    private static final String QUERY_GET_PIECE = "SELECT * FROM DownloadPiece WHERE pieceIndex = :index AND infoId = :infoId";
    private static final String QUERY_GET_HEADERS = "SELECT * FROM download_info_headers WHERE infoId = :infoId";

    @Transaction
    public void addInfo(DownloadInfo info)
    {
        add_info(info);
        addPieces(info.makePieces());
    }

    @Transaction
    public void addInfo(DownloadInfo info, List<Header> headers)
    {
        addInfo(info);
        if (headers != null && !headers.isEmpty())
            addHeaders(headers);
    }

    @Delete
    public abstract void deleteInfo(DownloadInfo info);

    @Delete
    public abstract void deleteInfoList(List<DownloadInfo> infoList);

    @Update
    public abstract void updateInfo(DownloadInfo info);

    @Transaction
    public void updateInfoWithPieces(DownloadInfo info)
    {
        updateInfo(info);
        deletePieces(info.id);
        addPieces(info.makePieces());
    }

    @Query(QUERY_GET_ALL_INFO)
    public abstract Single<List<DownloadInfo>> getAllInfoSingle();

    @Transaction
    @Query(QUERY_GET_ALL_INFO)
    public abstract Flowable<List<InfoAndPieces>> observeAllInfoAndPieces();

    @Query(QUERY_GET_INFO_BY_ID)
    public abstract DownloadInfo getInfoById(UUID id);

    @Query(QUERY_GET_INFO_BY_ID)
    public abstract Single<DownloadInfo> getInfoByIdSingle(UUID id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void addPieces(List<DownloadPiece> pieces);

    @Query(QUERY_DELETE_PIECES)
    public abstract void deletePieces(UUID infoId);

    @Update
    public abstract int updatePiece(DownloadPiece piece);

    @Query(QUERY_GET_PIECES_BY_ID)
    public abstract List<DownloadPiece> getPiecesById(UUID infoId);

    @Query(QUERY_GET_PIECE)
    public abstract DownloadPiece getPiece(int index, UUID infoId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void addHeaders(List<Header> headers);

    @Query(QUERY_GET_HEADERS)
    public abstract List<Header> getHeadersById(UUID infoId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void addHeader(Header header);

    @Insert
    public abstract void add_info(DownloadInfo info);
}