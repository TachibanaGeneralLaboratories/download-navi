/*
 * Copyright (C) 2019 Tachibana General Laboratories, LLC
 * Copyright (C) 2019, 2021 Yaroslav Pronin <proninyaroslav@mail.ru>
 * Copyright (C) 2020 8176135 <elsecaller@8176135.xyz>
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
import com.tachibana.downloader.core.system.FileDescriptorWrapper;
import com.tachibana.downloader.core.system.FileSystemFacade;
import com.tachibana.downloader.core.system.SystemFacade;
import com.tachibana.downloader.core.system.SystemFacadeHelper;
import com.tachibana.downloader.core.urlnormalizer.NormalizeUrl;
import com.tachibana.downloader.core.utils.DigestUtils;
import com.tachibana.downloader.core.utils.MimeTypeUtils;
import com.tachibana.downloader.core.utils.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class AddDownloadViewModel extends AndroidViewModel
{
    @SuppressWarnings("unused")
    private static final String TAG = AddDownloadViewModel.class.getSimpleName();

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
    private CompositeDisposable disposables = new CompositeDisposable();

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

        disposables.clear();
        params.removeOnPropertyChangedCallback(paramsCallback);
    }

    public void initParams(AddInitParams initParams)
    {
        if (TextUtils.isEmpty(initParams.url)) {
            /* Inserting a link from the clipboard */
            List<CharSequence> clipboard = Utils.getClipboardText(getApplication());
            if (!clipboard.isEmpty()) {
                String firstItem = clipboard.get(0).toString();
                String c = firstItem.toLowerCase();
                if (c.startsWith(Utils.HTTP_PREFIX))
                    initParams.url = firstItem;
            }
        }

        params.setUrl(initParams.url);
        params.setFileName(initParams.fileName);
        params.setDescription(initParams.description);
        params.setUserAgent(initParams.userAgent == null ?
                getPrefUserAgent().userAgent :
                initParams.userAgent);
        params.setDirPath(initParams.dirPath == null ?
                Uri.fromFile(new File(fs.getDefaultDownloadPath())) :
                initParams.dirPath);
        params.setUnmeteredConnectionsOnly(
                initParams.unmeteredConnectionsOnly == null ?
                        false :
                        initParams.unmeteredConnectionsOnly
        );
        params.setRetry(
                initParams.retry == null ?
                        false :
                        initParams.retry
        );
        params.setReplaceFile(
                initParams.replaceFile == null ?
                        false :
                        initParams.replaceFile
        );
        params.setNumPieces(
                initParams.numPieces == null ?
                        DownloadInfo.MIN_PIECES :
                        initParams.numPieces
        );
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
        fetchTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params.getUrl(), params.getReferer());
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

            final Exception[] err = new Exception[1];
            final boolean[] connectWithReferer = new boolean[] {false};
            do {
                HttpConnection connection;
                try {
                    connection = new HttpConnection(url);
                } catch (Exception e) {
                    return e;
                }
                connection.setTimeout(viewModel.get().pref.timeout());
                connection.setReferer(
                        params[1] == null && connectWithReferer[0] ?
                                params[0] :
                                params[1]
                );

                NetworkInfo netInfo = viewModel.get().systemFacade.getActiveNetworkInfo();
                if (netInfo == null || !netInfo.isConnected())
                    return new ConnectException("Network is disconnected");

                connection.setListener(new HttpConnection.Listener() {
                    @Override
                    public void onConnectionCreated(HttpURLConnection conn)
                    {
                        String userAgent = viewModel.get().params.getUserAgent();
                        if (conn.getRequestProperty("User-Agent") == null && !TextUtils.isEmpty(userAgent)) {
                            conn.addRequestProperty("User-Agent", userAgent);
                        }
                    }

                    @Override
                    public void onResponseHandle(HttpURLConnection conn, int code, String message)
                    {
                        if (viewModel.get() == null)
                            return;

                        if (code == HttpURLConnection.HTTP_OK) {
                            connectWithReferer[0] = viewModel.get()
                                    .parseOkHeaders(conn, connectWithReferer[0]);
                        } else {
                            err[0] = new HttpException("Failed to fetch link, response code: " + code, code);
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

            } while (connectWithReferer[0]);

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

    private boolean parseOkHeaders(HttpURLConnection conn, boolean needsRefererPrevValue)
    {
        String contentDisposition = conn.getHeaderField("Content-Disposition");
        String contentLocation = conn.getHeaderField("Content-Location");
        String tmpUrl = conn.getURL().toString();

        String mimeType = Intent.normalizeMimeType(conn.getContentType());
        /* Try to determine the MIME type later by the filename extension */
        if ("application/octet-stream".equals(mimeType))
            mimeType = null;

        String fileName = Utils.getHttpFileName(
                fs,
                tmpUrl,
                contentDisposition,
                contentLocation,
                mimeType
        );

        /* Try to get MIME from filename extension */
        if (mimeType == null) {
            String extension = fs.getExtension(fileName);
            if (!TextUtils.isEmpty(extension))
                mimeType = MimeTypeUtils.getMimeTypeFromExtension(extension);
        }
        boolean currentRefererEmpty = TextUtils.isEmpty(params.getReferer());
        boolean needsReferer = currentRefererEmpty &&
                Utils.needsReferer(mimeType, fs.getExtension(fileName));
        if (needsReferer && !needsRefererPrevValue) {
            return true;
        } else if (!needsReferer && needsRefererPrevValue) {
            if (currentRefererEmpty) {
                params.setReferer(params.getUrl());
            }
        }

        if (TextUtils.isEmpty(params.getFileName()))
            params.setFileName(fileName);
        if (mimeType != null)
            params.setMimeType(mimeType);

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

        return false;
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
        if (!TextUtils.isEmpty(params.getReferer())) {
            headers.add(new Header(info.id, "Referer", params.getReferer()));
        }

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
        if (!fs.isValidFatFilename(fileName)) {
            fileName = fs.buildValidFatFilename(params.getFileName());
        }
        String mimeType = params.getMimeType();
        if (TextUtils.isEmpty(fs.getExtension(fileName)) &&
                !"application/octet-stream".equals(mimeType)) {
            fileName = fs.appendExtension(fileName, mimeType);
        }
        if (filePath != null && params.isReplaceFile()) {
            try {
                fs.truncate(filePath, 0);
            } catch (IOException e) {
                Log.w(TAG, "Unable to truncate file size: " +  Log.getStackTraceString(e));
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
                if (dirPath == null)
                    return;

                disposables.add(Completable.fromRunnable(() -> {
                    params.setStorageFreeSpace(fs.getDirAvailableBytes(dirPath));
                    params.setDirName(fs.getDirName(dirPath));
                })
                .subscribeOn(Schedulers.io())
                .subscribe());
            }
        }
    };

    public void finish()
    {
        if (fetchTask != null)
            fetchTask.cancel(true);
    }
}
