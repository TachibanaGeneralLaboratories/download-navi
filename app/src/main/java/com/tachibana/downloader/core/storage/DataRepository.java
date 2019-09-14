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

import androidx.lifecycle.LiveData;

import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.model.data.entity.DownloadPiece;
import com.tachibana.downloader.core.model.data.entity.Header;
import com.tachibana.downloader.core.model.data.entity.InfoAndPieces;
import com.tachibana.downloader.core.model.data.entity.UserAgent;

import java.util.List;
import java.util.UUID;

import io.reactivex.Flowable;
import io.reactivex.Single;

public interface DataRepository
{
    void addInfo(DownloadInfo info, List<Header> headers);

    void replaceInfoByUrl(DownloadInfo info, List<Header> headers);

    void updateInfo(DownloadInfo info,
                    boolean filePathChanged,
                    boolean rebuildPieces);

    void deleteInfo(DownloadInfo info, boolean withFile);

    Flowable<List<InfoAndPieces>> observeAllInfoAndPieces();

    Flowable<InfoAndPieces> observeInfoAndPiecesById(UUID id);

    Single<List<InfoAndPieces>> getAllInfoAndPiecesSingle();

    List<DownloadInfo> getAllInfo();

    DownloadInfo getInfoById(UUID id);

    Single<DownloadInfo> getInfoByIdSingle(UUID id);

    int updatePiece(DownloadPiece piece);

    List<DownloadPiece> getPiecesById(UUID infoId);

    List<DownloadPiece> getPiecesByIdSorted(UUID infoId);

    DownloadPiece getPiece(int index, UUID infoId);

    List<Header> getHeadersById(UUID infoId);

    void addHeader(Header header);

    void addUserAgent(UserAgent agent);

    void deleteUserAgent(UserAgent agent);

    LiveData<List<UserAgent>> observeUserAgents();
}
