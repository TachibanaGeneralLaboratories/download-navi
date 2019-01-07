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
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import com.tachibana.downloader.MainApplication;
import com.tachibana.downloader.R;
import com.tachibana.downloader.core.AddDownloadParams;
import com.tachibana.downloader.core.AppExecutors;
import com.tachibana.downloader.core.HttpConnection;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.entity.Header;
import com.tachibana.downloader.core.entity.UserAgent;
import com.tachibana.downloader.core.exception.HttpException;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.utils.FileUtils;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.dialog.AddDownloadDialog;
import com.tachibana.downloader.service.DownloadScheduler;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class AddDownloadViewModel extends AndroidViewModel
{
    @SuppressWarnings("unused")
    private static final String TAG = AddDownloadDialog.class.getSimpleName();

    private FetchLinkTask fetchTask;
    private DataRepository repo;
    private AppExecutors appExecutors;
    public AddDownloadParams params = new AddDownloadParams();
    public MutableLiveData<State> fetchState = new MutableLiveData<>();

    public enum Status
    {
        UNKNOWN,
        FETCHING,
        FETCHED,
        ERROR
    }

    public static class State
    {
        public Status status;
        public Throwable error;

        public State(Status status, Throwable error)
        {
            this.status = status;
            this.error = error;
        }
        public State(Status status)
        {
            this(status, null);
        }
    }

    public AddDownloadViewModel(@NonNull Application application)
    {
        super(application);

        repo = ((MainApplication)getApplication()).getRepository();
        appExecutors = ((MainApplication)getApplication()).getAppExecutors();
        fetchState.setValue(new State(Status.UNKNOWN));
    }

    public LiveData<List<UserAgent>> observerUserAgents()
    {
        return repo.observeUserAgents();
    }

    public void deleteUserAgent(UserAgent userAgent) {
        if (userAgent == null)
            return;

        appExecutors.databaseIO().execute(() -> repo.deleteUserAgent(userAgent));
    }

    public void addUserAgent(UserAgent userAgent)
    {
        if (userAgent == null)
            return;

        appExecutors.databaseIO().execute(() -> repo.addUserAgent(userAgent));
    }

    public void startFetchTask()
    {
        if (params.getUrl() == null || fetchTask != null && fetchTask.getStatus() != FetchLinkTask.Status.FINISHED)
            return;

        params.setUrl(Utils.normalizeURL(params.getUrl()));

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
                viewModel.get().fetchState.setValue(new State(AddDownloadViewModel.Status.FETCHING));
        }

        @Override
        protected Throwable doInBackground(String... params)
        {
            if (viewModel.get() == null || isCancelled())
                return null;

            String url = params[0];
            if (url == null)
                return null;

            HttpConnection connection;
            try {
                connection = new HttpConnection(url);
            } catch (Exception e) {
                return e;
            }

            NetworkInfo netInfo = Utils.getActiveNetworkInfo(viewModel.get().getApplication().getApplicationContext());
            if (netInfo == null || !netInfo.isConnected())
                return new ConnectException("Network is disconnected");

            Exception err[] = new Exception[1];
            connection.setListener(new HttpConnection.Listener() {
                @Override
                public void onConnectionCreated(HttpURLConnection conn)
                {
                    /* TODO: user agent spoofing (from settings) */
                }

                @Override
                public void onResponseHandle(HttpURLConnection conn, int code, String message)
                {
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
                    /* Nothing */
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
                viewModel.get().fetchState.setValue(new State(AddDownloadViewModel.Status.ERROR, e));
            } else {
                viewModel.get().fetchState.setValue(new State(AddDownloadViewModel.Status.FETCHED));
            }
        }
    }

    private void parseOkHeaders(HttpURLConnection conn)
    {
        String contentDisposition = conn.getHeaderField("Content-Disposition");
        String contentLocation = conn.getHeaderField("Content-Location");

        params.setFileName(Utils.getHttpFileName(params.getUrl(), contentDisposition, contentLocation));
        params.setMimeType(Intent.normalizeMimeType(conn.getContentType()));
        if (params.getMimeType() == null)
            params.setMimeType("application/octet-stream");
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
    }

    public void addDownload(Uri filePath)
    {
        if (params == null || filePath == null)
            return;

        if (!checkFreeSpace(filePath))
            return;

        params.setFilePath(filePath);
        /*
         * File name could have changed in the file creation dialog or
         * an extension was added to it
         */
        DocumentFile file = DocumentFile.fromSingleUri(getApplication(), params.getFilePath());
        String fileName = file.getName();
        if (fileName != null)
            params.setFileName(fileName);

        DownloadInfo info = params.toDownloadInfo();
        info.dateAdded = System.currentTimeMillis();

        ArrayList<Header> headers = new ArrayList<>();
        headers.add(new Header(info.id, "User-Agent", params.getUserAgent()));
        headers.add(new Header(info.id, "ETag", params.getEtag()));

        /* Sync wait inserting */
        try {
            Thread t = new Thread(() -> repo.addInfo(getApplication(), info, headers));
            t.start();
            t.join();

        } catch (InterruptedException e) {
            return;
        }

        DownloadScheduler.runDownload(getApplication(), info);
    }

    private boolean checkFreeSpace(Uri filePath)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            long availBytes = -1L;
            try {
                ParcelFileDescriptor pfd = getApplication().getContentResolver().openFileDescriptor(filePath, "r");
                availBytes = FileUtils.getAvailableBytes(pfd.getFileDescriptor());

            } catch (Exception e) {
                /* Ignore */
            }

            if (availBytes < params.getTotalBytes()) {
                String totalSizeStr = Formatter.formatFileSize(getApplication(), params.getTotalBytes());
                String availSizeStr = Formatter.formatFileSize(getApplication(), availBytes);
                String format = getApplication().getString(R.string.download_error_no_enough_free_space);

                Toast.makeText(getApplication(),
                        String.format(format, availSizeStr, totalSizeStr),
                        Toast.LENGTH_LONG)
                        .show();

                return false;
            }

            return true;
        }

        return true;
    }

    public void finish()
    {
        if (fetchTask != null)
            fetchTask.cancel(true);
    }
}
