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

import static android.content.Context.NOTIFICATION_SERVICE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.util.ArrayMap;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.entity.DownloadPiece;
import com.tachibana.downloader.core.entity.InfoAndPieces;
import com.tachibana.downloader.core.utils.DateFormatUtils;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.receiver.NotificationReceiver;

import java.util.UUID;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/*
 * Update NotificationManager to reflect current download states.
 * Collapses similar downloads into a single notification.
 */

public class DownloadNotifier {

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
    private final ArrayMap<UUID, NotificationGroup> activeNotifs = new ArrayMap<>();

    private class NotificationGroup
    {
        public UUID downloadId;
        /* Tag + timestamp */
        public ArrayMap<String, Long> notifs = new ArrayMap<>();
        public long lastUpdateTime;

        public NotificationGroup(UUID downloadId, long lastUpdateTime)
        {
            this.downloadId = downloadId;
            this.lastUpdateTime = lastUpdateTime;
        }
    }

    public DownloadNotifier(Context context)
    {
        this.context = context;
        notifyManager = (NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);

        makeNotifyChans();
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

    public void update(InfoAndPieces infoAndPieces, boolean force)
    {
        if (infoAndPieces == null || infoAndPieces.info == null ||
            !(force || checkUpdateTime(infoAndPieces.info)))
            return;

        synchronized (activeNotifs) {
            int size = infoAndPieces.pieces.size();
            long downloadBytes = 0;
            long speed = 0;

            if (size > 0) {
                for (DownloadPiece piece : infoAndPieces.pieces) {
                    downloadBytes += piece.curBytes - infoAndPieces.info.pieceStartPos(piece);
                    speed += piece.speed;
                }
                /* Average speed */
                speed /= size;
            }

            updateWithLocked(infoAndPieces.info, downloadBytes, speed);
        }
    }

    public void update(DownloadInfo info, boolean force)
    {
        if (info == null || !(force || checkUpdateTime(info)))
            return;

        synchronized (activeNotifs) {
            updateWithLocked(info, 0, 0);
        }
    }

    private boolean checkUpdateTime(DownloadInfo info)
    {
        NotificationGroup notifyGroup = activeNotifs.get(info.id);
        /* Force first notification */
        if (notifyGroup == null)
            return true;

        long now = SystemClock.elapsedRealtime();
        long timeDelta = now - notifyGroup.lastUpdateTime;

        return timeDelta > MIN_PROGRESS_TIME;
    }

    private void updateWithLocked(DownloadInfo info, long downloadBytes, long speed)
    {
        NotificationGroup notifyGroup = activeNotifs.get(info.id);
        if (notifyGroup == null) {
            notifyGroup = new NotificationGroup(info.id, SystemClock.elapsedRealtime());
            activeNotifs.put(info.id, notifyGroup);
        }
        if (info.statusCode == StatusCode.STATUS_CANCELLED) {
            clearNotifs(notifyGroup, null);
            return;
        }

        String tag = makeNotificationTag(info);
        if (tag == null)
            return;
        int type = getNotificationTagType(tag);
        boolean isError = StatusCode.isStatusError(info.statusCode);

        /* Use time when notification was first shown to avoid shuffling */
        long firstShown;
        Long timestamp = notifyGroup.notifs.get(tag);
        if (timestamp == null) {
            firstShown = System.currentTimeMillis();
            notifyGroup.notifs.put(tag, firstShown);
        } else {
            firstShown = timestamp;
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
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setDataAndType(info.filePath, info.mimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                PendingIntent openPendingIntent =
                        PendingIntent.getActivity(
                                context,
                                0,
                                Intent.createChooser(intent, context.getString(R.string.open_using)),
                                PendingIntent.FLAG_UPDATE_CURRENT);

                builder.setContentIntent(openPendingIntent);
            }
        }

        /* Calculate and show progress */
        int progress = 0;
        long ETA = Utils.calcETA(info.totalBytes, downloadBytes, speed);
        if (type == TYPE_ACTIVE) {
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

        /* Build titles and description */
        switch (type) {
            case TYPE_ACTIVE:
                builder.setContentTitle(info.fileName);
                builder.setTicker(String.format(
                        context.getString(R.string.download_ticker_notify),
                        info.fileName));

                NotificationCompat.BigTextStyle progressBigText = new NotificationCompat.BigTextStyle();
                if (info.statusCode == StatusCode.STATUS_PAUSED) {
                    progressBigText.bigText(String.format(context.getString(R.string.download_queued_template),
                            Formatter.formatFileSize(context, downloadBytes),
                            Formatter.formatFileSize(context, info.totalBytes),
                            context.getString(R.string.pause)));
                } else {
                    progressBigText.bigText(String.format(context.getString(R.string.download_queued_progress_template),
                            Formatter.formatFileSize(context, downloadBytes),
                            Formatter.formatFileSize(context, info.totalBytes),
                            (ETA == -1 ? Utils.INFINITY_SYMBOL :
                                    DateFormatUtils.formatElapsedTime(context, ETA)),
                            Formatter.formatFileSize(context, speed)));
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
                        Formatter.formatFileSize(context, info.totalBytes),
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
                    builder.setCategory(Notification.CATEGORY_PROGRESS);
                    break;
                case TYPE_PENDING:
                    builder.setCategory(Notification.CATEGORY_STATUS);
                    break;
                case TYPE_COMPLETE:
                    if (isError)
                        builder.setCategory(Notification.CATEGORY_ERROR);
                    else
                        builder.setCategory(Notification.CATEGORY_STATUS);
                    break;
            }
        }

        notifyManager.notify(tag, 0, builder.build());

        clearNotifs(notifyGroup, tag);
    }

    /*
     * Remove stale tags that weren't renewed
     */

    private void clearNotifs(NotificationGroup notifyGroup, String renewedTag)
    {
        if (notifyGroup == null)
            return;
        ArrayMap<String, Long> notifs = notifyGroup.notifs;
        for (int j = 0; j < notifs.size(); j++) {
            if (renewedTag == null || !renewedTag.equals(notifs.keyAt(j))) {
                notifyManager.cancel(notifs.keyAt(j), 0);
                notifs.removeAt(j);
            }
        }
        if (notifs.isEmpty())
            activeNotifs.remove(notifyGroup.downloadId);
    }

    private static String makeNotificationTag(DownloadInfo info)
    {
        if (info.statusCode == StatusCode.STATUS_RUNNING ||
            info.statusCode == StatusCode.STATUS_PAUSED)
            return TYPE_ACTIVE + ":" + info.id;
        else if (info.statusCode == StatusCode.STATUS_PENDING ||
                info.statusCode == StatusCode.STATUS_WAITING_FOR_NETWORK ||
                info.statusCode == StatusCode.STATUS_WAITING_TO_RETRY)
            return TYPE_PENDING + ":" + info.id;
        else if (StatusCode.isStatusCompleted(info.statusCode))
            return TYPE_COMPLETE + ":" + info.id;
        else
            return null;
    }

    private static int getNotificationTagType(String tag)
    {
        return (tag == null ? -1 : Integer.parseInt(tag.substring(0, tag.indexOf(':'))));
    }
}
