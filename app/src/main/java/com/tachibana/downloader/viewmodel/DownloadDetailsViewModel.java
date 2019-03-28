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
import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import com.tachibana.downloader.MainApplication;
import com.tachibana.downloader.core.ChangeableParams;
import com.tachibana.downloader.core.DownloadEngine;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.entity.DownloadPiece;
import com.tachibana.downloader.core.entity.InfoAndPieces;
import com.tachibana.downloader.core.exception.FileAlreadyExistsException;
import com.tachibana.downloader.core.exception.FreeSpaceException;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.utils.DigestUtils;
import com.tachibana.downloader.core.utils.FileUtils;
import com.tachibana.downloader.dialog.AddDownloadDialog;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.databinding.Observable;
import androidx.databinding.library.baseAdapters.BR;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
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
    public MutableLiveData<Boolean> paramsChanged = new MutableLiveData<>();

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

        repo = ((MainApplication)getApplication()).getRepository();
        engine = ((MainApplication)getApplication()).getDownloadEngine();
        paramsChanged.setValue(false);
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
    }

    private final Observable.OnPropertyChangedCallback mutableParamsCallback = new Observable.OnPropertyChangedCallback()
    {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId)
        {
            if (propertyId == BR.dirPath) {
                Uri dirPath = mutableParams.getDirPath();
                if (dirPath != null) {
                    info.setStorageFreeSpace(FileUtils.getDirAvailableBytes(getApplication(), dirPath));
                    info.setDirName(FileUtils.getDirName(getApplication(), dirPath));
                }
            }

            checkParamsChanged();
        }
    };

    private void checkParamsChanged()
    {
        DownloadInfo downloadInfo = info.getDownloadInfo();
        if (info == null)
            return;

        boolean changed = !downloadInfo.url.equals(mutableParams.getUrl()) ||
                !downloadInfo.fileName.equals(mutableParams.getFileName()) ||
                !downloadInfo.dirPath.equals(mutableParams.getDirPath()) ||
                !(TextUtils.isEmpty(mutableParams.getDescription()) || mutableParams.getDescription().equals(downloadInfo.description)) ||
                downloadInfo.unmeteredConnectionsOnly != mutableParams.isUnmeteredConnectionsOnly() ||
                downloadInfo.retry != mutableParams.isRetry();

        paramsChanged.setValue(changed);
    }

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

        Uri filePath = FileUtils.getFileUri(getApplication(), downloadInfo.dirPath, downloadInfo.fileName);
        if (filePath == null)
            return null;

        ContentResolver resolver = getApplication().getContentResolver();
        try (ParcelFileDescriptor outPfd = resolver.openFileDescriptor(filePath, "r")) {
            FileDescriptor outFd = outPfd.getFileDescriptor();
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

        if (!downloadInfo.url.equals(url))
            params.url = url;
        if (!downloadInfo.fileName.equals(fileName))
            params.fileName = fileName;
        if (!downloadInfo.dirPath.equals(dirPath))
            params.dirPath = dirPath;
        if (!(TextUtils.isEmpty(description) || description.equals(downloadInfo.description)))
            params.description = description;
        if (downloadInfo.unmeteredConnectionsOnly != unmeteredConnectionsOnly)
            params.unmeteredConnectionsOnly = unmeteredConnectionsOnly;
        if (downloadInfo.retry != retry)
            params.retry = retry;

        return params;
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

        Uri filePath = FileUtils.getFileUri(getApplication(), dirPath, fileName);

        return filePath != null;
    }
}
