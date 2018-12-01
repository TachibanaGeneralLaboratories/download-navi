/*
 * Copyright (C) 2018 Tachibana General Laboratories, LLC
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import com.tachibana.downloader.core.storage.DownloadStorage;
import com.tachibana.downloader.core.utils.FileUtils;
import com.tachibana.downloader.core.utils.Utils;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.tachibana.downloader.core.DownloadInfo.STATUS_CANCELLED;
import static com.tachibana.downloader.core.DownloadInfo.STATUS_FILE_ERROR;
import static com.tachibana.downloader.core.DownloadInfo.STATUS_INSUFFICIENT_SPACE_ERROR;
import static com.tachibana.downloader.core.DownloadInfo.STATUS_PAUSED;
import static com.tachibana.downloader.core.DownloadInfo.STATUS_RUNNING;
import static com.tachibana.downloader.core.DownloadInfo.STATUS_SUCCESS;
import static com.tachibana.downloader.core.DownloadInfo.STATUS_UNKNOWN_ERROR;
import static com.tachibana.downloader.core.DownloadInfo.STATUS_WAITING_FOR_NETWORK;

/*
 * Represent one task of downloading.
 */

public class DownloadThread extends Thread
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadThread.class.getSimpleName();

    private static final String PROGRESS_THREAD_NAME = "progress";
    private static final int SYNC_TIME = 1000; /* ms */

    private DownloadInfo info;
    private UUID id;
    private Handler handler;
    private boolean cancel;
    /* Stop and delete */
    private boolean pause;
    private boolean running;
    private ExecutorService exec;
    private DownloadStorage storage;
    private Context context;
    private HandlerThread progressThread;
    private Handler progressHandler;
    private long downloadBytes;
    /* Time when current sample started */
    private long speedSampleStart;
    /* Historical bytes/second speed of this download */
    private long speed;
    /* Bytes transferred since current sample started */
    private long speedSampleBytes;

    public DownloadThread(UUID id, Context context, Handler handler)
    {
        this.id = id;
        this.handler = handler;
        this.storage = new DownloadStorage(context);
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

    public long getDownloadBytes()
    {
        return downloadBytes;
    }

    @Override
    public void run()
    {
        running = true;
        StopRequest ret = null;
        try {
            info = storage.getInfoById(id);
            if (info == null) {
                Log.w(TAG, "Info " + id + " is null, skipping");
                if (handler != null)
                    handler.sendMessage(DownloadMsg.makeCancelledMsg(id));
                return;
            }

            if (info.getStatusCode() == STATUS_SUCCESS) {
                Log.w(TAG, id + " already finished, skipping");
                if (handler != null)
                    handler.sendMessage(DownloadMsg.makeFinishedMsg(id));
                return;
            }

            info.setStatusCode(STATUS_RUNNING);
            info.setStatusMsg(null);
            writeToDatabase();

            if ((ret = execDownload()) != null) {
                info.setStatusCode(ret.getFinalStatus());
                info.setStatusMsg(ret.getMessage());
            } else {
                info.setStatusCode(STATUS_SUCCESS);
            }

            checkPiecesStatus();

        } catch (Throwable t) {
            Log.e(TAG, Log.getStackTraceString(t));
            info.setStatusCode(STATUS_UNKNOWN_ERROR);
            info.setStatusMsg(t.getMessage());

        } finally {
            finalizeThread();
            if (handler != null && info != null) {
                switch (info.getStatusCode()) {
                    case STATUS_PAUSED:
                        handler.sendMessage(DownloadMsg.makePausedMsg(id));
                        break;
                    case STATUS_CANCELLED:
                        handler.sendMessage(DownloadMsg.makeCancelledMsg(id));
                        break;
                    default:
                        handler.sendMessage(DownloadMsg.makeFinishedMsg(id));
                }
            }
        }
    }

    private void finalizeThread()
    {
        if (info != null) {
            writeToDatabase();

            if (info.isStatusError()) {
                /* When error, free up any disk space */
                ParcelFileDescriptor pfd = null;
                FileOutputStream fout = null;
                try {
                    pfd = context.getContentResolver()
                            .openFileDescriptor(info.getFilePath(), "rw");
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
        List<DownloadPiece> pieces = storage.getPiecesById(id);
        if (pieces == null || pieces.isEmpty()) {
            String errMsg = "Download deleted or missing";
            info.setStatusCode(STATUS_CANCELLED);
            info.setStatusMsg(errMsg);
            Log.i(TAG, "id=" + id + ", " + errMsg);

        } else if (pieces.size() != info.getNumPieces()) {
            String errMsg = "Some pieces are missing";
            info.setStatusCode(STATUS_UNKNOWN_ERROR);
            info.setStatusMsg(errMsg);
            Log.i(TAG, "id=" + id + ", " + errMsg);

        } else {
            /* If we just finished a chunked file, record total size */
            if (info.getTotalBytes() == -1 && pieces.size() == 1)
                info.setTotalBytes(pieces.get(0).getCurBytes());

            /* Check pieces status if we are not cancelled or paused */
            StopRequest ret;
            if ((ret = checkPauseStop()) != null) {
                info.setStatusCode(ret.getFinalStatus());
            } else {
                for (DownloadPiece piece : pieces) {
                    int status = piece.getStatusCode();
                    /* TODO: maybe change handle status behaviour */
                    if (status != STATUS_SUCCESS || status > info.getStatusCode()) {
                        info.setStatusCode(status);
                        info.setStatusMsg(piece.getStatusMsg());
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

            /* Pre-flight disk space requirements, when known */
            if (info.getTotalBytes() > 0) {
                if ((ret = allocFileSpace()) != null)
                    return ret;
            }

            exec = (info.getNumPieces() == 1 ?
                    Executors.newSingleThreadExecutor() :
                    Executors.newFixedThreadPool(info.getNumPieces()));

            startUpdateProgress();
            for (int i = 0; i < info.getNumPieces(); i++)
                exec.submit(new PieceThread(id, i, context));
            exec.shutdown();
            /* Wait "forever" */
            if (!exec.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS))
                requestCancel();

        } catch (InterruptedException e) {
            requestCancel();
        } finally {
            stopUpdateProgress();
        }

        return null;
    }

    private Runnable updateProgress = new Runnable()
    {
        @Override
        public void run()
        {
            List<DownloadPiece> pieces = storage.getPiecesById(id);
            if (pieces != null) {
                long downloadBytes = 0;
                for (DownloadPiece piece : pieces)
                    downloadBytes += piece.getCurBytes() - info.pieceStartPos(piece);

                long now = SystemClock.elapsedRealtime();
                long sampleDelta = now - speedSampleStart;

                if (sampleDelta > 500) {
                    long sampleSpeed = ((downloadBytes - speedSampleBytes) * 1000) / sampleDelta;
                    if (speed == 0)
                        speed = sampleSpeed;
                    else
                        speed = (speed * 3 + sampleSpeed) / 4;

                    speedSampleStart = now;
                    speedSampleBytes = downloadBytes;
                }

                DownloadThread.this.downloadBytes = downloadBytes;
                handler.sendMessage(DownloadMsg.makeProgressChangedMsg(id, downloadBytes, speed));
            }

            progressHandler.postDelayed(this, SYNC_TIME);
        }
    };

    private void startUpdateProgress()
    {
        if (progressHandler != null || progressThread != null)
            return;

        speed = 0;
        speedSampleStart = 0;
        speedSampleBytes = 0;

        progressThread = new HandlerThread(PROGRESS_THREAD_NAME);
        progressThread.start();
        progressHandler = new Handler(progressThread.getLooper());
        progressHandler.postDelayed(updateProgress, SYNC_TIME);
    }

    private void stopUpdateProgress()
    {
        if (progressHandler == null || progressThread == null)
            return;

        progressHandler.removeCallbacks(updateProgress);
        progressThread.quitSafely();

        progressHandler = null;
        progressThread = null;
        speed = 0;
        speedSampleStart = 0;
        speedSampleBytes = 0;
    }

    private StopRequest allocFileSpace()
    {
        ParcelFileDescriptor outPfd = null;
        FileDescriptor outFd = null;
        try {
            try {
                outPfd = context.getContentResolver().openFileDescriptor(info.getFilePath(), "rw");
                outFd = outPfd.getFileDescriptor();

            } catch (IOException e) {
                return new StopRequest(STATUS_FILE_ERROR, e);
            }

            try {
                FileUtils.fallocate(context, outFd, info.getTotalBytes());

            } catch (InterruptedIOException e) {
                requestCancel();
            } catch (IOException e) {
                return new StopRequest(STATUS_INSUFFICIENT_SPACE_ERROR, e);
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
        storage.updateInfo(info, false, false);
    }

    public StopRequest checkPauseStop()
    {
        if (pause)
            return new StopRequest(STATUS_PAUSED, "Download paused");
        else if (cancel)
            return new StopRequest(STATUS_CANCELLED, "Download cancelled");

        return null;
    }
}
