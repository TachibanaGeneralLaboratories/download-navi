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

package com.tachibana.downloader.ui.adddownload;

import android.app.Application;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.databinding.Observable;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableInt;
import androidx.databinding.library.baseAdapters.BR;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tachibana.downloader.core.HttpConnection;
import com.tachibana.downloader.core.RepositoryHelper;
import com.tachibana.downloader.core.exception.FreeSpaceException;
import com.tachibana.downloader.core.exception.HttpException;
import com.tachibana.downloader.core.exception.NormalizeUrlException;
import com.tachibana.downloader.core.model.DownloadEngine;
import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.model.data.entity.Header;
import com.tachibana.downloader.core.model.data.entity.UserAgent;
import com.tachibana.downloader.core.settings.SettingsRepository;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.system.FileSystemFacade;
import com.tachibana.downloader.core.system.SystemFacade;
import com.tachibana.downloader.core.system.SystemFacadeHelper;
import com.tachibana.downloader.core.urlnormalizer.NormalizeUrl;
import com.tachibana.downloader.core.utils.DigestUtils;
import com.tachibana.downloader.core.utils.Utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Completable;

public class AddDownloadViewModel extends AndroidViewModel
{
    @SuppressWarnings("unused")
    private static final String TAG = AddDownloadDialog.class.getSimpleName();

    private FetchLinkTask fetchTask;
    private DataRepository repo;
    public SettingsRepository pref;
    private DownloadEngine engine;
    public AddDownloadParams params = new AddDownloadParams();
    public MutableLiveData<FetchState> fetchState = new MutableLiveData<>();
    public ObservableInt maxNumPieces = new ObservableInt(DownloadInfo.MAX_PIECES);
    public ObservableBoolean showClipboardButton = new ObservableBoolean(false);
    public SystemFacade systemFacade;
    public FileSystemFacade fs;

    public enum Status
    {
        UNKNOWN,
        FETCHING,
        FETCHED,
        ERROR
    }

    public static class FetchState
    {
        public Status status;
        public Throwable error;

        public FetchState(Status status, Throwable error)
        {
            this.status = status;
            this.error = error;
        }
        public FetchState(Status status)
        {
            this(status, null);
        }
    }

    public AddDownloadViewModel(@NonNull Application application)
    {
        super(application);

        repo = RepositoryHelper.getDataRepository(application);
        pref = RepositoryHelper.getSettingsRepository(application);
        systemFacade = SystemFacadeHelper.getSystemFacade(application);
        fs = SystemFacadeHelper.getFileSystemFacade(application);
        engine = DownloadEngine.getInstance(application);
        fetchState.setValue(new FetchState(Status.UNKNOWN));
        params.addOnPropertyChangedCallback(paramsCallback);
    }

    @Override
    protected void onCleared()
    {
        super.onCleared();

        params.removeOnPropertyChangedCallback(paramsCallback);
    }

    public LiveData<List<UserAgent>> observeUserAgents()
    {
        return repo.observeUserAgents();
    }

    public Completable deleteUserAgent(UserAgent userAgent)
    {
        if (userAgent.userAgent.equals(params.getUserAgent())) {
            String systemUserAgent = systemFacade.getSystemUserAgent();
            if (systemUserAgent == null)
                systemUserAgent = pref.userAgent();
            params.setUserAgent(systemUserAgent);
        }

        return Completable.fromAction(() -> repo.deleteUserAgent(userAgent));
    }

    public Completable addUserAgent(UserAgent userAgent)
    {
        return Completable.fromAction(() -> repo.addUserAgent(userAgent));
    }

    public UserAgent getPrefUserAgent()
    {
        return new UserAgent(pref.userAgent());
    }

    public void savePrefUserAgent(UserAgent userAgent)
    {
        if (userAgent == null)
            return;

        pref.userAgent(userAgent.userAgent);
    }

