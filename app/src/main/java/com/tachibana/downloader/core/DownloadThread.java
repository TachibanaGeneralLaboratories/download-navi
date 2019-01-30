/*
 * Copyright (C) 2018, 2019 Tachibana General Laboratories, LLC
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;

import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.entity.DownloadPiece;
import com.tachibana.downloader.core.entity.Header;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.utils.FileUtils;
import com.tachibana.downloader.core.utils.Utils;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.tachibana.downloader.core.StatusCode.STATUS_BAD_REQUEST;
import static com.tachibana.downloader.core.StatusCode.STATUS_CANCELLED;
import static com.tachibana.downloader.core.StatusCode.STATUS_CANNOT_RESUME;
import static com.tachibana.downloader.core.StatusCode.STATUS_FILE_ERROR;
import static com.tachibana.downloader.core.StatusCode.STATUS_HTTP_DATA_ERROR;
import static com.tachibana.downloader.core.StatusCode.STATUS_FETCH_METADATA;
import static com.tachibana.downloader.core.StatusCode.STATUS_PAUSED;
import static com.tachibana.downloader.core.StatusCode.STATUS_PENDING;
import static com.tachibana.downloader.core.StatusCode.STATUS_RUNNING;
import static com.tachibana.downloader.core.StatusCode.STATUS_SUCCESS;
import static com.tachibana.downloader.core.StatusCode.STATUS_TOO_MANY_REDIRECTS;
import static com.tachibana.downloader.core.StatusCode.STATUS_UNHANDLED_HTTP_CODE;
import static com.tachibana.downloader.core.StatusCode.STATUS_UNKNOWN_ERROR;
import static com.tachibana.downloader.core.StatusCode.STATUS_WAITING_FOR_NETWORK;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PRECON_FAILED;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

/*
 * Represent one task of downloading.
 */

