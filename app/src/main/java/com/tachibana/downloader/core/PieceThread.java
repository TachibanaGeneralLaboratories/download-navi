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
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.tachibana.downloader.MainApplication;
import com.tachibana.downloader.R;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.entity.DownloadPiece;
import com.tachibana.downloader.core.entity.Header;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.utils.FileUtils;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.settings.SettingsManager;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.UUID;

import static com.tachibana.downloader.core.StatusCode.STATUS_BAD_REQUEST;
import static com.tachibana.downloader.core.StatusCode.STATUS_CANNOT_RESUME;
import static com.tachibana.downloader.core.StatusCode.STATUS_FILE_ERROR;
import static com.tachibana.downloader.core.StatusCode.STATUS_HTTP_DATA_ERROR;
import static com.tachibana.downloader.core.StatusCode.STATUS_RUNNING;
import static com.tachibana.downloader.core.StatusCode.STATUS_STOPPED;
import static com.tachibana.downloader.core.StatusCode.STATUS_SUCCESS;
import static com.tachibana.downloader.core.StatusCode.STATUS_TOO_MANY_REDIRECTS;
import static com.tachibana.downloader.core.StatusCode.STATUS_UNHANDLED_HTTP_CODE;
import static com.tachibana.downloader.core.StatusCode.STATUS_UNKNOWN_ERROR;
import static com.tachibana.downloader.core.StatusCode.STATUS_WAITING_FOR_NETWORK;
import static com.tachibana.downloader.core.StatusCode.STATUS_WAITING_TO_RETRY;
import static java.net.HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PARTIAL;
import static java.net.HttpURLConnection.HTTP_PRECON_FAILED;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

/*
 * Represent one task of piece downloading.
 */

public class PieceThread extends Thread
{
    @SuppressWarnings("unused")
    private static final String TAG = PieceThread.class.getSimpleName();

    private static final int BUFFER_SIZE = 8192;
    /* The minimum amount of progress that has to be done before the progress bar gets updated */
    private static final int MIN_PROGRESS_STEP = 65536;
    /* The minimum amount of time that has to elapse before the progress bar gets updated, ms */
    private static final long MIN_PROGRESS_TIME = 2000;

    private DownloadPiece piece;
    private UUID infoId;
    private int pieceIndex;
    private long startPos, endPos;
    /* Details from the last time we pushed a database update */
    private long lastUpdateBytes = 0;
    private long lastUpdateTime = 0;
    /* Time when current sample started */
    private long speedSampleStart;
    /* Bytes transferred since current sample started */
    private long speedSampleBytes;
    /*
     * Flag indicating if we've made forward progress transferring file data
     * from a remote server.
     */
    private boolean madeProgress = false;
    private int networkType;
    private DataRepository repo;
    private SharedPreferences pref;
    private Context appContext;

    private ParcelFileDescriptor outPfd;
    private FileDescriptor outFd;
    private FileOutputStream fout;
    private InputStream in;

    public PieceThread(Context appContext, UUID infoId, int pieceIndex)
    {
        this.infoId = infoId;
        this.pieceIndex = pieceIndex;
        this.appContext = appContext;
        repo = ((MainApplication)appContext).getRepository();
        pref = SettingsManager.getInstance(appContext).getPreferences();
    }

    @Override
    public void run()
    {
        StopRequest ret;
        try {
            piece = repo.getPiece(pieceIndex, infoId);
            if (piece == null) {
                Log.w(TAG, "Piece " + pieceIndex + " is null, skipping");
                return;
            }

            if (piece.statusCode == STATUS_SUCCESS) {
                Log.w(TAG, pieceIndex + " already finished, skipping");
                return;
            }

            do {
                piece.statusCode = STATUS_RUNNING;
                piece.statusMsg = null;
                writeToDatabase();
                /*
                 * Remember which network this download started on;
                 * used to determine if errors were due to network changes
                 */
                SystemFacade systemFacade = Utils.getSystemFacade(appContext);
                NetworkInfo netInfo = systemFacade.getActiveNetworkInfo();
                if (netInfo != null)
                    networkType = netInfo.getType();

                if ((ret = execDownload()) != null)
                    handleRequest(ret);
                else
                    piece.statusCode = STATUS_SUCCESS;

            } while (piece != null && piece.statusCode == STATUS_WAITING_TO_RETRY);

        } catch (Throwable t) {
            Log.e(TAG, Log.getStackTraceString(t));
            piece.statusCode = STATUS_UNKNOWN_ERROR;
            piece.statusMsg = t.getMessage();

        } finally {
            finalizeThread();
        }
    }

