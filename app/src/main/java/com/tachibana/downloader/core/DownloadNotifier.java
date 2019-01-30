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

package com.tachibana.downloader.core;

import static android.app.DownloadManager.Request.VISIBILITY_HIDDEN;
import static android.app.DownloadManager.Request.VISIBILITY_VISIBLE;
import static android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED;
import static android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION;
import static android.content.Context.NOTIFICATION_SERVICE;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.util.ArrayMap;
import android.util.Log;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.entity.DownloadPiece;
import com.tachibana.downloader.core.entity.InfoAndPieces;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.utils.DateFormatUtils;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.receiver.NotificationReceiver;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/*
 * Update NotificationManager to reflect current download states.
 * Collapses similar downloads into a single notification.
 */

public class DownloadNotifier
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadNotifier.class.getSimpleName();

    private static final int TYPE_ACTIVE = 1;
    private static final int TYPE_PENDING = 2;
    private static final int TYPE_COMPLETE = 3;
    /* The minimum amount of time that has to elapse before the progress bar gets updated, ms */
    private static final long MIN_PROGRESS_TIME = 2000;

    private static final String CHANNEL_ACTIVE = "active";
    private static final String CHANNEL_PENDING = "pending";
    private static final String CHANNEL_COMPLETE = "complete";

    private Context context;
    private NotificationManager notifyManager;
    /*
     * Currently active notifications, mapped from clustering tag to timestamp
     * when first shown
     */
    private final ArrayMap<UUID, Notification> activeNotifs = new ArrayMap<>();
    private DataRepository repo;
    private CompositeDisposable disposables = new CompositeDisposable();

    private class Notification
    {
        public UUID downloadId;
        public String tag;
        public long timestamp;
        public long lastUpdateTime;

        public Notification(UUID downloadId, long lastUpdateTime)
        {
            this.downloadId = downloadId;
            this.lastUpdateTime = lastUpdateTime;
        }
    }

    public DownloadNotifier(Context context, DataRepository repo)
    {
        this.context = context;
        notifyManager = (NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
        this.repo = repo;

        makeNotifyChans();
    }

    public void startUpdate()
    {
        disposables.add(repo.observeAllInfoAndPieces()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::update,
                        (Throwable t) -> Log.e(TAG, "Getting info and pieces error: "
                                + Log.getStackTraceString(t))
                ));
    }

    public void stopUpdate()
    {
        disposables.clear();
    }

    private void makeNotifyChans()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;

        notifyManager.createNotificationChannel(new NotificationChannel(CHANNEL_ACTIVE,
                context.getText(R.string.download_running),
                NotificationManager.IMPORTANCE_MIN));
        notifyManager.createNotificationChannel(new NotificationChannel(CHANNEL_PENDING,
                context.getText(R.string.pending),
                NotificationManager.IMPORTANCE_DEFAULT));
        notifyManager.createNotificationChannel(new NotificationChannel(CHANNEL_COMPLETE,
                context.getText(R.string.done_label),
                NotificationManager.IMPORTANCE_DEFAULT));
    }

    private void update(@NonNull List<InfoAndPieces> infoAndPiecesList)
    {
        synchronized (activeNotifs) {
            HashSet<UUID> ids = new HashSet<>();
            for (InfoAndPieces infoAndPieces : infoAndPiecesList) {
                ids.add(infoAndPieces.info.id);

                String tag = makeNotificationTag(infoAndPieces.info);
                if (tag == null)
                    return;
                int type = getNotificationTagType(tag);
                Notification notify = activeNotifs.get(infoAndPieces.info.id);

                boolean force;
                if (notify == null)
                    force = true;
                else {
                    int prevType = getNotificationTagType(notify.tag);
                    force = type != prevType;
                }
                if (!(force || checkUpdateTime(infoAndPieces.info)))
                    continue;

                updateWithLocked(infoAndPieces, notify, tag, type);
            }
            cleanNotifs(ids);
        }
    }

    private boolean checkUpdateTime(DownloadInfo info)
    {
        Notification notify = activeNotifs.get(info.id);
        /* Force first notification */
        if (notify == null)
            return true;

        long now = SystemClock.elapsedRealtime();
        long timeDelta = now - notify.lastUpdateTime;

        return timeDelta > MIN_PROGRESS_TIME;
    }

    private void updateWithLocked(InfoAndPieces infoAndPieces, Notification notify, String tag, int type)
    {
        DownloadInfo info = infoAndPieces.info;
        if (info.statusCode == StatusCode.STATUS_CANCELLED) {
            notifyManager.cancel(tag, 0);
            return;
        }

        String prevTag = null;
        if (notify == null) {
            notify = new Notification(info.id, SystemClock.elapsedRealtime());
            activeNotifs.put(info.id, notify);

        } else {
            /* Save previous tag for deleting */
            prevTag = notify.tag;
        }
        boolean isError = StatusCode.isStatusError(info.statusCode);

        /* Use time when notification was first shown to avoid shuffling */
        long firstShown;
        if (notify.timestamp == 0) {
            firstShown = System.currentTimeMillis();
            notify.tag = tag;
            notify.timestamp = firstShown;
            activeNotifs.put(info.id, notify);
        } else {
            firstShown = notify.timestamp;
        }

        NotificationCompat.Builder builder;
        switch (type) {
            case TYPE_ACTIVE:
                builder = new NotificationCompat.Builder(context, CHANNEL_ACTIVE);
                break;
            case TYPE_PENDING:
                builder = new NotificationCompat.Builder(context, CHANNEL_PENDING);
                break;
            case TYPE_COMPLETE:
                builder = new NotificationCompat.Builder(context, CHANNEL_COMPLETE);
                break;
            default:
                return;
        }
        builder.setColor(ContextCompat.getColor(context, R.color.primary));
        builder.setWhen(firstShown);
        builder.setOnlyAlertOnce(true);

        switch (type) {
            case TYPE_ACTIVE:
                if (info.statusCode == StatusCode.STATUS_PAUSED)
                    builder.setSmallIcon(R.drawable.ic_pause_white_24dp);
                else
                    builder.setSmallIcon(android.R.drawable.stat_sys_download);
                break;
            case TYPE_PENDING:
                builder.setSmallIcon(android.R.drawable.stat_sys_warning);
                break;
            case TYPE_COMPLETE:
                if (isError)
                    builder.setSmallIcon(R.drawable.ic_error_white_24dp);
                else
                    builder.setSmallIcon(android.R.drawable.stat_sys_download_done);
                break;
        }

        /* Build action intents */
        if (type == TYPE_ACTIVE || type == TYPE_PENDING) {
            if (type == TYPE_ACTIVE)
                builder.setOngoing(true);

            Intent pauseResumeButtonIntent = new Intent(context, NotificationReceiver.class);
            pauseResumeButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME);
            pauseResumeButtonIntent.putExtra(NotificationReceiver.TAG_ID, info.id);
            boolean isPause = info.statusCode == StatusCode.STATUS_PAUSED;
            int icon = (isPause ? R.drawable.ic_play_arrow_white_24dp : R.drawable.ic_pause_white_24dp);
            String text = (isPause ? context.getString(R.string.resume) : context.getString(R.string.pause));
            PendingIntent pauseResumeButtonPendingIntent =
                    PendingIntent.getBroadcast(
                            context,
                            0,
                            pauseResumeButtonIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);

            builder.addAction(new NotificationCompat.Action.Builder(
                    icon, text, pauseResumeButtonPendingIntent).build());

            Intent stopButtonIntent = new Intent(context, NotificationReceiver.class);
            stopButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_CANCEL);
            stopButtonIntent.putExtra(NotificationReceiver.TAG_ID, info.id);
            PendingIntent stopButtonPendingIntent =
                    PendingIntent.getBroadcast(
                            context,
                            0,
                            stopButtonIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);

            builder.addAction(new NotificationCompat.Action.Builder(
                    R.drawable.ic_stop_white_24dp,
                    context.getString(R.string.stop),
                    stopButtonPendingIntent)
                    .build());

        } else if (type == TYPE_COMPLETE) {
            builder.setAutoCancel(true);
            if (!isError) {
                PendingIntent openPendingIntent =
                        PendingIntent.getActivity(
                                context,
                                0,
                                Intent.createChooser(Utils.createOpenFileIntent(context, info),
                                        context.getString(R.string.open_using)),
                                PendingIntent.FLAG_UPDATE_CURRENT);

                builder.setContentIntent(openPendingIntent);
            }
        }

        /* Calculate and show progress */

        int size = infoAndPieces.pieces.size();
        long downloadBytes = 0;
        long speed = 0;

        if (size > 0) {
            for (DownloadPiece piece : infoAndPieces.pieces) {
                downloadBytes += piece.curBytes - info.pieceStartPos(piece);
                speed += piece.speed;
            }
            /* Average speed */
            speed /= size;
        }

        int progress = 0;
        long ETA = Utils.calcETA(info.totalBytes, downloadBytes, speed);
        if (type == TYPE_ACTIVE) {
            if (info.statusCode == StatusCode.STATUS_FETCH_METADATA) {
                builder.setProgress(100, 0, true);
            } else {
                if (info.totalBytes > 0) {
                    progress = (int)((downloadBytes * 100) / info.totalBytes);
                    if (info.statusCode == StatusCode.STATUS_PAUSED)
                        builder.setProgress(0, 0, false);
                    else
                        builder.setProgress(100, progress, false);
                } else {
                    builder.setProgress(100, 0, true);
                }
            }
        }

        /* Build titles and description */
        switch (type) {
            case TYPE_ACTIVE:
                builder.setContentTitle(info.fileName);
                builder.setTicker(String.format(
                        context.getString(R.string.download_ticker_notify),
                        info.fileName));

                NotificationCompat.BigTextStyle progressBigText = new NotificationCompat.BigTextStyle();
                if (info.statusCode == StatusCode.STATUS_RUNNING) {
                    progressBigText.bigText(String.format(context.getString(R.string.download_queued_progress_template),
                            Formatter.formatFileSize(context, downloadBytes),
                            (info.totalBytes == -1 ? context.getString(R.string.not_available) :
                                    Formatter.formatFileSize(context, info.totalBytes)),
                            (ETA == -1 ? Utils.INFINITY_SYMBOL :
                                    DateFormatUtils.formatElapsedTime(context, ETA)),
                            Formatter.formatFileSize(context, speed)));
                } else {
                    String statusStr = "";
                    switch (info.statusCode) {
                        case StatusCode.STATUS_PAUSED:
                            statusStr = context.getString(R.string.pause);
                            break;
                        case StatusCode.STATUS_FETCH_METADATA:
                            statusStr = context.getString(R.string.fetching_metadata);
                            break;
                    }
                    progressBigText.bigText(String.format(context.getString(R.string.download_queued_template),
                            Formatter.formatFileSize(context, downloadBytes),
                            (info.totalBytes == -1 ? context.getString(R.string.not_available) :
                                    Formatter.formatFileSize(context, info.totalBytes)),
                            statusStr));
                }
                builder.setStyle(progressBigText);
                break;
            case TYPE_PENDING:
                builder.setContentTitle(info.fileName);
                builder.setTicker(String.format(
                        context.getString(R.string.download_in_queue_ticker_notify),
                        info.fileName));
                NotificationCompat.BigTextStyle pendingBigText = new NotificationCompat.BigTextStyle();
                pendingBigText.bigText(String.format(context.getString(R.string.download_queued_template),
                        Formatter.formatFileSize(context, downloadBytes),
                        (info.totalBytes == -1 ? context.getString(R.string.not_available) :
                                Formatter.formatFileSize(context, info.totalBytes)),
                        context.getString(R.string.pending)));
                builder.setStyle(pendingBigText);
                break;
            case TYPE_COMPLETE:
                if (isError) {
                    builder.setContentTitle(info.fileName);
                    builder.setTicker(context.getString(R.string.download_error_notify_title));
                    builder.setContentText(String.format(context.getString(R.string.error_template), info.statusMsg));
                } else {
                    builder.setContentTitle(context.getString(R.string.download_finished_notify));
                    builder.setTicker(context.getString(R.string.download_finished_notify));
                    builder.setContentText(info.fileName);
                }
                break;
        }

        /* Set category */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            switch (type) {
                case TYPE_ACTIVE:
                    builder.setCategory(android.app.Notification.CATEGORY_PROGRESS);
                    break;
                case TYPE_PENDING:
                    builder.setCategory(android.app.Notification.CATEGORY_STATUS);
                    break;
                case TYPE_COMPLETE:
                    if (isError)
                        builder.setCategory(android.app.Notification.CATEGORY_ERROR);
                    else
                        builder.setCategory(android.app.Notification.CATEGORY_STATUS);
                    break;
            }
        }

        if (prevTag != null && !prevTag.equals(notify.tag))
            notifyManager.cancel(prevTag, 0);
        notifyManager.notify(notify.tag, 0, builder.build());

        if (type == TYPE_COMPLETE && info.visibility != VISIBILITY_HIDDEN)
            markAsHidden(info);
    }

    /*
     * Disable notifications for download
     */

    private void markAsHidden(DownloadInfo info)
    {
        info.visibility = VISIBILITY_HIDDEN;

        disposables.add(Completable.fromAction(() -> repo.updateInfo(context, info, false, false))
                .subscribeOn(Schedulers.io())
                .subscribe());
    }

    private void cleanNotifs(@NonNull Set<UUID> excludedIds)
    {
        for (int i = 0; i < activeNotifs.size(); i++) {
            UUID id = activeNotifs.keyAt(i);
            if (excludedIds.contains(id))
                continue;
            Notification notify = activeNotifs.remove(id);
            if (notify == null)
                continue;
            notifyManager.cancel(notify.tag, 0);
        }
    }

    private static String makeNotificationTag(DownloadInfo info)
    {
        if (isActiveAndVisible(info.statusCode, info.visibility))
            return TYPE_ACTIVE + ":" + info.id;
        else if (isPendingAndVisible(info.statusCode, info.visibility))
            return TYPE_PENDING + ":" + info.id;
        else if (isCompleteAndVisible(info.statusCode, info.visibility))
            return TYPE_COMPLETE + ":" + info.id;
        else
            return null;
    }

    private static int getNotificationTagType(String tag)
    {
        return (tag == null ? -1 : Integer.parseInt(tag.substring(0, tag.indexOf(':'))));
    }

    private static boolean isPendingAndVisible(int statusCode, int visibility)
    {
        return (statusCode == StatusCode.STATUS_PENDING ||
                statusCode == StatusCode.STATUS_WAITING_FOR_NETWORK ||
                statusCode == StatusCode.STATUS_WAITING_TO_RETRY) &&
                (visibility == VISIBILITY_VISIBLE ||
                 visibility == VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
    }

    private static boolean isActiveAndVisible(int statusCode, int visibility)
    {
        return (statusCode == StatusCode.STATUS_RUNNING ||
                statusCode == StatusCode.STATUS_PAUSED ||
                statusCode == StatusCode.STATUS_FETCH_METADATA) &&
                (visibility == VISIBILITY_VISIBLE ||
                 visibility == VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
    }

    private static boolean isCompleteAndVisible(int statusCode, int visibility)
    {
        return StatusCode.isStatusCompleted(statusCode) &&
                (visibility == VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        || visibility == VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION);
    }
}