public class DownloadThread implements Callable<DownloadResult>
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadThread.class.getSimpleName();

    private DownloadInfo info;
    private UUID id;
    /* Stop and delete */
    private boolean cancel;
    private boolean pause;
    private boolean running;
    private ExecutorService exec;
    private DataRepository repo;
    private Context context;

    public DownloadThread(Context context, DataRepository repo, UUID id)
    {
        this.id = id;
        this.repo = repo;
        this.context = context;
    }

    public void requestCancel()
    {
        cancel = true;
        if (exec != null)
            exec.shutdownNow();
    }

    public void requestPause()
    {
        pause = true;
        if (exec != null)
            exec.shutdownNow();
    }

    public boolean isRunning()
    {
        return running;
    }

    @Override
    public DownloadResult call()
    {
        running = true;
        StopRequest ret = null;
        try {
            info = repo.getInfoById(id);
            if (info == null) {
                Log.w(TAG, "Info " + id + " is null, skipping");
                return new DownloadResult(id, DownloadResult.Status.CANCELLED);
            }

            if (info.statusCode == STATUS_SUCCESS) {
                Log.w(TAG, id + " already finished, skipping");
                return new DownloadResult(id, DownloadResult.Status.FINISHED);
            }

            if (!info.hasMetadata)
                info.statusCode = STATUS_FETCH_METADATA;
            else
                info.statusCode = STATUS_RUNNING;
            info.statusMsg = null;
            writeToDatabase();

            if ((ret = execDownload()) != null) {
                info.statusCode = ret.getFinalStatus();
                info.statusMsg = ret.getMessage();
                Log.i(TAG, "id=" + id + ", " + info.statusMsg);
            } else {
                info.statusCode = STATUS_SUCCESS;
            }

            checkPiecesStatus();

        } catch (Throwable t) {
            Log.e(TAG, Log.getStackTraceString(t));
            if (info != null) {
                info.statusCode = STATUS_UNKNOWN_ERROR;
                info.statusMsg = t.getMessage();
            }

        } finally {
            finalizeThread();
        }

        DownloadResult.Status status = DownloadResult.Status.FINISHED;
        if (info != null) {
            switch (info.statusCode) {
                case STATUS_PAUSED:
                    status = DownloadResult.Status.PAUSED;
                    break;
                case STATUS_CANCELLED:
                    status = DownloadResult.Status.CANCELLED;
                    break;
            }
        }

        return new DownloadResult(id, status);
    }

    private void finalizeThread()
    {
        if (info != null) {
            writeToDatabase();

            if (StatusCode.isStatusError(info.statusCode)) {
                /* When error, free up any disk space */
                ParcelFileDescriptor pfd = null;
                FileOutputStream fout = null;
                try {
                    Uri filePath = FileUtils.getFileUri(context, info.dirPath, info.fileName);
                    if (filePath == null)
                        return;
                    pfd = context.getContentResolver()
                            .openFileDescriptor(filePath, "rw");
                    fout = new FileOutputStream(pfd.getFileDescriptor());

                    FileUtils.ftruncate(fout, 0);

                } catch (Exception e) {
                    /* Ignore */
                } finally {
                    FileUtils.closeQuietly(fout);
                }
            }
        }

        running = false;
        cancel = false;
        pause = false;
    }

    private void checkPiecesStatus()
    {
        List<DownloadPiece> pieces = repo.getPiecesById(id);
        if (pieces == null || pieces.isEmpty()) {
            String errMsg = "Download deleted or missing";
            info.statusCode = STATUS_CANCELLED;
            info.statusMsg = errMsg;
            Log.i(TAG, "id=" + id + ", " + errMsg);

        } else if (pieces.size() != info.getNumPieces()) {
            String errMsg = "Some pieces are missing";
            info.statusCode = STATUS_UNKNOWN_ERROR;
            info.statusMsg = errMsg;
            Log.i(TAG, "id=" + id + ", " + errMsg);

        } else {
            /* If we just finished a chunked file, record total size */
            if (info.totalBytes == -1 && pieces.size() == 1)
                info.totalBytes = pieces.get(0).curBytes;

            /* Check pieces status if we are not cancelled or paused */
            StopRequest ret;
            if ((ret = checkPauseStop()) != null) {
                info.statusCode = ret.getFinalStatus();
            } else {
                for (DownloadPiece piece : pieces) {
                    /* TODO: maybe change handle status behaviour */
                    if (piece.statusCode > info.statusCode) {
                        info.statusCode = piece.statusCode;
                        info.statusMsg = piece.statusMsg;
                        break;
                    }
                }
            }
        }
    }

    private StopRequest execDownload()
    {
        try {
            StopRequest ret;
            if ((ret = checkPauseStop()) != null)
                return ret;
            if (!Utils.checkConnectivity(context))
                return new StopRequest(STATUS_WAITING_FOR_NETWORK);

            if (!info.hasMetadata) {
                if ((ret = fetchMetadata()) != null)
                    return ret;
            }

            /* Create file if doesn't exists or replace it */
            Pair<Uri, String> res;
            try {
                res = FileUtils.createFile(context, info.dirPath,
                        info.fileName, info.mimeType, true);

            } catch (IOException e) {
                return new StopRequest(STATUS_FILE_ERROR, e);
            }
            if (res == null || res.first == null)
                return new StopRequest(STATUS_FILE_ERROR, "Unable to create file");

            Uri filePath = res.first;

            /* Maybe an extension was added to the file name */
            String newName = res.second;
            if (newName != null && !newName.equals(info.fileName)) {
                info.fileName = res.second;
                writeToDatabase();
            }

            /* Check free space */
            long availBytes = FileUtils.getDirAvailableBytes(context, info.dirPath);
            if (availBytes != -1 && availBytes < info.totalBytes)
                return new StopRequest(StatusCode.STATUS_INSUFFICIENT_SPACE_ERROR,
                        "No space left on device");

            /* Pre-flight disk space requirements, when known */
            if (info.totalBytes > 0) {
                if ((ret = allocFileSpace(filePath)) != null)
                    return ret;
            }

            exec = (info.getNumPieces() == 1 ?
                    Executors.newSingleThreadExecutor() :
                    Executors.newFixedThreadPool(info.getNumPieces()));

            for (int i = 0; i < info.getNumPieces(); i++)
                exec.submit(new PieceThread(id, i, context, repo));
            exec.shutdown();
            /* Wait "forever" */
            if (!exec.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS))
                requestCancel();

        } catch (InterruptedException e) {
            requestCancel();
        }

        return null;
    }

    private StopRequest fetchMetadata()
    {
        final StopRequest[] ret = new StopRequest[1];

        HttpConnection connection;
        try {
            connection = new HttpConnection(info.url);

        } catch (MalformedURLException e) {
            return new StopRequest(STATUS_BAD_REQUEST, "bad url " + info.url, e);
        } catch (GeneralSecurityException e) {
            return new StopRequest(STATUS_UNKNOWN_ERROR, "Unable to create SSLContext");
        }

        connection.setListener(new HttpConnection.Listener() {
            @Override
            public void onConnectionCreated(HttpURLConnection conn)
            {
                /* Nothing */
            }

            @Override
            public void onResponseHandle(HttpURLConnection conn, int code, String message)
            {
                switch (code) {
                    case HTTP_OK:
                        ret[0] = parseOkHeaders(conn);
                        break;
                    case HTTP_PRECON_FAILED:
                        ret[0] = new StopRequest(STATUS_CANNOT_RESUME,
                                "Precondition failed");
                        break;
                    case HTTP_UNAVAILABLE:
                        ret[0] = new StopRequest(HTTP_UNAVAILABLE, message);
                        break;
                    case HTTP_INTERNAL_ERROR:
                        ret[0] = new StopRequest(HTTP_INTERNAL_ERROR, message);
                        break;
                    default:
                        ret[0] = StopRequest.getUnhandledHttpError(code, message);
                        break;
                }
            }

            @Override
            public void onMovedPermanently(String newUrl)
            {
                info.url = newUrl;
            }

            @Override
            public void onIOException(IOException e)
            {
                if (e instanceof ProtocolException && e.getMessage().startsWith("Unexpected status line"))
                    ret[0] = new StopRequest(STATUS_UNHANDLED_HTTP_CODE, e);
                else if (e instanceof InterruptedIOException)
                    ret[0] = new StopRequest(STATUS_CANCELLED, "Download cancelled");
                else
                    /* Trouble with low-level sockets */
                    ret[0] = new StopRequest(STATUS_HTTP_DATA_ERROR, e);
            }

            @Override
            public void onTooManyRedirects()
            {
                ret[0] = new StopRequest(STATUS_TOO_MANY_REDIRECTS, "Too many redirects");
            }
        });
        connection.run();

        return ret[0];
    }

    private StopRequest parseOkHeaders(HttpURLConnection conn)
    {
        String mimeType = Intent.normalizeMimeType(conn.getContentType());
        if (mimeType != null && !mimeType.equals(info.mimeType))
            info.mimeType = mimeType;

        final String transferEncoding = conn.getHeaderField("Transfer-Encoding");
        if (transferEncoding == null) {
            try {
                info.totalBytes = Long.parseLong(conn.getHeaderField("Content-Length"));

            } catch (NumberFormatException e) {
                info.totalBytes = -1;
            }
        } else {
            info.totalBytes = -1;
        }
        info.partialSupport = "bytes".equalsIgnoreCase(conn.getHeaderField("Accept-Ranges"));

        Header eTagHeader = null;
        /* Find already added ETag */
        for (Header header : repo.getHeadersById(id)) {
            if ("ETag".equals(header.name)) {
                eTagHeader = header;
                break;
            }
        }
        if (eTagHeader == null)
            eTagHeader = new Header(id, "ETag", conn.getHeaderField("ETag"));
        else
            eTagHeader.value = conn.getHeaderField("ETag");

        repo.addHeader(eTagHeader);

        info.hasMetadata = true;
        info.statusCode = STATUS_RUNNING;
        writeToDatabaseWithPieces();

        StopRequest ret;
        if ((ret = checkPauseStop()) != null)
            return ret;

        return null;
    }

    private StopRequest allocFileSpace(Uri filePath)
    {
        ParcelFileDescriptor outPfd = null;
        FileDescriptor outFd = null;
        try {
            try {
                outPfd = context.getContentResolver().openFileDescriptor(filePath, "rw");
                outFd = outPfd.getFileDescriptor();

            } catch (IOException e) {
                return new StopRequest(STATUS_FILE_ERROR, e);
            }

            try {
                FileUtils.fallocate(context, outFd, info.totalBytes);

            } catch (InterruptedIOException e) {
                requestCancel();
            } catch (IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));
                return null;
            }

        } finally {
            try {
                if (outFd != null)
                    outFd.sync();

            } catch (IOException e) {
                /* Ignore */
            } finally {
                FileUtils.closeQuietly(outPfd);
            }
        }

        return null;
    }

    private void writeToDatabase()
    {
        repo.updateInfo(context, info, false, false);
    }

    private void writeToDatabaseWithPieces()
    {
        repo.updateInfo(context, info, false, true);
    }

    public StopRequest checkPauseStop()
    {
        if (pause)
            return new StopRequest(STATUS_PAUSED, "Download paused");
        else if (cancel || Thread.currentThread().isInterrupted())
            return new StopRequest(STATUS_CANCELLED, "Download cancelled");

        return null;
    }
}