    private void handleRequest(StopRequest request)
    {
        if (request.getException() != null)
            Log.e(TAG, "piece=" + pieceIndex + ", " + request + "\n" +
                    Log.getStackTraceString(request.getException()));
        else
            Log.i(TAG, "piece=" + pieceIndex + ", " + request);

        piece.statusCode = request.getFinalStatus();
        piece.statusMsg = request.getMessage();
        /*
         * Nobody below our level should request retries, since we handle
         * failure counts at this level
         */
        if (piece.statusCode == STATUS_WAITING_TO_RETRY)
            throw new IllegalStateException("Execution should always throw final error codes");

        /* Some errors should be retryable, unless we fail too many times */
        if (isStatusRetryable(piece.statusCode)) {
            ++piece.numFailed;

            int maxRetries = pref.getInt(appContext.getString(R.string.pref_key_max_download_retries),
                                         SettingsManager.Default.maxDownloadRetries);
            if (piece.numFailed < maxRetries) {
                SystemFacade systemFacade = Utils.getSystemFacade(appContext);
                NetworkInfo netInfo = systemFacade.getActiveNetworkInfo();
                if (netInfo != null && netInfo.getType() == networkType && netInfo.isConnected())
                    /* Underlying network is still intact, use normal backoff */
                    piece.statusCode = STATUS_WAITING_TO_RETRY;
                else
                    /* Network changed, retry on any next available */
                    piece.statusCode = STATUS_WAITING_FOR_NETWORK;

                if (getETag(repo.getHeadersById(infoId)) == null && madeProgress) {
                    /*
                     * However, if we wrote data and have no ETag to verify
                     * contents against later, we can't actually resume
                     */
                    piece.statusCode = STATUS_CANNOT_RESUME;
                }
            }
        }
    }

    private void finalizeThread()
    {
        if (piece != null)
            writeToDatabase();
    }

