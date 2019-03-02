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
import android.text.TextUtils;
import android.util.Log;

import com.tachibana.downloader.MainApplication;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.exception.FileAlreadyExistsException;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.utils.FileUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.NonNull;
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
    private CompositeDisposable disposables = new CompositeDisposable();
    private HashMap<UUID, DownloadThread> tasks = new HashMap<>();
    private ArrayList<DownloadEngineListener> listeners = new ArrayList<>();
    private HashMap<UUID, ChangeableParams> duringChange = new HashMap<>();

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
        repo = ((MainApplication) appContext).getRepository();
    }

    public void addListener(DownloadEngineListener listener)
    {
        listeners.add(listener);
    }

    public void removeListener(DownloadEngineListener listener)
    {
        listeners.remove(listener);
    }

    public synchronized void runDownload(@NonNull UUID id)
    {
        if (duringChange.containsKey(id))
            return;

        DownloadThread task = tasks.get(id);
        if (task != null && task.isRunning())
            return;

        task = new DownloadThread(appContext, repo, id);
        tasks.put(id, task);
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

    public synchronized void pauseResumeDownload(@NonNull UUID id)
    {
        if (duringChange.containsKey(id))
            return;

        disposables.add(repo.getInfoByIdSingle(id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .filter((info) -> info != null)
                .subscribe((info) -> {
                            if (StatusCode.isStatusStoppedOrPaused(info.statusCode)) {
                                DownloadHelper.scheduleDownloading(appContext, info);
                            } else {
                                DownloadThread task = tasks.get(id);
                                if (task != null)
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

    public synchronized void cancelDownload(@NonNull UUID id)
    {
        if (duringChange.containsKey(id))
            return;

        disposables.add(repo.getInfoByIdSingle(id)
                .subscribeOn(Schedulers.io())
                .filter((info) -> info != null)
                .subscribe((info) -> {
                            repo.deleteInfo(appContext, info, true);
                            DownloadThread task = tasks.get(id);
                            if (task != null)
                                task.requestStop();
                        },
                        (Throwable t) -> {
                            Log.e(TAG, "Getting info " + id + " error: " +
                                    Log.getStackTraceString(t));
                            if (checkNoDownloads())
                                notifyListeners(DownloadEngineListener::onDownloadsCompleted);
                        }
                )
        );
    }

    public synchronized void pauseAllDownloads()
    {
        for (Map.Entry<UUID, DownloadThread> entry : tasks.entrySet()) {
            if (duringChange.containsKey(entry.getKey()))
                continue;
            DownloadThread task = entry.getValue();
            if (task == null)
                continue;
            task.requestPause();
        }
    }

    public synchronized void resumeAllDownloads()
    {
        disposables.add(repo.getAllInfoSingle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .filter((list) -> !list.isEmpty())
                .flattenAsObservable((it) -> it)
                .filter((info) -> {
                    return info != null && StatusCode.isStatusStoppedOrPaused(info.statusCode);
                })
                .subscribe((info) -> {
                            if (duringChange.containsKey(info.id))
                                return;
                            DownloadThread task = tasks.get(info.id);
                            if (task != null && task.isRunning())
                                return;
                            DownloadHelper.scheduleDownloading(appContext, info);
                        },
                        (Throwable t) -> {
                            Log.e(TAG, "Getting info list error: " + Log.getStackTraceString(t));
                            if (checkNoDownloads())
                                notifyListeners(DownloadEngineListener::onDownloadsCompleted);
                        })
        );
    }

    public synchronized void stopAllDownloads()
    {
        for (Map.Entry<UUID, DownloadThread> entry : tasks.entrySet()) {
            if (duringChange.containsKey(entry.getKey()))
                continue;
            DownloadThread task = entry.getValue();
            if (task != null)
                task.requestStop();
        }
    }

    public boolean hasDownloads()
    {
        return !tasks.isEmpty();
    }

    public synchronized void changeParams(@NonNull UUID id,
                                          @NonNull ChangeableParams params)
    {
        if (duringChange.containsKey(id))
            return;

        duringChange.put(id, params);

        DownloadThread task = tasks.get(id);
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
                                applyParams(info, params);

                            } catch (Throwable e) {
                                err[0] = e;
                            } finally {
                                duringChange.remove(id);
                                notifyListeners((listener) -> listener.onParamsApplied(id, err[0]));
                                if (runAfter) {
                                    info = repo.getInfoById(id);
                                    if (info != null)
                                        DownloadHelper.scheduleDownloading(appContext, info);
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

    private void applyParams(DownloadInfo info, ChangeableParams params)
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
        if (params.wifiOnly != null) {
            changed = true;
            info.wifiOnly = params.wifiOnly;
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
        return tasks.isEmpty();
    }

    private void observeDownloadResult(DownloadResult result)
    {
        if (result == null)
            return;

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
        tasks.remove(id);

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
            case StatusCode.STATUS_WAITING_TO_RETRY:
            case StatusCode.STATUS_WAITING_FOR_NETWORK:
                DownloadHelper.scheduleDownloading(appContext, info);
                break;
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                /* TODO: request authorization from user */
                break;
            case HttpURLConnection.HTTP_PROXY_AUTH:
                /* TODO: proxy support */
                break;
        }
    }

    private void onCancelled(UUID id)
    {
        tasks.remove(id);
        ChangeableParams params = duringChange.get(id);
        if (params == null) {
            if (checkNoDownloads())
                notifyListeners(DownloadEngineListener::onDownloadsCompleted);
        } else {
            applyParams(id, params, true);
        }
    }
}
