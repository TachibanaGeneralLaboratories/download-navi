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
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import com.tachibana.downloader.MainActivity;
import com.tachibana.downloader.MainApplication;
import com.tachibana.downloader.R;
import com.tachibana.downloader.core.ChangeableParams;
import com.tachibana.downloader.core.DownloadEngine;
import com.tachibana.downloader.core.DownloadEngineListener;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.receiver.NotificationReceiver;
import com.tachibana.downloader.settings.SettingsManager;

import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LifecycleService;

/*
 * Only for work that exceeds 10 minutes and and it's impossible to use WorkManager.
 */

public class DownloadService extends LifecycleService
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadService.class.getSimpleName();

    private static final int FOREGROUND_NOTIFICATION_ID = 1;
    private static final int APPLYING_PARAMS_NOTIFICATION_ID = 2;
    public static final String ACTION_SHUTDOWN = "com.tachibana.downloader.service.DownloadService.ACTION_SHUTDOWN";
    public static final String ACTION_RUN_DOWNLOAD = "com.tachibana.downloader.service.ACTION_RUN_DOWNLOAD";
    public static final String ACTION_CHANGE_PARAMS = "com.tachibana.downloader.service.ACTION_CHANGE_PARAMS";
    public static final String TAG_DOWNLOAD_ID = "download_id";
    public static final String TAG_PARAMS = "params";

    private boolean isAlreadyRunning;
    private NotificationManager notifyManager;
    private NotificationCompat.Builder foregroundNotify;
    private DownloadEngine engine;
    private SharedPreferences pref;
    private PowerManager.WakeLock wakeLock;
    private boolean downloadsApplyingParams;

    private void init()
    {
        Log.i(TAG, "Start " + TAG);
        notifyManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        pref = SettingsManager.getInstance(getApplicationContext()).getPreferences();
        pref.registerOnSharedPreferenceChangeListener(sharedPreferenceListener);

        setKeepCpuAwake(pref.getBoolean(getString(R.string.pref_key_cpu_do_not_sleep),
                                        SettingsManager.Default.cpuDoNotSleep));
        engine = ((MainApplication)getApplication()).getDownloadEngine();
        engine.addListener(listener);

        makeForegroundNotify();
    }

    private DownloadEngineListener listener = new DownloadEngineListener()
    {
        @Override
        public void onDownloadsCompleted()
        {
            if (checkStopService())
                stopService();
        }

        @Override
        public void onApplyingParams(@NonNull UUID id)
        {
            downloadsApplyingParams = true;
            makeApplyingParamsNotify();
        }

        @Override
        public void onParamsApplied(@NonNull UUID id, @Nullable String name, @Nullable Throwable e)
        {
            downloadsApplyingParams = false;
            makeApplyingParamsNotify();
            if (e != null && name != null)
                makeApplyingParamsErrorNotify(id, name, e);
            if (checkStopService())
                stopService();
        }
    };

    private boolean checkStopService()
    {
        if (downloadsApplyingParams)
            return false;

        return !engine.hasActiveDownloads();
    }

    private void stopService()
    {
        pref.unregisterOnSharedPreferenceChangeListener(sharedPreferenceListener);
        engine.removeListener(listener);
        isAlreadyRunning = false;
        engine = null;
        pref = null;
        setKeepCpuAwake(false);

        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        Log.i(TAG, "Stop " + TAG);
    }

    @Override
    public void onLowMemory()
    {
        super.onLowMemory();

        if (engine != null)
            engine.stopDownloads();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);

        /* The first start */
        if (!isAlreadyRunning) {
            isAlreadyRunning = true;
            init();
            /* Run by system */
            if (intent != null && intent.getAction() == null)
                engine.restoreDownloads();
        }

        if (intent != null && intent.getAction() != null) {
            UUID id;
            switch (intent.getAction()) {
                case NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP:
                case ACTION_SHUTDOWN:
                    if (engine != null)
                        engine.stopDownloads();
                    if (!downloadsApplyingParams && (engine == null || !engine.hasActiveDownloads()))
                        stopService();
                    return START_NOT_STICKY;
                case ACTION_RUN_DOWNLOAD:
                    id = (UUID)intent.getSerializableExtra(TAG_DOWNLOAD_ID);
                    if (id != null && engine != null)
                        engine.doRunDownload(id);
                    break;
                case ACTION_CHANGE_PARAMS:
                    id = (UUID)intent.getSerializableExtra(TAG_DOWNLOAD_ID);
                    ChangeableParams params = intent.getParcelableExtra(TAG_PARAMS);
                    if (id != null && params != null) {
                        downloadsApplyingParams = true;
                        makeApplyingParamsNotify();
                        engine.doChangeParams(id, params);
                    }
                    break;
                case NotificationReceiver.NOTIFY_ACTION_PAUSE_ALL:
                    if (engine != null)
                        engine.pauseAllDownloads();
                    break;
                case NotificationReceiver.NOTIFY_ACTION_RESUME_ALL:
                    if (engine != null)
                        engine.resumeDownloads(false);
                    break;
                case NotificationReceiver.NOTIFY_ACTION_CANCEL:
                    id = (UUID)intent.getSerializableExtra(NotificationReceiver.TAG_ID);
                    if (id != null && engine != null)
                        engine.deleteDownloads(true, id);
                    break;
                case NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME:
                    id = (UUID)intent.getSerializableExtra(NotificationReceiver.TAG_ID);
                    if (id != null && engine != null)
                        engine.pauseResumeDownload(id);
                    break;
            }
        }

        return START_STICKY;
    }

    private void setKeepCpuAwake(boolean enable)
    {
        if (enable) {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            }

            if (!wakeLock.isHeld())
                wakeLock.acquire();

        } else {
            if (wakeLock == null)
                return;

            if (wakeLock.isHeld())
                wakeLock.release();
        }
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
                Utils.FOREGROUND_NOTIFY_CHAN_ID)
                .setSmallIcon(R.drawable.ic_app_notification)
                .setContentIntent(startupPendingIntent)
                .setContentTitle(getString(R.string.app_running_in_the_background))
                .setWhen(System.currentTimeMillis());

        foregroundNotify.addAction(makePauseAllAction());
        foregroundNotify.addAction(makeResumeAllAction());
        foregroundNotify.addAction(makeShutdownAction());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            foregroundNotify.setCategory(Notification.CATEGORY_PROGRESS);

        /* Disallow killing the service process by system */
        startForeground(FOREGROUND_NOTIFICATION_ID, foregroundNotify.build());
    }

    private NotificationCompat.Action makePauseAllAction()
    {
        Intent pauseButtonIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        pauseButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_PAUSE_ALL);
        PendingIntent pauseButtonPendingIntent =
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        pauseButtonIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(R.drawable.ic_pause_white_24dp,
                getString(R.string.pause_all),
                pauseButtonPendingIntent)
                .build();
    }

    private NotificationCompat.Action makeResumeAllAction()
    {
        Intent resumeButtonIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        resumeButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_RESUME_ALL);
        PendingIntent resumeButtonPendingIntent =
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        resumeButtonIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(R.drawable.ic_play_arrow_white_24dp,
                getString(R.string.resume_all),
                resumeButtonPendingIntent)
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
                R.drawable.ic_power_white_24dp,
                getString(R.string.shutdown),
                shutdownPendingIntent)
                .build();
    }

    private void makeApplyingParamsNotify()
    {
        if (!downloadsApplyingParams) {
            notifyManager.cancel(APPLYING_PARAMS_NOTIFICATION_ID);
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                Utils.DEFAULT_NOTIFY_CHAN_ID)
                .setContentTitle(getString(R.string.applying_params_title))
                .setTicker(getString(R.string.applying_params_title))
                .setContentText(getString(R.string.applying_params_for_downloads))
                .setSmallIcon(R.drawable.ic_warning_white_24dp)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_STATUS);

        notifyManager.notify(APPLYING_PARAMS_NOTIFICATION_ID, builder.build());
    }

    private void makeApplyingParamsErrorNotify(UUID id, String name, Throwable e)
    {
        String title = String.format(getString(R.string.applying_params_error_title), name);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                Utils.DEFAULT_NOTIFY_CHAN_ID)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(e.toString())
                .setSmallIcon(R.drawable.ic_error_white_24dp)
                .setAutoCancel(true)
                .setOngoing(false)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_ERROR);

        builder.addAction(makeReportAction(e));

        notifyManager.notify(id.hashCode(), builder.build());
    }

    private NotificationCompat.Action makeReportAction(Throwable e)
    {
        Intent reportIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        reportIntent.setAction(NotificationReceiver.NOTIFY_ACTION_REPORT_APPLYING_PARAMS_ERROR);
        reportIntent.putExtra(NotificationReceiver.TAG_ERR, e);
        PendingIntent shutdownPendingIntent =
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        reportIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_send_white_24dp,
                getString(R.string.report),
                shutdownPendingIntent)
                .build();
    }

    SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceListener = (sharedPreferences, key) -> {
        if (key.equals(getString(R.string.pref_key_cpu_do_not_sleep)))
            setKeepCpuAwake(sharedPreferences.getBoolean(key, SettingsManager.Default.cpuDoNotSleep));
    };
}