    private StopRequest execDownload()
    {
        if (piece.size == 0)
            return new StopRequest(STATUS_SUCCESS, "Length is zero; skipping");

        DownloadInfo info = repo.getInfoById(infoId);
        if (info == null)
            return new StopRequest(STATUS_STOPPED, "Download deleted or missing");

        startPos = info.pieceStartPos(piece);
        endPos = info.pieceEndPos(piece);

        /* Reset and download from the beginning */
        if (!info.partialSupport) {
            piece.curBytes = startPos;
            writeToDatabase();
        }

        HttpConnection connection;
        try {
            connection = new HttpConnection(info.url);

        } catch (MalformedURLException e) {
            return new StopRequest(STATUS_BAD_REQUEST, "bad url " + info.url, e);
        } catch (GeneralSecurityException e) {
            return new StopRequest(STATUS_UNKNOWN_ERROR, "Unable to create SSLContext");
        }

        if (!Utils.checkConnectivity(appContext))
            return new StopRequest(STATUS_WAITING_FOR_NETWORK);

        final StopRequest[] ret = new StopRequest[1];
        boolean resuming = piece.curBytes != startPos;

        connection.setListener(new HttpConnection.Listener() {
            @Override
            public void onConnectionCreated(HttpURLConnection conn)
            {
                ret[0] = addRequestHeaders(conn, resuming);
            }

            @Override
            public void onResponseHandle(HttpURLConnection conn, int code, String message)
            {
                switch (code) {
                    case HTTP_OK:
                        if (startPos != 0 || resuming) {
                            ret[0] = new StopRequest(STATUS_CANNOT_RESUME,
                                    "Expected partial, but received OK");
                            return;
                        }
                        ret[0] = transferData(conn);
                        break;
                    case HTTP_PARTIAL:
                        ret[0] = transferData(conn);
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
                /* Ignore */
            }

            @Override
            public void onIOException(IOException e)
            {
                if (e instanceof ProtocolException && e.getMessage().startsWith("Unexpected status line"))
                    ret[0] = new StopRequest(STATUS_UNHANDLED_HTTP_CODE, e);
                else if (e instanceof SocketTimeoutException)
                    ret[0] = new StopRequest(HTTP_GATEWAY_TIMEOUT, "Download timeout");
                else if (e instanceof InterruptedIOException)
                    ret[0] = new StopRequest(STATUS_STOPPED, "Download cancelled");
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

    /*
     * Add custom headers for this download to the HTTP request.
     */

    private StopRequest addRequestHeaders(HttpURLConnection conn, boolean resuming)
    {
        DownloadInfo info = repo.getInfoById(infoId);
        if (info == null)
            return new StopRequest(STATUS_STOPPED, "Download deleted or missing");

        String etag = null;
        for (Header header : repo.getHeadersById(infoId)) {
            if ("ETag".equals(header.name)) {
                etag = header.value;
                continue;
            }
            conn.addRequestProperty(header.name, header.value);
        }
        if (conn.getRequestProperty("User-Agent") == null && !TextUtils.isEmpty(info.userAgent))
            conn.addRequestProperty("User-Agent", info.userAgent);
        /*
         * Defeat transparent gzip compression, since it doesn't allow us to
         * easily resume partial downloads.
         */
        conn.setRequestProperty("Accept-Encoding", "identity");
        /*
         * Defeat connection reuse, since otherwise servers may continue
         * streaming large downloads after cancelled.
         */
        conn.setRequestProperty("Connection", "close");
        if (resuming && etag != null)
            conn.addRequestProperty("If-Match", etag);
        String rangeRequest = "bytes=" + piece.curBytes + "-";
        if (endPos >= 0)
            rangeRequest += endPos;
        conn.addRequestProperty("Range", rangeRequest);

        return null;
    }

    /*
     * Transfer data from the given connection to the destination file.
     */

    private StopRequest transferData(HttpURLConnection conn)
    {
        DownloadInfo info = repo.getInfoById(infoId);
        if (info == null)
            return new StopRequest(STATUS_STOPPED, "Download deleted or missing");
        StopRequest ret;
        if ((ret = checkCancel()) != null)
            return ret;

        /*
         * To detect when we're really finished, we either need a length, closed
         * connection, or chunked encoding.
         */
        boolean hasLength = piece.size != -1;
        boolean isConnectionClose = "close".equalsIgnoreCase(conn.getHeaderField("Connection"));
        boolean isEncodingChunked = "chunked".equalsIgnoreCase(conn.getHeaderField("Transfer-Encoding"));

        if (!(hasLength || isConnectionClose || isEncodingChunked))
            return new StopRequest(STATUS_CANNOT_RESUME,
                    "Can't know size of download, giving up");

        try {
            try {
                in = conn.getInputStream();

            } catch (SocketTimeoutException e) {
                return new StopRequest(HTTP_GATEWAY_TIMEOUT, "Download timeout");
            } catch(InterruptedIOException e) {
                return new StopRequest(STATUS_STOPPED, "Download cancelled");
            } catch (IOException e) {
                return new StopRequest(STATUS_HTTP_DATA_ERROR, e);
            }

            try {
                Uri filePath = FileUtils.getFileUri(appContext, info.dirPath, info.fileName);
                if (filePath == null)
                    throw new IOException("Write error: file not found");
                outPfd = appContext.getContentResolver().openFileDescriptor(filePath, "rw");
                outFd = outPfd.getFileDescriptor();
                fout = new FileOutputStream(outFd);

                /* Move into place to begin writing */
                FileUtils.lseek(fout, piece.curBytes);

            } catch (InterruptedIOException e) {
                return new StopRequest(STATUS_STOPPED, "Download cancelled");
            } catch (IOException e) {
                return new StopRequest(STATUS_FILE_ERROR, e);
            }

            /*
             * Start streaming data, periodically watch for pause/cancel
             * commands and checking disk space as needed.
             */
            return transferData(in, fout, outFd);

        } finally {
            FileUtils.closeQuietly(in);
            try {
                if (fout != null)
                    fout.flush();
                if (outFd != null)
                    outFd.sync();

            } catch (IOException e) {
                /* Ignore */
            } finally {
                FileUtils.closeQuietly(fout);
                fout = null;
                outFd = null;
                outPfd = null;
                in = null;
            }
        }
    }

    /*
     * Transfer as much data as possible from the
     * net response to the destination file
     */

    private StopRequest transferData(InputStream in, FileOutputStream fout, FileDescriptor outFd)
    {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (true) {
            StopRequest ret;
            if ((ret = checkCancel()) != null)
                return ret;

            int len = -1;
            try {
                len = in.read(buffer);

            } catch (InterruptedIOException e) {
                return new StopRequest(STATUS_STOPPED, "Download cancelled");
            } catch (IOException e) {
                return new StopRequest(STATUS_HTTP_DATA_ERROR,
                        "Failed reading response: " + e, e);
            }
            if (len == -1)
                break;

            try {
                fout.write(buffer, 0, len);

                madeProgress = true;
                piece.curBytes += len;
                if ((ret = updateProgress(outFd)) != null)
                    return ret;

            } catch (InterruptedIOException e) {
                return new StopRequest(STATUS_STOPPED, "Download cancelled");
            } catch (IOException e) {
                return new StopRequest(STATUS_FILE_ERROR, e);
            }

            if (piece.size != -1 && piece.curBytes >= endPos + 1)
                break;
        }

        /* Finished without error; verify length if known */
        if (piece.size != -1 && piece.curBytes != endPos + 1) {
            return new StopRequest(STATUS_HTTP_DATA_ERROR,
                    "Piece length mismatch; found "
                    + piece.curBytes + " instead of " + (endPos + 1));
        }

        return null;
    }

    private StopRequest updateProgress(FileDescriptor outFd) throws IOException
    {
        long now = SystemClock.elapsedRealtime();
        long currentBytes = piece.curBytes;

        final long sampleDelta = now - speedSampleStart;
        if (sampleDelta > 500) {
            long sampleSpeed = ((currentBytes - speedSampleBytes) * 1000) / sampleDelta;
            if (piece.speed == 0)
                piece.speed = sampleSpeed;
            else
                piece.speed = ((piece.speed * 3) + sampleSpeed) / 4;

            speedSampleStart = now;
            speedSampleBytes = currentBytes;
        }

        long bytesDelta = currentBytes - lastUpdateBytes;
        long timeDelta = now - lastUpdateTime;
        if (bytesDelta > MIN_PROGRESS_STEP && timeDelta > MIN_PROGRESS_TIME) {
            /*
             * sync() to ensure that current progress has been flushed to disk,
             * so we can always resume based on latest database information
             */
            outFd.sync();

            StopRequest ret;
            if ((ret = writeToDatabaseOrCancel()) != null)
                return ret;

            lastUpdateBytes = currentBytes;
            lastUpdateTime = now;
        }

        return null;
    }

    private StopRequest writeToDatabaseOrCancel()
    {
        return repo.updatePiece(piece) > 0 ?
                null :
                new StopRequest(STATUS_STOPPED, "Download deleted or missing");
    }

    private void writeToDatabase()
    {
        repo.updatePiece(piece);
    }

    private Header getETag(List<Header> headers)
    {
        for (Header header : headers) {
            if ("ETag".equals(header.name))
                return header;
        }

        return null;
    }

    private boolean isStatusRetryable(int statusCode)
    {
        switch (statusCode) {
            case STATUS_HTTP_DATA_ERROR:
            case HTTP_UNAVAILABLE:
            case HTTP_INTERNAL_ERROR:
            case STATUS_FILE_ERROR:
                return true;
            default:
                return false;
        }
    }

    private StopRequest checkCancel()
    {
        return (Thread.currentThread().isInterrupted() ?
                new StopRequest(STATUS_STOPPED, "Download cancelled") :
                null);
    }
}