    public void startFetchTask()
    {
        if (TextUtils.isEmpty(params.getUrl()) || fetchTask != null && fetchTask.getStatus() != FetchLinkTask.Status.FINISHED)
            return;

        try {
            params.setUrl(NormalizeUrl.normalize(params.getUrl()));

        } catch (NormalizeUrlException e) {
            fetchState.setValue(new FetchState(Status.ERROR, e));
            return;
        }

        fetchTask = new FetchLinkTask(this);
        fetchTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params.getUrl());
    }

    private static class FetchLinkTask extends AsyncTask<String, Void, Throwable>
    {
        private final WeakReference<AddDownloadViewModel> viewModel;

        private FetchLinkTask(AddDownloadViewModel viewModel)
        {
            this.viewModel = new WeakReference<>(viewModel);
        }

        @Override
        protected void onPreExecute()
        {
            if (viewModel.get() != null)
                viewModel.get().fetchState.setValue(new FetchState(AddDownloadViewModel.Status.FETCHING));
        }

        @Override
        protected Throwable doInBackground(String... params)
        {
            if (viewModel.get() == null || isCancelled())
                return null;

            String url = params[0];
            if (url == null)
                return null;

            /* TODO: change after add FTP support */
            if (!url.startsWith(Utils.HTTP_PREFIX))
                return new MalformedURLException(Utils.getScheme(url));

            HttpConnection connection;
            try {
                connection = new HttpConnection(url);
            } catch (Exception e) {
                return e;
            }
            connection.setTimeout(viewModel.get().pref.timeout());

            NetworkInfo netInfo = viewModel.get().systemFacade.getActiveNetworkInfo();
            if (netInfo == null || !netInfo.isConnected())
                return new ConnectException("Network is disconnected");

            Exception err[] = new Exception[1];
            connection.setListener(new HttpConnection.Listener() {
                @Override
                public void onConnectionCreated(HttpURLConnection conn)
                {
                    /* TODO: maybe user agent spoofing (from settings) */
                }

                @Override
                public void onResponseHandle(HttpURLConnection conn, int code, String message)
                {
                    if (viewModel.get() == null)
                        return;

                    switch (code) {
                        case HttpURLConnection.HTTP_OK:
                            viewModel.get().parseOkHeaders(conn);
                            break;
                        default:
                            err[0] = new HttpException("Failed to fetch link, response code: " + code, code);
                            break;
                    }
                }

                @Override
                public void onMovedPermanently(String newUrl)
                {
                    if (viewModel.get() == null)
                        return;

                    try {
                        viewModel.get().params.setUrl(NormalizeUrl.normalize(newUrl));

                    } catch (NormalizeUrlException e) {
                        err[0] = e;
                    }
                }

                @Override
                public void onIOException(IOException e)
                {
                    err[0] = e;
                }

                @Override
                public void onTooManyRedirects()
                {
                    err[0] = new HttpException("Too many redirects");
                }
            });
            connection.run();

            return err[0];
        }

        @Override
        protected void onPostExecute(Throwable e)
        {
            if (viewModel.get() == null)
                return;

            if (e != null) {
                Log.e(TAG, Log.getStackTraceString(e));
                viewModel.get().fetchState.setValue(new FetchState(AddDownloadViewModel.Status.ERROR, e));
            } else {
                viewModel.get().fetchState.setValue(new FetchState(AddDownloadViewModel.Status.FETCHED));
            }
        }
    }

    private void parseOkHeaders(HttpURLConnection conn)
    {
        String contentDisposition = conn.getHeaderField("Content-Disposition");
        String contentLocation = conn.getHeaderField("Content-Location");

        String mimeType = Intent.normalizeMimeType(conn.getContentType());
        if (mimeType != null)
            params.setMimeType(mimeType);
        if (TextUtils.isEmpty(params.getFileName()))
            params.setFileName(Utils.getHttpFileName(fs,
                    params.getUrl(),
                    contentDisposition,
                    contentLocation,
                    mimeType));
        params.setEtag(conn.getHeaderField("ETag"));
        final String transferEncoding = conn.getHeaderField("Transfer-Encoding");
        if (transferEncoding == null) {
            try {
                params.setTotalBytes(Long.parseLong(conn.getHeaderField("Content-Length")));

            } catch (NumberFormatException e) {
                params.setTotalBytes(-1);
            }
        } else {
            params.setTotalBytes(-1);
        }
        params.setPartialSupport("bytes".equalsIgnoreCase(conn.getHeaderField("Accept-Ranges")));

        /* The number of pieces can't be more than the number of bytes */
        long total = params.getTotalBytes();
        if (total > 0)
            maxNumPieces.set(total < maxNumPieces.get() ? (int)total : DownloadInfo.MAX_PIECES);
    }

    /*
     * Throws FileNotFoundException if the stub file doesn't created or doesn't exists
     */

    public void addDownload() throws IOException, FreeSpaceException, NormalizeUrlException
    {
        if (TextUtils.isEmpty(params.getUrl()) || TextUtils.isEmpty(params.getFileName()))
            return;

        Uri dirPath = params.getDirPath();
        if (dirPath == null)
            throw new FileNotFoundException();

        if (!checkFreeSpace())
            throw new FreeSpaceException();

        DownloadInfo info = makeDownloadInfo(dirPath);

        ArrayList<Header> headers = new ArrayList<>();
        headers.add(new Header(info.id, "ETag", params.getEtag()));

        /* TODO: rewrite to WorkManager */
        /* Sync wait inserting */
        try {
            Thread t = new Thread(() -> {
                if (pref.replaceDuplicateDownloads())
                    repo.replaceInfoByUrl(info, headers);
                else
                    repo.addInfo(info, headers);
            });
            t.start();
            t.join();

        } catch (InterruptedException e) {
            return;
        }

        engine.runDownload(info);
    }

    private DownloadInfo makeDownloadInfo(Uri dirPath) throws NormalizeUrlException
    {
        FetchState state = fetchState.getValue();

        String url = params.getUrl();
        if (state != null && state.status != Status.FETCHED)
            url = NormalizeUrl.normalize(url);

        Uri filePath = fs.getFileUri(params.getDirPath(), params.getFileName());

        String fileName = params.getFileName();
        if (!fs.isValidFatFilename(fileName))
            fileName = fs.buildValidFatFilename(params.getFileName());
        fileName = fs.appendExtension(fileName, params.getMimeType());
        if (params.isReplaceFile()) {
            try {
                if (filePath != null)
                    fs.deleteFile(filePath);

            } catch (FileNotFoundException e) {
                /* Ignore */
            }
        } else {
            fileName = fs.makeFilename(params.getDirPath(), fileName);
        }

        DownloadInfo info = new DownloadInfo(dirPath, url, fileName);
        info.mimeType = params.getMimeType();
        info.totalBytes = params.getTotalBytes();
        info.description = params.getDescription();
        info.unmeteredConnectionsOnly = params.isUnmeteredConnectionsOnly();
        info.partialSupport = params.isPartialSupport();
        info.setNumPieces((params.isPartialSupport() && params.getTotalBytes() > 0 ?
                params.getNumPieces() :
                DownloadInfo.MIN_PIECES));
        info.retry = params.isRetry();
        info.userAgent = params.getUserAgent();

        String checksum = params.getChecksum();
        if (isChecksumValid(checksum))
            info.checksum = checksum;

        info.dateAdded = System.currentTimeMillis();

        if (state != null)
            info.hasMetadata = state.status == Status.FETCHED;

        return info;
    }

    private boolean checkFreeSpace()
    {
        long storageFreeSpace = params.getStorageFreeSpace();

        return storageFreeSpace == -1 || storageFreeSpace >= params.getTotalBytes();
    }

    public boolean isChecksumValid(String checksum)
    {
        if (checksum == null)
            return false;

        return DigestUtils.isMd5Hash(checksum) || DigestUtils.isSha256Hash(checksum);
    }

    private final Observable.OnPropertyChangedCallback paramsCallback = new Observable.OnPropertyChangedCallback()
    {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId)
        {
            if (propertyId == BR.dirPath) {
                Uri dirPath = params.getDirPath();
                if (dirPath != null) {
                    params.setStorageFreeSpace(fs.getDirAvailableBytes(dirPath));
                    params.setDirName(fs.getDirName(dirPath));
                }
            }
        }
    };

    public void finish()
    {
        if (fetchTask != null)
            fetchTask.cancel(true);
    }
}
