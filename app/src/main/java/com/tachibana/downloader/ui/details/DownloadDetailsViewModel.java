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

import android.app.Application;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.databinding.Observable;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.library.baseAdapters.BR;
import androidx.lifecycle.AndroidViewModel;

import com.tachibana.downloader.core.RepositoryHelper;
import com.tachibana.downloader.core.exception.FileAlreadyExistsException;
import com.tachibana.downloader.core.exception.FreeSpaceException;
import com.tachibana.downloader.core.model.ChangeableParams;
import com.tachibana.downloader.core.model.DownloadEngine;
import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.model.data.entity.DownloadPiece;
import com.tachibana.downloader.core.model.data.entity.InfoAndPieces;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.system.FileDescriptorWrapper;
import com.tachibana.downloader.core.system.FileSystemFacade;
import com.tachibana.downloader.core.system.SystemFacadeHelper;
import com.tachibana.downloader.core.utils.DigestUtils;
import com.tachibana.downloader.ui.adddownload.AddDownloadDialog;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.UUID;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class DownloadDetailsViewModel extends AndroidViewModel
{
    @SuppressWarnings("unused")
    private static final String TAG = AddDownloadDialog.class.getSimpleName();

    private DataRepository repo;
    private DownloadEngine engine;
    private CompositeDisposable disposables = new CompositeDisposable();
    public DownloadDetailsInfo info = new DownloadDetailsInfo();
    public DownloadDetailsMutableParams mutableParams = new DownloadDetailsMutableParams();
    public ObservableBoolean showClipboardButton = new ObservableBoolean(false);
    public FileSystemFacade fs;

    @Override
    protected void onCleared()
    {
        super.onCleared();

        disposables.clear();
        mutableParams.removeOnPropertyChangedCallback(mutableParamsCallback);
    }

    public DownloadDetailsViewModel(@NonNull Application application)
    {
        super(application);

        repo = RepositoryHelper.getDataRepository(application);
        fs = SystemFacadeHelper.getFileSystemFacade(application);
        engine = DownloadEngine.getInstance(application);
        mutableParams.addOnPropertyChangedCallback(mutableParamsCallback);
    }

    public Flowable<InfoAndPieces> observeInfoAndPieces(UUID id)
    {
        return repo.observeInfoAndPiecesById(id);
    }

    public void updateInfo(InfoAndPieces infoAndPieces)
    {
        boolean firstUpdate = info.getDownloadInfo() == null;

        info.setDownloadInfo(infoAndPieces.info);
        long downloadedBytes = 0;
        for (DownloadPiece piece : infoAndPieces.pieces)
            downloadedBytes += infoAndPieces.info.getDownloadedBytes(piece);
        info.setDownloadedBytes(downloadedBytes);

        if (firstUpdate)
            initMutableParams();
    }

    private void initMutableParams()
    {
        DownloadInfo downloadInfo = info.getDownloadInfo();
        if (downloadInfo == null)
            return;

        mutableParams.setUrl(downloadInfo.url);
        mutableParams.setFileName(downloadInfo.fileName);
        mutableParams.setDescription(downloadInfo.description);
        mutableParams.setDirPath(downloadInfo.dirPath);
        mutableParams.setUnmeteredConnectionsOnly(downloadInfo.unmeteredConnectionsOnly);
        mutableParams.setRetry(downloadInfo.retry);
        mutableParams.setChecksum(downloadInfo.checksum);
    }

    private final Observable.OnPropertyChangedCallback mutableParamsCallback = new Observable.OnPropertyChangedCallback()
    {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId)
        {
            if (propertyId == BR.dirPath) {
                Uri dirPath = mutableParams.getDirPath();
                if (dirPath != null) {
                    info.setStorageFreeSpace(fs.getDirAvailableBytes(dirPath));
                    info.setDirName(fs.getDirName(dirPath));
                }
            }
        }
    };

    public void calcMd5Hash()
    {
        info.setMd5State(DownloadDetailsInfo.HashSumState.CALCULATION);

        disposables.add(io.reactivex.Observable.fromCallable(() -> calcHashSum(false))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((md5Hash) -> {
                            info.setMd5Hash(md5Hash);
                            info.setMd5State(DownloadDetailsInfo.HashSumState.CALCULATED);
                        },
                        (Throwable t) -> {
                            Log.e(TAG, "md5 calculation error: " +
                                    Log.getStackTraceString(t));
                            info.setMd5State(DownloadDetailsInfo.HashSumState.CALCULATED);
                        }));
    }

    public void calcSha256Hash()
    {
        info.setSha256State(DownloadDetailsInfo.HashSumState.CALCULATION);

        disposables.add(io.reactivex.Observable.fromCallable(() -> calcHashSum(true))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((sha256Hash) -> {
                            info.setSha256Hash(sha256Hash);
                            info.setSha256State(DownloadDetailsInfo.HashSumState.CALCULATED);
                        },
                        (Throwable t) -> {
                            Log.e(TAG, "sha256 calculation error: " +
                                    Log.getStackTraceString(t));
                            info.setSha256State(DownloadDetailsInfo.HashSumState.CALCULATED);
                        }));
    }

    private String calcHashSum(boolean sha256Hash) throws IOException
    {
        DownloadInfo downloadInfo = info.getDownloadInfo();
        if (downloadInfo == null)
            return null;

        Uri filePath = fs.getFileUri(downloadInfo.dirPath, downloadInfo.fileName);
        if (filePath == null)
            return null;

        try (FileDescriptorWrapper w = fs.getFD(filePath)) {
            FileDescriptor outFd = w.open("r");
            try (FileInputStream is = new FileInputStream(outFd)) {
                return (sha256Hash ? DigestUtils.makeSha256Hash(is) : DigestUtils.makeMd5Hash(is));
            }
        }
    }

    public boolean applyChangedParams(boolean checkFileExists) throws FreeSpaceException, FileAlreadyExistsException
    {
        if (!checkFreeSpace())
            throw new FreeSpaceException();

        DownloadInfo downloadInfo = info.getDownloadInfo();
        if (downloadInfo == null)
            return false;

        ChangeableParams params = makeParams(downloadInfo);

        if (!checkFileExists && (params.dirPath != null || params.fileName != null))
            if (checkFileExists(params, downloadInfo))
                throw new FileAlreadyExistsException();

        engine.changeParams(downloadInfo.id, params);

        return true;
    }

    private ChangeableParams makeParams(DownloadInfo downloadInfo)
    {
        ChangeableParams params = new ChangeableParams();

        String url = mutableParams.getUrl();
        String fileName = mutableParams.getFileName();
        Uri dirPath = mutableParams.getDirPath();
        String description = mutableParams.getDescription();
        boolean unmeteredConnectionsOnly = mutableParams.isUnmeteredConnectionsOnly();
        boolean retry = mutableParams.isRetry();
        String checksum = mutableParams.getChecksum();

        if (!downloadInfo.url.equals(url))
            params.url = url;
        if (!downloadInfo.fileName.equals(fileName))
            params.fileName = fileName;
        if (!downloadInfo.dirPath.equals(dirPath))
            params.dirPath = dirPath;
        if (TextUtils.isEmpty(description) || !description.equals(downloadInfo.description))
            params.description = description;
        if (downloadInfo.unmeteredConnectionsOnly != unmeteredConnectionsOnly)
            params.unmeteredConnectionsOnly = unmeteredConnectionsOnly;
        if (downloadInfo.retry != retry)
            params.retry = retry;
        if (TextUtils.isEmpty(checksum) || isChecksumValid(checksum) &&
                !checksum.equals(downloadInfo.checksum))
            params.checksum = checksum;

        return params;
    }

    public boolean isChecksumValid(String checksum)
    {
        if (checksum == null)
            return false;

        return DigestUtils.isMd5Hash(checksum) || DigestUtils.isSha256Hash(checksum);
    }

    private boolean checkFreeSpace()
    {
        long storageFreeSpace = info.getStorageFreeSpace();

        return storageFreeSpace == -1 || storageFreeSpace >= info.getDownloadInfo().totalBytes;
    }

    private boolean checkFileExists(ChangeableParams params, DownloadInfo downloadInfo)
    {
        String fileName = (params.fileName == null ? downloadInfo.fileName : params.fileName);
        Uri dirPath = (params.dirPath == null ? downloadInfo.dirPath : params.dirPath);

        Uri filePath = fs.getFileUri(dirPath, fileName);

        return filePath != null;
    }
}
