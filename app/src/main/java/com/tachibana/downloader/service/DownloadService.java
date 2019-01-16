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

package com.tachibana.downloader.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LifecycleService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import android.util.Log;

import com.tachibana.downloader.MainActivity;
import com.tachibana.downloader.MainApplication;
import com.tachibana.downloader.R;
import com.tachibana.downloader.worker.DownloadScheduler;
import com.tachibana.downloader.core.DownloadNotifier;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.DownloadResult;
import com.tachibana.downloader.core.DownloadThread;
import com.tachibana.downloader.core.StatusCode;
import com.tachibana.downloader.core.entity.InfoAndPieces;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.receiver.NotificationReceiver;
import com.tachibana.downloader.settings.SettingsManager;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class DownloadService extends LifecycleService
        implements SharedPreferences.OnSharedPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadService.class.getSimpleName();

    private static final int FOREGROUND_NOTIFICATION_ID = 1;
    public static final String FOREGROUND_NOTIFY_CHAN_ID = "com.tachibana.downloader.FOREGROUND_NOTIFY_CHAN";
    public static final String ACTION_SHUTDOWN = "com.tachibana.downloader.service.DownloadService.ACTION_SHUTDOWN";
    public static final String ACTION_RUN_DOWNLOAD = "com.tachibana.downloader.service.ACTION_RUN_DOWNLOAD";
    public static final String ACTION_PAUSE_DOWNLOAD = "com.tachibana.downloader.service.ACTION_PAUSE_DOWNLOAD";
    public static final String TAG_DOWNLOAD_ID = "download_id";

    private boolean isAlreadyRunning;
    private NotificationManager notifyManager;
    private DownloadNotifier downloadNotifier;
    private NotificationCompat.Builder foregroundNotify;
    private AtomicBoolean isPauseButton = new AtomicBoolean(true);
    private DataRepository repo;
    private SharedPreferences pref;
    private CompositeDisposable disposables = new CompositeDisposable();
    private HashMap<UUID, DownloadThread> tasks =  new HashMap<>();
    private HashMap<UUID, Disposable> observableInfoList = new HashMap<>();
    private boolean requestShutdown;

    private void init()
    {
        Log.i(TAG, "Start " + TAG);
        pref = SettingsManager.getPreferences(getApplicationContext());
        pref.registerOnSharedPreferenceChangeListener(this);
        notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        downloadNotifier = new DownloadNotifier(getApplicationContext());
        repo = ((MainApplication)getApplication()).getRepository();

        makeNotifyChans(notifyManager);
        makeForegroundNotify();
    }

    private void stopService()
    {
        disposables.clear();
        observableInfoList.clear();
        pref.unregisterOnSharedPreferenceChangeListener(this);
        isAlreadyRunning = false;
        pref = null;

        /* If manually shutdown */
        if (requestShutdown)
            clearNotifications();
        requestShutdown = false;

        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        Log.i(TAG, "Stop " + TAG);
    }

    private void requestShutdown()
    {
        requestShutdown = true;
        if (tasks.isEmpty())
            stopService();
        else
            stopAllDownloads();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);

        /* The first start */
        if (!isAlreadyRunning) {
            isAlreadyRunning = true;
            init();
        }

        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP:
                case ACTION_SHUTDOWN:
                    requestShutdown();
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
                case ACTION_PAUSE_DOWNLOAD:
                    pauseResumeDownload((UUID)intent.getSerializableExtra(TAG_DOWNLOAD_ID));
                    break;
                case NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME:
                    pauseResumeDownload((UUID)intent.getSerializableExtra(NotificationReceiver.TAG_ID));
                    break;
            }
        }
        /* TODO: autoloading of stopped downloads */

        /* Clear old notifications */
        clearNotifications();

        return START_STICKY;
    }

    private void clearNotifications()
    {
        try {
            NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (manager != null)
                manager.cancelAll();
        } catch (SecurityException e) {
            /* Ignore */
        }
    }

    private void observeDownloadResult(DownloadResult result)
    {
        if (result == null)
            return;

        switch (result.status) {
            case FINISHED:
                onFinished(result.infoId);
                break;
            case CANCELLED:
                onCancelled(result.infoId);
                break;
        }
    }

    private void onFinished(UUID id)
    {
        deleteDownloadTask(id);

        disposables.add(repo.getInfoByIdSingle(id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .filter((info) -> info != null)
                .subscribe((info) -> {
                            handleInfoStatus(info);
                            checkShutdownService();
                        },
                        (Throwable t) -> {
                            Log.e(TAG, "Getting info " + id + " error: " +
                                    Log.getStackTraceString(t));
                        })
        );
    }

    private void handleInfoStatus(DownloadInfo info)
    {
        if (info == null)
            return;

        if (StatusCode.isStatusSuccess(info.statusCode)) {
            downloadNotifier.update(info, true);
        } else if (StatusCode.isStatusError(info.statusCode)) {
            switch (info.statusCode) {
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    /* TODO: request authorization from user */
                    break;
                case HttpURLConnection.HTTP_PROXY_AUTH:
                    /* TODO: proxy support */
                    break;
                default:
                    downloadNotifier.update(info, true);
                    break;
            }
        } else {
            switch (info.statusCode) {
                case StatusCode.STATUS_WAITING_TO_RETRY:
                case StatusCode.STATUS_WAITING_FOR_NETWORK:
                    DownloadScheduler.runDownload(getApplicationContext(), info);
                    break;
            }
        }
    }

    private void onCancelled(UUID id)
    {
        deleteDownloadTask(id);
        /*
         * Control deletion of the notification if the state
         * of the removed object wasn't received by the manager
         */
        downloadNotifier.remove(id);

        checkShutdownService();
    }

    private void checkShutdownService()
    {
        if (tasks.isEmpty() || requestShutdown)
            stopService();
    }

    private synchronized void runDownload(UUID id)
    {
        if (id == null)
            return;

        DownloadThread task = tasks.get(id);
        if (task != null && task.isRunning())
            return;

        task = new DownloadThread(id, getApplicationContext(), repo);
        tasks.put(id, task);
        disposables.add(Observable.fromCallable(task)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::observeDownloadResult,
                        (Throwable t) -> Log.e(TAG, Log.getStackTraceString(t))
                )
        );
        observeInfo(id);
    }

    private void deleteDownloadTask(UUID id)
    {
        tasks.remove(id);
        stopObserveInfo(id);
    }

    private void observeInfo(UUID id)
    {
        Disposable d = observableInfoList.get(id);
        if (d != null && !d.isDisposed())
            return;

        Disposable disposable = repo.observeInfoAndPiecesById(id)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::updateDownloadNotify,
                                (Throwable t) -> {
                                    Log.e(TAG, "Getting info and pieces" + id + " error: " +
                                            Log.getStackTraceString(t));
                                });
        observableInfoList.put(id, disposable);
        disposables.add(disposable);
    }

    private void stopObserveInfo(UUID id)
    {
        Disposable d = observableInfoList.remove(id);
        if (d != null && !d.isDisposed())
            d.dispose();
    }

    private void updateDownloadNotify(InfoAndPieces infoAndPieces)
    {
        if (infoAndPieces == null)
            return;

        boolean force = StatusCode.isStatusCompleted(infoAndPieces.info.statusCode) ||
                        infoAndPieces.info.statusCode == StatusCode.STATUS_PAUSED;
        downloadNotifier.update(infoAndPieces, force);
    }

    private synchronized void pauseResumeDownload(UUID id)
    {
        if (id == null)
            return;

        disposables.add(repo.getInfoByIdSingle(id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .filter((info) -> info != null && info.partialSupport)
                .subscribe((info) -> {
                            if (info.statusCode == StatusCode.STATUS_PAUSED) {
                                DownloadScheduler.runDownload(getApplicationContext(), info);
                            } else {
                                DownloadThread task = tasks.get(id);
                                if (task == null)
                                    return;

                                task.requestPause();
                            }
                        },
                        (Throwable t) -> {
                            Log.e(TAG, "Getting info " + id + " error: " +
                                    Log.getStackTraceString(t));
                        })
        );
    }

    private synchronized void cancelDownload(UUID id)
    {
        if (id == null)
            return;

        disposables.add(repo.getInfoByIdSingle(id)
                .subscribeOn(Schedulers.io())
                .filter((info) -> info != null)
                .subscribe((info) -> repo.deleteInfo(getApplicationContext(), info, true),
                        (Throwable t) -> {
                            Log.e(TAG, "Getting info " + id + " error: " +
                                    Log.getStackTraceString(t));
                        }
                )
        );

        DownloadThread task = tasks.get(id);
        if (task == null) {
            checkShutdownService();
            return;
        }

        task.requestCancel();
    }

    private synchronized void pauseAllDownloads()
    {
        for (Map.Entry<UUID, DownloadThread> entry : tasks.entrySet()) {
            DownloadThread task = entry.getValue();
            if (task == null)
                continue;
            task.requestPause();
        }
    }

    private synchronized void resumeAllDownloads()
    {
        disposables.add(repo.getAllInfoSingle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .filter((list) -> !list.isEmpty())
                .flattenAsObservable((it) -> it)
                .filter((info) -> {
                    return info != null &&
                            info.partialSupport &&
                            info.statusCode == StatusCode.STATUS_PAUSED;
                })
                .subscribe((info) -> {
                            DownloadThread task = tasks.get(info.id);
                            if (task != null && task.isRunning())
                                return;
                            DownloadScheduler.runDownload(getApplicationContext(), info);
                        },
                        (Throwable t) -> {
                            Log.e(TAG, "Getting info list error: " + Log.getStackTraceString(t));
                        })
        );
    }

    private synchronized void cancelAllDownloads()
    {
        disposables.add(Observable.fromIterable(tasks.entrySet())
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .filter((entry) -> entry.getValue() != null)
                .subscribe((entry) -> {
                    DownloadThread task = entry.getValue();
                    UUID id = entry.getKey();
                    if (task == null)
                        return;

                    DownloadInfo info = repo.getInfoById(id);
                    if (info != null)
                        repo.deleteInfo(getApplicationContext(), info, true);

                    task.requestCancel();
                })
        );
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

        notifyManager.createNotificationChannel(
                new NotificationChannel(FOREGROUND_NOTIFY_CHAN_ID,
                        getString(R.string.foreground_notification),
                        NotificationManager.IMPORTANCE_LOW));
    }

    private void makeForegroundNotify()
    {
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
