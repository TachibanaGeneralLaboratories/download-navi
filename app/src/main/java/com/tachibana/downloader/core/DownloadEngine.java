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
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.tachibana.downloader.MainApplication;
import com.tachibana.downloader.R;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.exception.FileAlreadyExistsException;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.utils.FileUtils;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.receiver.ConnectionReceiver;
import com.tachibana.downloader.receiver.PowerReceiver;
import com.tachibana.downloader.service.DownloadService;
import com.tachibana.downloader.settings.SettingsManager;
import com.tachibana.downloader.worker.DeleteDownloadsWorker;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class DownloadEngine
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadEngine.class.getSimpleName();

    private Context appContext;
    private DataRepository repo;
    private SharedPreferences pref;
    private CompositeDisposable disposables = new CompositeDisposable();
    private HashMap<UUID, DownloadThread> activeDownloads = new HashMap<>();
    private ArrayList<DownloadEngineListener> listeners = new ArrayList<>();
    private HashMap<UUID, ChangeableParams> duringChange = new HashMap<>();

    private PowerReceiver powerReceiver = new PowerReceiver();
    private ConnectionReceiver connectionReceiver = new ConnectionReceiver();

    private static DownloadEngine INSTANCE;

    public static DownloadEngine getInstance(@NonNull Context appContext)
    {
        if (INSTANCE == null) {
            synchronized (DownloadEngine.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DownloadEngine(appContext);
                }
            }
        }

        return INSTANCE;
    }

    private DownloadEngine(Context appContext)
    {
        this.appContext = appContext;
        repo = ((MainApplication)appContext).getRepository();
        pref = SettingsManager.getInstance(appContext).getPreferences();
        switchConnectionReceiver();
        switchPowerReceiver();
        pref.registerOnSharedPreferenceChangeListener(sharedPrefListener);
    }

    public void addListener(DownloadEngineListener listener)
    {
        listeners.add(listener);
    }

    public void removeListener(DownloadEngineListener listener)
    {
        listeners.remove(listener);
    }

    public void runDownload(@NonNull DownloadInfo info)
    {
        DownloadScheduler.run(appContext, info);
    }

    public void reschedulePendingDownloads()
    {
        DownloadScheduler.rescheduleAll();
    }

    /*
     * Exclude pending downloads
     */

    public void rescheduleDownloads()
    {
        if (checkStopDownloads())
            stopDownloads();
        else
            resumeStoppedDownloads();
    }

    public void pauseResumeDownload(@NonNull UUID id)
    {
        disposables.add(repo.getInfoByIdSingle(id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .filter((info) -> info != null)
                .subscribe((info) -> {
                            if (StatusCode.isStatusStoppedOrPaused(info.statusCode)) {
                                runDownload(info);
                            } else {
                                DownloadThread task = activeDownloads.get(id);
                                if (task != null && !duringChange.containsKey(id))
                                    task.requestPause();
                            }
                        },
                        (Throwable t) -> {
                            Log.e(TAG, "Getting info " + id + " error: " +
                                    Log.getStackTraceString(t));
                            if (checkNoDownloads())
                                notifyListeners(DownloadEngineListener::onDownloadsCompleted);
                        })
        );
    }

    public synchronized void pauseAllDownloads()
    {
        for (Map.Entry<UUID, DownloadThread> entry : activeDownloads.entrySet()) {
            if (duringChange.containsKey(entry.getKey()))
                continue;
            DownloadThread task = entry.getValue();
            if (task == null)
                continue;
            task.requestPause();
        }
    }

    public void resumeDownloads()
    {
        DownloadScheduler.runAll(false);
    }

    public void resumeStoppedDownloads()
    {
        DownloadScheduler.runAll(true);
    }

    public synchronized void stopDownloads()
    {
        for (Map.Entry<UUID, DownloadThread> entry : activeDownloads.entrySet()) {
            if (duringChange.containsKey(entry.getKey()))
                continue;
            DownloadThread task = entry.getValue();
            if (task != null)
                task.requestStop();
        }
    }

    public void deleteDownloads(boolean withFile, @NonNull UUID... idList)
    {
        String[] strIdList = new String[idList.length];
        for (int i = 0; i < idList.length; i++) {
            if (idList[i] != null)
                strIdList[i] = idList[i].toString();
        }

        runDeleteDownloadsWorker(strIdList, withFile);
    }

    public void deleteDownloads(boolean withFile, @NonNull DownloadInfo... infoList)
    {
        String[] strIdList = new String[infoList.length];
        for (int i = 0; i < infoList.length; i++) {
            if (infoList[i] != null)
                strIdList[i] = infoList[i].id.toString();
        }

        runDeleteDownloadsWorker(strIdList, withFile);
    }

    public boolean hasActiveDownloads()
    {
        return !activeDownloads.isEmpty();
    }

    public void changeParams(@NonNull UUID id,
                             @NonNull ChangeableParams params)
    {
        Intent i = new Intent(appContext, DownloadService.class);
        i.setAction(DownloadService.ACTION_CHANGE_PARAMS);
        i.putExtra(DownloadService.TAG_DOWNLOAD_ID, id);
        i.putExtra(DownloadService.TAG_PARAMS, params);

        appContext.startService(i);
    }

    /*
     * Do not call directly
     */

    public synchronized void doRunDownload(@NonNull UUID id)
    {
        if (duringChange.containsKey(id))
            return;

        if (isMaxActiveDownloads()) {
            DownloadScheduler.pushIntoQueue(id);
            return;
        }

        DownloadThread task = activeDownloads.get(id);
        if (task != null && task.isRunning())
            return;

        task = new DownloadThread(appContext, id);
        activeDownloads.put(id, task);
        disposables.add(Observable.fromCallable(task)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::observeDownloadResult,
                        (Throwable t) -> {
                            Log.e(TAG, Log.getStackTraceString(t));
                            if (checkNoDownloads())
                                notifyListeners(DownloadEngineListener::onDownloadsCompleted);
                        }
                )
        );
    }

    /*
     * Do not call directly
     */

    public synchronized void doDeleteDownload(@NonNull DownloadInfo info, boolean withFile)
    {
        if (duringChange.containsKey(info.id))
            return;

        DownloadScheduler.undone(info);
        repo.deleteInfo(appContext, info, withFile);

        DownloadThread task = activeDownloads.get(info.id);
        if (task != null)
            task.requestStop();
        else if (checkNoDownloads())
            notifyListeners(DownloadEngineListener::onDownloadsCompleted);
    }

    private void runDeleteDownloadsWorker(String[] idList, boolean withFile)
    {
        Data data = new Data.Builder()
                .putStringArray(DeleteDownloadsWorker.TAG_ID_LIST, idList)
                .putBoolean(DeleteDownloadsWorker.TAG_WITH_FILE, withFile)
                .build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(DeleteDownloadsWorker.class)
                .setInputData(data)
                .build();
        WorkManager.getInstance().enqueue(work);
    }

    /*
     * Do not call directly
     */

    public synchronized void doChangeParams(@NonNull UUID id,
                                            @NonNull ChangeableParams params)
    {
        if (duringChange.containsKey(id))
            return;

        duringChange.put(id, params);
        notifyListeners((listener) -> listener.onApplyingParams(id));

        DownloadThread task = activeDownloads.get(id);
        if (task != null && task.isRunning())
            task.requestStop();
        else
            applyParams(id, params, false);
    }

    private void applyParams(UUID id, ChangeableParams params, boolean runAfter)
    {
        disposables.add(repo.getInfoByIdSingle(id)
                .subscribeOn(Schedulers.io())
                .subscribe((info) -> {
                            Throwable[] err = new Throwable[1];
                            try {
                                if (info == null)
                                    throw new NullPointerException();
                                doApplyParams(info, params);

                            } catch (Throwable e) {
                                err[0] = e;
                            } finally {
                                duringChange.remove(id);
                                notifyListeners((listener) -> listener.onParamsApplied(id, err[0]));
                                if (runAfter) {
                                    info = repo.getInfoById(id);
                                    if (info != null)
                                        runDownload(info);
                                }
                            }
                        },
                        (Throwable t) -> {
                            Log.e(TAG, "Getting info " + id + " error: " +
                                    Log.getStackTraceString(t));
                            duringChange.remove(id);
                            notifyListeners((listener) -> listener.onParamsApplied(id, t));
                        }
                )
        );
    }

    private void doApplyParams(DownloadInfo info, ChangeableParams params)
    {
        boolean changed = false;
        if (!TextUtils.isEmpty(params.url)) {
            changed = true;
            info.url = params.url;
        }
        if (!TextUtils.isEmpty(params.description)) {
            changed = true;
            info.description = params.description;
        }
        if (params.unmeteredConnectionsOnly != null) {
            changed = true;
            info.unmeteredConnectionsOnly = params.unmeteredConnectionsOnly;
        }
        if (params.retry != null) {
            changed = true;
            info.retry = params.retry;
        }

        Exception err = null;
        boolean nameChanged = !TextUtils.isEmpty(params.fileName);
        boolean dirChanged = params.dirPath != null;
        if (nameChanged || dirChanged) {
            changed = true;
            try {
                FileUtils.moveFile(appContext,
                        info.dirPath, info.fileName,
                        (dirChanged ? params.dirPath : info.dirPath),
                        (nameChanged ? params.fileName : info.fileName),
                        info.mimeType,
                        true);

            } catch (IOException | FileAlreadyExistsException e) {
                err = new Exception(e);
            }

            if (err == null) {
                if (nameChanged)
                    info.fileName = params.fileName;
                if (dirChanged)
                    info.dirPath = params.dirPath;
            }
        }

        if (changed)
            repo.updateInfo(appContext, info, true, false);
    }

    private interface CallListener
    {
        void apply(DownloadEngineListener listener);
    }

    private void notifyListeners(@NonNull CallListener l)
    {
        for (DownloadEngineListener listener : listeners) {
            if (listener != null)
                l.apply(listener);
        }
    }

    private boolean checkNoDownloads()
    {
        return activeDownloads.isEmpty();
    }

    private void observeDownloadResult(DownloadResult result)
    {
        if (result == null)
            return;

        activeDownloads.remove(result.infoId);
        scheduleWaitingDownload();

        switch (result.status) {
            case FINISHED:
                onFinished(result.infoId);
                break;
            case PAUSED:
            case STOPPED:
                onCancelled(result.infoId);
                break;
        }
    }

    private void onFinished(UUID id)
    {
        disposables.add(repo.getInfoByIdSingle(id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .filter((info) -> info != null)
                .subscribe((info) -> {
                            handleInfoStatus(info);
                            if (checkNoDownloads())
                                notifyListeners(DownloadEngineListener::onDownloadsCompleted);
                        },
                        (Throwable t) -> {
                            Log.e(TAG, "Getting info " + id + " error: " +
                                    Log.getStackTraceString(t));
                            if (checkNoDownloads())
                                notifyListeners(DownloadEngineListener::onDownloadsCompleted);
                        })
        );
    }

    private void handleInfoStatus(DownloadInfo info)
    {
        if (info == null)
            return;

        switch (info.statusCode) {
            case StatusCode.STATUS_SUCCESS:
                checkMoveAfterDownload(info);
                break;
            case StatusCode.STATUS_WAITING_TO_RETRY:
            case StatusCode.STATUS_WAITING_FOR_NETWORK:
                runDownload(info);
                break;
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                /* TODO: request authorization from user */
                break;
            case HttpURLConnection.HTTP_PROXY_AUTH:
                /* TODO: proxy support */
                break;
        }
    }

    private void checkMoveAfterDownload(DownloadInfo info)
    {
        if (!pref.getBoolean(appContext.getString(R.string.pref_key_move_after_download),
                             SettingsManager.Default.moveAfterDownload))
            return;

        Uri movePath = Uri.parse(pref.getString(appContext.getString(R.string.pref_key_move_after_download_in),
                                                SettingsManager.Default.moveAfterDownloadIn));
        if (movePath == null)
            return;

        ChangeableParams params = new ChangeableParams();
        params.dirPath = movePath;
        changeParams(info.id, params);
    }

    private void onCancelled(UUID id)
    {
        ChangeableParams params = duringChange.get(id);
        if (params == null) {
            if (checkNoDownloads())
                notifyListeners(DownloadEngineListener::onDownloadsCompleted);
        } else {
            applyParams(id, params, true);
        }
    }

    private boolean isMaxActiveDownloads()
    {
        return activeDownloads.size() == pref.getInt(appContext.getString(R.string.pref_key_max_active_downloads),
                                                     SettingsManager.Default.maxActiveDownloads);
    }

    private void scheduleWaitingDownload()
    {
        if (!isMaxActiveDownloads())
            DownloadScheduler.runFromQueue();
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener sharedPrefListener = (sharedPreferences, key) -> {
        boolean reschedule = false;

        if (key.equals(appContext.getString(R.string.pref_key_umnetered_connections_only)) ||
            key.equals(appContext.getString(R.string.pref_key_enable_roaming))) {
            reschedule = true;
            switchConnectionReceiver();

        } else if (key.equals(appContext.getString(R.string.pref_key_download_only_when_charging)) ||
                   key.equals(appContext.getString(R.string.pref_key_battery_control))) {
            reschedule = true;
            switchPowerReceiver();

        } else if (key.equals(appContext.getString(R.string.pref_key_custom_battery_control))) {
            switchPowerReceiver();
        }

        if (reschedule) {
            reschedulePendingDownloads();
            rescheduleDownloads();
        }
    };

    private void switchPowerReceiver()
    {
        boolean batteryControl = pref.getBoolean(appContext.getString(R.string.pref_key_battery_control),
                                                 SettingsManager.Default.batteryControl);
        boolean customBatteryControl = pref.getBoolean(appContext.getString(R.string.pref_key_custom_battery_control),
                                                       SettingsManager.Default.customBatteryControl);
        boolean onlyCharging = pref.getBoolean(appContext.getString(R.string.pref_key_download_only_when_charging),
                                               SettingsManager.Default.onlyCharging);

        try {
            appContext.unregisterReceiver(powerReceiver);

        } catch (IllegalArgumentException e) {
            /* Ignore non-registered receiver */
        }
        if (customBatteryControl)
            appContext.registerReceiver(powerReceiver, PowerReceiver.getCustomFilter());
        else if (batteryControl || onlyCharging)
            appContext.registerReceiver(powerReceiver, PowerReceiver.getFilter());
    }

    private void switchConnectionReceiver()
    {
        boolean unmeteredOnly = pref.getBoolean(appContext.getString(R.string.pref_key_umnetered_connections_only),
                                                SettingsManager.Default.unmeteredConnectionsOnly);
        boolean roaming = pref.getBoolean(appContext.getString(R.string.pref_key_enable_roaming),
                                          SettingsManager.Default.enableRoaming);

        try {
            appContext.unregisterReceiver(connectionReceiver);

        } catch (IllegalArgumentException e) {
            /* Ignore non-registered receiver */
        }
        if (unmeteredOnly || roaming)
            appContext.registerReceiver(connectionReceiver, ConnectionReceiver.getFilter());
    }

    private boolean checkStopDownloads()
    {
        boolean batteryControl = pref.getBoolean(appContext.getString(R.string.pref_key_battery_control),
                                                 SettingsManager.Default.batteryControl);
        boolean customBatteryControl = pref.getBoolean(appContext.getString(R.string.pref_key_custom_battery_control),
                                                       SettingsManager.Default.customBatteryControl);
        int customBatteryControlValue = pref.getInt(appContext.getString(R.string.pref_key_custom_battery_control_value),
                                                    Utils.getDefaultBatteryLowLevel());
        boolean onlyCharging = pref.getBoolean(appContext.getString(R.string.pref_key_download_only_when_charging),
                                               SettingsManager.Default.onlyCharging);
        boolean unmeteredOnly = pref.getBoolean(appContext.getString(R.string.pref_key_umnetered_connections_only),
                                                SettingsManager.Default.unmeteredConnectionsOnly);
        boolean roaming = pref.getBoolean(appContext.getString(R.string.pref_key_enable_roaming),
                                          SettingsManager.Default.enableRoaming);

        boolean stop = false;
        if (roaming)
            stop = Utils.isRoaming(appContext);
        if (unmeteredOnly)
            stop = Utils.isMetered(appContext);
        if (onlyCharging)
            stop |= !Utils.isBatteryCharging(appContext);
        if (customBatteryControl)
            stop |= Utils.isBatteryBelowThreshold(appContext, customBatteryControlValue);
        else if (batteryControl)
            stop |= Utils.isBatteryLow(appContext);

        return stop;
    }
}
