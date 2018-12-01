/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of DownloadNavi.
 *
 * DownloadNavi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DownloadNavi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DownloadNavi.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tachibana.downloader.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.os.Message;
import android.text.format.Formatter;
import android.util.Log;

import com.tachibana.downloader.MainActivity;
import com.tachibana.downloader.R;
import com.tachibana.downloader.core.DownloadInfo;
import com.tachibana.downloader.core.DownloadMsg;
import com.tachibana.downloader.core.DownloadThread;
import com.tachibana.downloader.core.utils.DateFormatUtils;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.core.storage.DownloadStorage;
import com.tachibana.downloader.receiver.NotificationReceiver;
import com.tachibana.downloader.settings.SettingsManager;

import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class DownloadService extends Service
    implements SharedPreferences.OnSharedPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadService.class.getSimpleName();

    private static final int FOREGROUND_NOTIFICATION_ID = 1;
    public static final String FOREGROUND_NOTIFY_CHAN_ID = "com.tachibana.downloader.FOREGROUND_NOTIFY_CHAN";
    public static final String PROGRESS_CHAN_ID = "com.tachibana.downloader.PROGRESS_CHAN";
    public static final String DEFAULT_CHAN_ID = "com.tachibana.downloader.DEFAULT_CHAN";
    public static final String ACTION_SHUTDOWN = "com.tachibana.downloader.service.DownloadService.ACTION_SHUTDOWN";
    public static final String ACTION_RUN_DOWNLOAD = "com.tachibana.downloader.service.ACTION_RUN_DOWNLOAD";
    public static final String TAG_ADD_DOWNLOAD_PARAMS = "add_download_params";
    public static final String TAG_DOWNLOAD_ID = "download_id";

    private boolean isAlreadyRunning;
    private NotificationManager notifyManager;
    private NotificationCompat.Builder foregroundNotify;
    private final IBinder binder = new LocalBinder();
    private DownloadHandler handler = new DownloadHandler(this);
    private AtomicBoolean isPauseButton = new AtomicBoolean(true);
    private DownloadStorage storage;
    private SharedPreferences pref;
    private ConcurrentHashMap<UUID, DownloadThread> tasks =  new ConcurrentHashMap<>();

    public class LocalBinder extends Binder
    {
        public DownloadService getService()
        {
            return DownloadService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return binder;
    }

    private void init()
    {
        Log.i(TAG, "Start " + TAG);
        pref = SettingsManager.getPreferences(getApplicationContext());
        pref.registerOnSharedPreferenceChangeListener(this);
        notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Context context = getApplicationContext();
        storage = new DownloadStorage(context);

        makeNotifyChans(notifyManager);
        makeForegroundNotify();
    }

    private void stopService()
    {
        pref.unregisterOnSharedPreferenceChangeListener(this);
        isAlreadyRunning = false;
        pref = null;

        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        Log.i(TAG, "Stop " + TAG);
    }

    private void shutdown()
    {
        Intent shutdownIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        shutdownIntent.setAction(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP);
        sendBroadcast(shutdownIntent);
    }

    private void requestStop()
    {
        stopAllDownloads();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        /* The first start */
        if (!isAlreadyRunning) {
            isAlreadyRunning = true;
            init();
        }

        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP:
                case ACTION_SHUTDOWN:
                    requestStop();
                    return START_NOT_STICKY;
                case ACTION_RUN_DOWNLOAD:
                    UUID id = (UUID)intent.getSerializableExtra(TAG_DOWNLOAD_ID);
                    if (id != null)
                        runDownload(id);
                    break;
                case NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME_ALL:
                    boolean pause = isPauseButton.getAndSet(!isPauseButton.get());
                    updateForegroundNotifyActions();
                    if (pause)
                        pauseAllDownloads();
                    else
                        resumeAllDownloads();
                    break;
                case NotificationReceiver.NOTIFY_ACTION_CANCEL_ALL:
                    cancelAllDownloads();
                    break;
                case NotificationReceiver.NOTIFY_ACTION_CANCEL:
                    cancelDownload((UUID)intent.getSerializableExtra(NotificationReceiver.TAG_ID));
                    break;
                case NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME:
                    pauseResumeDownload((UUID)intent.getSerializableExtra(NotificationReceiver.TAG_ID));
                    break;
            }
        }

        /* Clear old notifications */
        try {
            NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (manager != null)
                manager.cancelAll();
        } catch (SecurityException e) {
            /* Ignore */
        }

        return START_STICKY;
    }

    private static class DownloadHandler extends Handler
    {
        private final WeakReference<DownloadService> service;

        private DownloadHandler(DownloadService service)
        {
            this.service = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg)
        {
            if (service.get() == null)
                return;

            UUID id = (UUID)msg.getData().getSerializable(DownloadMsg.TAG_DOWNLOAD_ID);
            if (id == null)
                return;

            Bundle data = msg.getData();
            switch (msg.what) {
                case DownloadMsg.MSG_PROGRESS_CHANGED:
                    long downloadBytes = data.getLong(DownloadMsg.TAG_DOWNLOAD_BYTES);
                    long speed = data.getLong(DownloadMsg.TAG_SPEED);
                    service.get().makeDownloadNotify(id, downloadBytes, speed);
                    break;
                case DownloadMsg.MSG_FINISHED:
                    service.get().onFinished(id);
                    break;
                case DownloadMsg.MSG_PAUSED:
                    service.get().onPaused(id);
                    break;
                case DownloadMsg.MSG_CANCELLED:
                    service.get().onCancelled(id);
                    break;
            }
        }
    }

    private void onFinished(UUID id)
    {
        tasks.remove(id);

        DownloadInfo info = storage.getInfoById(id);
        if (info != null) {
            if (info.isStatusSuccess()) {
                makeFinishNotify(info);
            } else if (info.isStatusError()) {
                switch (info.getStatusCode()) {
                    case HttpURLConnection.HTTP_UNAUTHORIZED:
                        /* TODO: request authorization from user */
                        break;
                    case HttpURLConnection.HTTP_PROXY_AUTH:
                        /* TODO: proxy support */
                        break;
                    default:
                        makeDownloadErrorNotify(info);
                        break;
                }
            } else {
                switch (info.getStatusCode()) {
                    case DownloadInfo.STATUS_WAITING_TO_RETRY:
                    case DownloadInfo.STATUS_WAITING_FOR_NETWORK:
                        DownloadScheduler.runDownload(getApplicationContext(), id);
                        break;
                    case DownloadInfo.STATUS_INSUFFICIENT_SPACE_ERROR:
                        makeDownloadErrorNotify(info);
                        break;
                }
            }
        }

        checkShutdownService();
    }

    private void onPaused(UUID id)
    {
        DownloadThread task = tasks.get(id);
        long downloadBytes = (task != null ? task.getDownloadBytes() : 0);
        tasks.remove(id);
        makeDownloadNotify(id, downloadBytes, 0);
    }

    private void onCancelled(UUID id)
    {
        tasks.remove(id);
        notifyManager.cancel(id.hashCode());

        checkShutdownService();
    }

    private void checkShutdownService()
    {
        if (tasks.isEmpty())
            stopService();
    }

    private void runDownload(UUID id)
    {
        if (id == null)
            return;

        DownloadThread task = tasks.get(id);
        if (task != null && task.isRunning())
            return;

        DownloadInfo info = storage.getInfoById(id);
        if (info == null)
            return;
        info.setStatusCode(DownloadInfo.STATUS_PENDING);
        storage.updateInfo(info, false, false);

        task = new DownloadThread(id, getApplicationContext(), handler);
        tasks.put(id, task);
        task.start();
    }

    private void pauseResumeDownload(UUID id)
    {
        if (id == null)
            return;

        DownloadInfo info = storage.getInfoById(id);
        if (info == null || !info.isPartialSupport())
            return;

        if (info.getStatusCode() == DownloadInfo.STATUS_PAUSED) {
            DownloadScheduler.runDownload(getApplicationContext(), id);
        } else {
            DownloadThread task = tasks.get(id);
            if (task == null)
                return;

            task.requestPause();
        }
    }

    private void cancelDownload(UUID id)
    {
        if (id == null)
            return;

        storage.deleteInfo(id, true);

        DownloadThread task = tasks.get(id);
        if (task == null) {
            checkShutdownService();
            return;
        }

        task.requestCancel();
    }

    private void pauseAllDownloads()
    {
        for (Map.Entry<UUID, DownloadThread> entry : tasks.entrySet()) {
            DownloadThread task = entry.getValue();
            if (task == null)
                continue;
            DownloadInfo info = storage.getInfoById(entry.getKey());
            if (info == null || !info.isPartialSupport())
                continue;
            task.requestPause();
        }
    }

    private void resumeAllDownloads()
    {
        for (DownloadInfo info : storage.getAllInfo()) {
            if (info == null || !info.isPartialSupport())
                continue;
            DownloadThread task = tasks.get(info.getId());
            if (task == null || task.isRunning())
                continue;
            if (info.getStatusCode() == DownloadInfo.STATUS_PAUSED)
                DownloadScheduler.runDownload(getApplicationContext(), info.getId());
        }
    }

    private void cancelAllDownloads()
    {
        for (Map.Entry<UUID, DownloadThread> entry : tasks.entrySet()) {
            DownloadThread task = entry.getValue();
            UUID id = entry.getKey();

            storage.deleteInfo(id, true);
            if (task != null)
                task.requestCancel();
        }
    }

    private void stopAllDownloads()
    {
        for (DownloadThread task : tasks.values()) {
            if (task != null)
                task.requestCancel();
        }
    }


    private void makeNotifyChans(NotificationManager notifyManager)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;

        ArrayList<NotificationChannel> chans = new ArrayList<>();
        NotificationChannel defaultChan = new NotificationChannel(DEFAULT_CHAN_ID, getString(R.string.def),
                NotificationManager.IMPORTANCE_DEFAULT);
        chans.add(defaultChan);

        NotificationChannel progressChan = new NotificationChannel(PROGRESS_CHAN_ID, getString(R.string.progress_notification),
                NotificationManager.IMPORTANCE_LOW);

        chans.add(progressChan);

        chans.add(new NotificationChannel(FOREGROUND_NOTIFY_CHAN_ID, getString(R.string.foreground_notification),
                NotificationManager.IMPORTANCE_LOW));
        notifyManager.createNotificationChannels(chans);
    }

    private void makeForegroundNotify() {
        /* For starting main activity */
        Intent startupIntent = new Intent(getApplicationContext(), MainActivity.class);
        startupIntent.setAction(Intent.ACTION_MAIN);
        startupIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        startupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent startupPendingIntent =
                PendingIntent.getActivity(getApplicationContext(),
                        0,
                        startupIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        foregroundNotify = new NotificationCompat.Builder(getApplicationContext(),
                FOREGROUND_NOTIFY_CHAN_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(startupPendingIntent)
                .setContentTitle(getString(R.string.app_running_in_the_background))
                .setWhen(System.currentTimeMillis());

        foregroundNotify.addAction(makePauseAllAction());
        foregroundNotify.addAction(makeStopAllAction());
        foregroundNotify.addAction(makeShutdownAction());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            foregroundNotify.setCategory(Notification.CATEGORY_PROGRESS);

        /* Disallow killing the service process by system */
        startForeground(FOREGROUND_NOTIFICATION_ID, foregroundNotify.build());
    }

    private NotificationCompat.Action makePauseAllAction()
    {
        Intent funcButtonIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        funcButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME_ALL);
        boolean isPause = isPauseButton.get();
        int icon = (isPause ? R.drawable.ic_pause_white_24dp : R.drawable.ic_play_arrow_white_24dp);
        String text = (isPause ? getString(R.string.pause_all) : getString(R.string.resume_all));
        PendingIntent funcButtonPendingIntent =
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        funcButtonIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(icon, text, funcButtonPendingIntent).build();
    }

    private NotificationCompat.Action makeStopAllAction()
    {
        Intent funcButtonIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        funcButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_CANCEL_ALL);
        PendingIntent funcButtonPendingIntent =
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        funcButtonIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_stop_white_24dp,
                getString(R.string.stop_all),
                funcButtonPendingIntent)
                .build();
    }

    private NotificationCompat.Action makeShutdownAction()
    {
        Intent shutdownIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        shutdownIntent.setAction(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP);
        PendingIntent shutdownPendingIntent =
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        shutdownIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_power_settings_new_white_24dp,
                getString(R.string.shutdown),
                shutdownPendingIntent)
                .build();
    }

    private void updateForegroundNotifyActions()
    {
        if (foregroundNotify == null)
            return;

        foregroundNotify.mActions.clear();
        foregroundNotify.addAction(makePauseAllAction());
        foregroundNotify.addAction(makeStopAllAction());
        foregroundNotify.addAction(makeShutdownAction());
        startForeground(FOREGROUND_NOTIFICATION_ID, foregroundNotify.build());
    }

    private void makeFinishNotify(DownloadInfo info)
    {
        if (info == null)
            return;

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(info.getFilePath(), info.getMimeType());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        PendingIntent openPendingIntent =
                PendingIntent.getActivity(
                        getApplicationContext(),
                        0,
                        Intent.createChooser(intent, getString(R.string.open_using)),
                        PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                DEFAULT_CHAN_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(getString(R.string.download_finished_notify))
                .setContentIntent(openPendingIntent)
                .setTicker(getString(R.string.download_finished_notify))
                .setContentText(info.getFileName())
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_STATUS);

        notifyManager.notify(info.getId().hashCode(), builder.build());
    }

    private void makeDownloadErrorNotify(DownloadInfo info)
    {
        if (info == null || info.getStatusMsg() == null)
            return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                DEFAULT_CHAN_ID)
                .setSmallIcon(R.drawable.ic_error_white_24dp)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(info.getFileName())
                .setTicker(getString(R.string.download_error_notify_title))
                .setContentText(String.format(getString(R.string.download_error_notify_template), info.getStatusMsg()))
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_ERROR);

        notifyManager.notify(info.getId().hashCode(), builder.build());
    }

    private void makeDownloadNotify(UUID id, long downloadSize, long speed)
    {
        makeDownloadNotify(storage.getInfoById(id), downloadSize, speed);
    }

    private void makeDownloadNotify(DownloadInfo info, long downloadBytes, long speed)
    {
        if (info == null)
            return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                PROGRESS_CHAN_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(info.getFileName())
                .setTicker(getString(R.string.download_finished_notify))
                .setAutoCancel(false)
                .setWhen(System.currentTimeMillis());

        long totalBytes = info.getTotalBytes();
        int progress = (int)((downloadBytes * 100) / totalBytes);
        long ETA = calcETA(totalBytes, downloadBytes, speed);

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        if (info.getStatusCode() == DownloadInfo.STATUS_PAUSED) {
            builder.setProgress(0, 0, false);
            bigTextStyle.bigText(String.format(getString(R.string.pause_notify_template),
                            Formatter.formatFileSize(this, downloadBytes),
                            Formatter.formatFileSize(this, totalBytes),
                            progress));
        } else {
           bigTextStyle.bigText(String.format(getString(R.string.downloading_notify_template),
                            Formatter.formatFileSize(this, downloadBytes),
                            Formatter.formatFileSize(this, totalBytes),
                            progress,
                            (ETA == -1 ? Utils.INFINITY_SYMBOL :
                                    DateFormatUtils.formatElapsedTime(getApplicationContext(), ETA)),
                            Formatter.formatFileSize(this, speed)));
            builder.setProgress(100, progress, false);
        }
        builder.setStyle(bigTextStyle);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_PROGRESS);

        if (info.isPartialSupport())
            builder.addAction(makePauseAction(info));
        builder.addAction(makeStopAction(info));

        notifyManager.notify(info.getId().hashCode(), builder.build());
    }

    private long calcETA(long totalBytes, long curBytes, long speed)
    {
        long left = totalBytes - curBytes;
        if (left <= 0)
            return 0;
        if (speed <= 0)
            return -1;
        return left / speed;
    }

    private NotificationCompat.Action makePauseAction(DownloadInfo info)
    {
        Intent funcButtonIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        funcButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME);
        funcButtonIntent.putExtra(NotificationReceiver.TAG_ID, info.getId());
        boolean isPause = info.getStatusCode() == DownloadInfo.STATUS_PAUSED;
        int icon = (isPause ? R.drawable.ic_play_arrow_white_24dp : R.drawable.ic_pause_white_24dp);
        String text = (isPause ? getString(R.string.resume) : getString(R.string.pause));
        PendingIntent funcButtonPendingIntent =
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        funcButtonIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(icon, text, funcButtonPendingIntent).build();
    }

    private NotificationCompat.Action makeStopAction(DownloadInfo info)
    {
        Intent funcButtonIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        funcButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_CANCEL);
        funcButtonIntent.putExtra(NotificationReceiver.TAG_ID, info.getId());
        PendingIntent funcButtonPendingIntent =
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        funcButtonIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_stop_white_24dp,
                getString(R.string.stop),
                funcButtonPendingIntent)
                .build();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        if (pref == null)
            pref = sharedPreferences;

        if (key.equals(getString(R.string.pref_key_wifi_only)) ||
            key.equals(getString(R.string.pref_key_enable_roaming))) {
            if (Utils.isNetworkTypeAllowed(getApplicationContext()))
                resumeAllDownloads();
            else
                pauseAllDownloads();
        }
    }
}
