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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.model.data.StatusCode;
import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.model.data.entity.DownloadPiece;
import com.tachibana.downloader.core.model.data.entity.InfoAndPieces;
import com.tachibana.downloader.core.settings.SettingsRepository;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.system.FileSystemFacade;
import com.tachibana.downloader.core.system.SystemFacadeHelper;
import com.tachibana.downloader.core.utils.DateUtils;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.receiver.NotificationReceiver;
import com.tachibana.downloader.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import static android.content.Context.NOTIFICATION_SERVICE;
import static com.tachibana.downloader.core.model.data.entity.DownloadInfo.VISIBILITY_HIDDEN;
import static com.tachibana.downloader.core.model.data.entity.DownloadInfo.VISIBILITY_VISIBLE;
import static com.tachibana.downloader.core.model.data.entity.DownloadInfo.VISIBILITY_VISIBLE_NOTIFY_COMPLETED;
import static com.tachibana.downloader.core.model.data.entity.DownloadInfo.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION;

/*
 * Update NotificationManager to reflect current download states.
 * Collapses similar downloads into a single notification.
 */

public class DownloadNotifier
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadNotifier.class.getSimpleName();

    public static final String DEFAULT_NOTIFY_CHAN_ID = "com.tachibana.downloader.DEFAULT_NOTIFY_CHAN";
    public static final String FOREGROUND_NOTIFY_CHAN_ID = "com.tachibana.downloader.FOREGROUND_NOTIFY_CHAN";
    public static final String ACTIVE_DOWNLOADS_NOTIFY_CHAN_ID = "com.tachibana.downloader.CTIVE_DOWNLOADS_NOTIFY_CHAN";
    public static final String PENDING_DOWNLOADS_NOTIFY_CHAN_ID = "com.tachibana.downloader.PENDING_DOWNLOADS_NOTIFY_CHAN";
    public static final String COMPLETED_DOWNLOADS_NOTIFY_CHAN_ID = "com.tachibana.downloader.COMPLETED_DOWNLOADS_NOTIFY_CHAN";

    private static final int TYPE_ACTIVE = 1;
    private static final int TYPE_PENDING = 2;
    private static final int TYPE_COMPLETE = 3;
    /* The minimum amount of time that has to elapse before the progress bar gets updated, ms */
    private static final long MIN_PROGRESS_TIME = 2000;

    private static volatile DownloadNotifier INSTANCE;

    private Context appContext;
    private NotificationManager notifyManager;
    /*
     * Currently active notifications, mapped from clustering tag to timestamp
     * when first shown
     */
    private final ArrayMap<UUID, Notification> activeNotifs = new ArrayMap<>();
    private DataRepository repo;
    private SettingsRepository pref;
    private CompositeDisposable disposables = new CompositeDisposable();
    private FileSystemFacade fs;

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

    public static DownloadNotifier getInstance(@NonNull Context appContext)
    {
        if (INSTANCE == null) {
            synchronized (DownloadNotifier.class) {
                if (INSTANCE == null)
                    INSTANCE = new DownloadNotifier(appContext);
            }
        }
        return INSTANCE;
    }

    private DownloadNotifier(Context appContext)
    {
        this.appContext = appContext;
        notifyManager = (NotificationManager)appContext.getSystemService(NOTIFICATION_SERVICE);
        repo = RepositoryHelper.getDataRepository(appContext);
        pref = RepositoryHelper.getSettingsRepository(appContext);
        fs = SystemFacadeHelper.getFileSystemFacade(appContext);
    }

    public void makeNotifyChans()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;

        ArrayList<NotificationChannel> channels = new ArrayList<>();

        channels.add(new NotificationChannel(DEFAULT_NOTIFY_CHAN_ID,
                appContext.getText(R.string.Default),
                NotificationManager.IMPORTANCE_DEFAULT));
        NotificationChannel foregroundChan = new NotificationChannel(FOREGROUND_NOTIFY_CHAN_ID,
                appContext.getString(R.string.foreground_notification),
                NotificationManager.IMPORTANCE_LOW);
        foregroundChan.setShowBadge(false);
        channels.add(foregroundChan);
        channels.add(new NotificationChannel(ACTIVE_DOWNLOADS_NOTIFY_CHAN_ID,
                appContext.getText(R.string.download_running),
                NotificationManager.IMPORTANCE_MIN));
        channels.add(new NotificationChannel(PENDING_DOWNLOADS_NOTIFY_CHAN_ID,
                appContext.getText(R.string.pending),
                NotificationManager.IMPORTANCE_LOW));
        channels.add(new NotificationChannel(COMPLETED_DOWNLOADS_NOTIFY_CHAN_ID,
                appContext.getText(R.string.finished),
                NotificationManager.IMPORTANCE_DEFAULT));

        notifyManager.createNotificationChannels(channels);
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

    private void update(@NonNull List<InfoAndPieces> infoAndPiecesList)
    {
        synchronized (activeNotifs) {
            HashSet<UUID> ids = new HashSet<>();
            for (InfoAndPieces infoAndPieces : infoAndPiecesList) {
                if (infoAndPieces.info.statusCode == StatusCode.STATUS_STOPPED)
                    continue;

                /* Do not remove current notification */
                ids.add(infoAndPieces.info.id);

                String tag = makeNotificationTag(infoAndPieces.info);
                if (tag == null)
                    continue;

                int type = getNotificationTagType(tag);
                if (checkShowNotification(type)) {
                    Notification notify = activeNotifs.get(infoAndPieces.info.id);

                    boolean force;
                    if (notify == null) {
                        force = true;
                    } else {
                        int prevType = getNotificationTagType(notify.tag);
                        force = type != prevType;
                    }
                    if (!(force || checkUpdateTime(infoAndPieces.info)))
                        continue;

                    updateWithLocked(infoAndPieces, notify, tag, type);
                } else {
                    /* For clearing previous notification */
                    ids.remove(infoAndPieces.info.id);
                }
                if (type == TYPE_COMPLETE && infoAndPieces.info.visibility != VISIBILITY_HIDDEN)
                    markAsHidden(infoAndPieces.info);
            }
            cleanNotifs(ids);
        }
    }

    private boolean checkShowNotification(int type)
    {
        switch (type) {
            case TYPE_ACTIVE:
                return pref.progressNotify();
            case TYPE_PENDING:
                return pref.pendingNotify();
            case TYPE_COMPLETE:
                return pref.finishNotify();
        }

        return false;
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
        if (info.statusCode == StatusCode.STATUS_STOPPED) {
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
        notify.tag = tag;

        boolean isError = StatusCode.isStatusError(info.statusCode);

        /* Use time when notification was first shown to avoid shuffling */
        long firstShown;
        if (notify.timestamp == 0) {
            firstShown = System.currentTimeMillis();
            notify.timestamp = firstShown;
            activeNotifs.put(info.id, notify);
        } else {
            firstShown = notify.timestamp;
        }

        NotificationCompat.Builder builder;
        switch (type) {
            case TYPE_ACTIVE:
                builder = new NotificationCompat.Builder(appContext, ACTIVE_DOWNLOADS_NOTIFY_CHAN_ID);
                break;
            case TYPE_PENDING:
                builder = new NotificationCompat.Builder(appContext, PENDING_DOWNLOADS_NOTIFY_CHAN_ID);
                break;
            case TYPE_COMPLETE:
                builder = new NotificationCompat.Builder(appContext, COMPLETED_DOWNLOADS_NOTIFY_CHAN_ID);
                break;
            default:
                return;
        }
        builder.setColor(ContextCompat.getColor(appContext, R.color.primary));
        builder.setWhen(firstShown);

        switch (type) {
            case TYPE_ACTIVE:
                if (StatusCode.isStatusStoppedOrPaused(info.statusCode))
                    builder.setSmallIcon(R.drawable.ic_pause_white_24dp);
                else
                    builder.setSmallIcon(android.R.drawable.stat_sys_download);
                break;
            case TYPE_PENDING:
                builder.setSmallIcon(R.drawable.ic_warning_white_24dp);
                break;
            case TYPE_COMPLETE:
                applyLegacyNotifySettings(builder);
                if (isError)
                    builder.setSmallIcon(R.drawable.ic_error_white_24dp);
                else
                    builder.setSmallIcon(android.R.drawable.stat_sys_download_done);
                break;
        }

        /* Build a synthetic uri for intent identification purposes */
        Uri uri = new Uri.Builder().scheme("active-dl").appendPath(tag).build();

        /* Build action intents */
        if (type == TYPE_ACTIVE || type == TYPE_PENDING) {
            boolean isStopped = StatusCode.isStatusStoppedOrPaused(info.statusCode);
            if (isStopped) {
                builder.setOngoing(false);
            } else if (type == TYPE_ACTIVE) {
                builder.setOngoing(true);
            }

            Intent pauseResumeButtonIntent = new Intent(NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME,
                    uri, appContext, NotificationReceiver.class);
            pauseResumeButtonIntent.putExtra(NotificationReceiver.TAG_ID, info.id);
            int icon = (isStopped ? R.drawable.ic_play_arrow_white_24dp : R.drawable.ic_pause_white_24dp);
            String text = (isStopped ? appContext.getString(R.string.resume) : appContext.getString(R.string.pause));
            PendingIntent pauseResumeButtonPendingIntent =
                    PendingIntent.getBroadcast(
                            appContext,
                            tag.hashCode(),
                            pauseResumeButtonIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);

            builder.addAction(new NotificationCompat.Action.Builder(
                    icon, text, pauseResumeButtonPendingIntent).build());

            Intent stopButtonIntent = new Intent(NotificationReceiver.NOTIFY_ACTION_CANCEL,
                    uri, appContext, NotificationReceiver.class);
            stopButtonIntent.putExtra(NotificationReceiver.TAG_ID, info.id);
            PendingIntent stopButtonPendingIntent =
                    PendingIntent.getBroadcast(
                            appContext,
                            tag.hashCode(),
                            stopButtonIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);

            builder.addAction(new NotificationCompat.Action.Builder(
                    R.drawable.ic_stop_white_24dp,
                    appContext.getString(R.string.stop),
                    stopButtonPendingIntent)
                    .build());

            /* For starting main activity */
            Intent startupIntent = new Intent(appContext, MainActivity.class);
            startupIntent.setAction(Intent.ACTION_MAIN);
            startupIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            startupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent startupPendingIntent =
                    PendingIntent.getActivity(appContext,
                            tag.hashCode(),
                            startupIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(startupPendingIntent);

        } else if (type == TYPE_COMPLETE) {
            builder.setAutoCancel(true);
            if (!isError) {
                Uri filePath = fs.getFileUri(info.dirPath, info.fileName);
                Intent i = Intent.createChooser(
                        Utils.createOpenFileIntent(appContext, filePath, info.mimeType),
                        appContext.getString(R.string.open_using));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                PendingIntent openPendingIntent = PendingIntent.getActivity(
                        appContext, tag.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT);

                builder.setContentIntent(openPendingIntent);
            }
        }

        /* Calculate and show progress */

        int size = infoAndPieces.pieces.size();
        long downloadBytes = 0;
        long speed = 0;

        if (size > 0) {
            for (DownloadPiece piece : infoAndPieces.pieces) {
                downloadBytes += info.getDownloadedBytes(piece);
                speed += piece.speed;
            }
        }

        int progress = 0;
        long ETA = Utils.calcETA(info.totalBytes, downloadBytes, speed);
        if (type == TYPE_ACTIVE) {
            if (info.statusCode == StatusCode.STATUS_FETCH_METADATA) {
                builder.setProgress(100, 0, true);
            } else {
                if (info.totalBytes > 0) {
                    progress = (int)((downloadBytes * 100) / info.totalBytes);
                    if (StatusCode.isStatusStoppedOrPaused(info.statusCode))
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
                builder.setTicker(appContext.getString(R.string.download_ticker_notify, info.fileName));

                NotificationCompat.BigTextStyle progressBigText = new NotificationCompat.BigTextStyle();
                if (info.statusCode == StatusCode.STATUS_RUNNING) {
                    progressBigText.bigText(appContext.getString(R.string.download_queued_progress_template,
                            Formatter.formatFileSize(appContext, downloadBytes),
                            (info.totalBytes == -1 ? appContext.getString(R.string.not_available) :
                                    Formatter.formatFileSize(appContext, info.totalBytes)),
                            (ETA == -1 ? Utils.INFINITY_SYMBOL :
                                    DateUtils.formatElapsedTime(appContext, ETA)),
                            Formatter.formatFileSize(appContext, speed)));
                } else {
                    String statusStr = "";
                    switch (info.statusCode) {
                        case StatusCode.STATUS_PAUSED:
                            statusStr = appContext.getString(R.string.pause);
                            break;
                        case StatusCode.STATUS_STOPPED:
                            statusStr = appContext.getString(R.string.stopped);
                            break;
                        case StatusCode.STATUS_FETCH_METADATA:
                            statusStr = appContext.getString(R.string.downloading_metadata);
                            break;
                    }
                    progressBigText.bigText(appContext.getString(R.string.download_queued_template,
                            Formatter.formatFileSize(appContext, downloadBytes),
                            (info.totalBytes == -1 ? appContext.getString(R.string.not_available) :
                                    Formatter.formatFileSize(appContext, info.totalBytes)),
                            statusStr));
                }
                builder.setStyle(progressBigText);
                break;
            case TYPE_PENDING:
                builder.setContentTitle(info.fileName);
                builder.setTicker(appContext.getString(R.string.download_in_queue_ticker_notify, info.fileName));

                NotificationCompat.BigTextStyle pendingBigText = new NotificationCompat.BigTextStyle();
                String downloadBytesStr = Formatter.formatFileSize(appContext, downloadBytes);
                String totalBytesStr = (info.totalBytes == -1 ?
                        appContext.getString(R.string.not_available) :
                        Formatter.formatFileSize(appContext, info.totalBytes));
                String statusStr;
                switch (info.statusCode) {
                    case StatusCode.STATUS_WAITING_FOR_NETWORK:
                        statusStr = appContext.getString(R.string.waiting_for_network);
                        break;
                    case StatusCode.STATUS_WAITING_TO_RETRY:
                        statusStr = appContext.getString(R.string.waiting_for_retry);
                        break;
                    default:
                        statusStr = appContext.getString(R.string.pending);
                        break;
                }
                pendingBigText.bigText(appContext.getString(R.string.download_queued_template,
                        downloadBytesStr,
                        totalBytesStr,
                        statusStr));
                builder.setStyle(pendingBigText);
                break;
            case TYPE_COMPLETE:
                if (isError) {
                    builder.setContentTitle(info.fileName);
                    builder.setTicker(appContext.getString(R.string.download_error_notify_title));
                    builder.setContentText(appContext.getString(R.string.error_template, info.statusMsg));
                } else {
                    builder.setContentTitle(appContext.getString(R.string.download_finished_notify));
                    builder.setTicker(appContext.getString(R.string.download_finished_notify));
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

        if (prevTag != null && !prevTag.equals(notify.tag)) {
            notifyManager.cancel(prevTag, 0);
        }
        notifyManager.notify(notify.tag, 0, builder.build());
    }

    /*
     * Disable notifications for download
     */

    private void markAsHidden(DownloadInfo info)
    {
        info.visibility = VISIBILITY_HIDDEN;

        disposables.add(Completable.fromAction(() -> repo.updateInfo(info, false, false))
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
                (visibility == VISIBILITY_VISIBLE_NOTIFY_COMPLETED ||
                 visibility == VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION);
    }

    /*
     * Starting with the version of Android 8.0,
     * setting notifications from the app preferences isn't working,
     * you can change them only in the settings of Android 8.0
     */

    private void applyLegacyNotifySettings(NotificationCompat.Builder builder)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            return;

        SettingsRepository pref = RepositoryHelper.getSettingsRepository(appContext);

        if (pref.playSoundNotify())
            builder.setSound(Uri.parse(pref.notifySound()));

        if (pref.vibrationNotify())
            builder.setVibrate(new long[] {1000}); /* ms */

        if (pref.ledIndicatorNotify())
            builder.setLights(pref.ledIndicatorColorNotify(), 1000, 1000); /* ms */
    }
}
