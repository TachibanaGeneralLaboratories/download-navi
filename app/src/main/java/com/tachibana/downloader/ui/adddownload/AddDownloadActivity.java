/*
 * Copyright (C) 2018-2022 Tachibana General Laboratories, LLC
 * Copyright (C) 2018-2022 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.ui.adddownload;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.RepositoryHelper;
import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.settings.SettingsRepository;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.ui.BaseAlertDialog;
import com.tachibana.downloader.ui.BatteryOptimizationDialog;
import com.tachibana.downloader.ui.FragmentCallback;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class AddDownloadActivity extends AppCompatActivity
    implements FragmentCallback
{
    public static final String TAG_INIT_PARAMS = "init_params";

    private static final String TAG_DOWNLOAD_DIALOG = "add_download_dialog";
    private static final String TAG_BATTERY_DIALOG = "battery_dialog";

    private AddDownloadDialog addDownloadDialog;
    private BatteryOptimizationDialog batteryDialog;
    private CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private SettingsRepository pref;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        setTheme(Utils.getTranslucentAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        pref = RepositoryHelper.getSettingsRepository(getApplicationContext());
        ViewModelProvider provider = new ViewModelProvider(this);
        dialogViewModel = provider.get(BaseAlertDialog.SharedViewModel.class);

        FragmentManager fm = getSupportFragmentManager();
        addDownloadDialog = (AddDownloadDialog)fm.findFragmentByTag(TAG_DOWNLOAD_DIALOG);
        if (addDownloadDialog == null) {
            AddInitParams initParams = null;
            Intent i = getIntent();
            if (i != null)
                initParams = i.getParcelableExtra(TAG_INIT_PARAMS);
            if (initParams == null) {
                initParams = new AddInitParams();
            }
            fillInitParams(initParams);
            addDownloadDialog = AddDownloadDialog.newInstance(initParams);
            addDownloadDialog.show(fm, TAG_DOWNLOAD_DIALOG);
        }
        batteryDialog = (BatteryOptimizationDialog)fm.findFragmentByTag(TAG_BATTERY_DIALOG);
        if (Utils.shouldShowBatteryOptimizationDialog(this)) {
            showBatteryOptimizationDialog();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        subscribeAlertDialog();
    }

    @Override
    protected void onStop() {
        super.onStop();

        disposables.clear();
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (event.dialogTag == null) {
                        return;
                    }
                    if (event.dialogTag.equals(TAG_BATTERY_DIALOG)) {
                        if (event.type != BaseAlertDialog.EventType.DIALOG_SHOWN) {
                            batteryDialog.dismiss();
                            pref.askDisableBatteryOptimization(false);
                        }
                        if (event.type == BaseAlertDialog.EventType.POSITIVE_BUTTON_CLICKED) {
                            Utils.requestDisableBatteryOptimization(this);
                        }
                    }
                });
        disposables.add(d);
    }

    private void fillInitParams(AddInitParams params)
    {
        SettingsRepository pref = RepositoryHelper.getSettingsRepository(getApplicationContext());
        SharedPreferences localPref = PreferenceManager.getDefaultSharedPreferences(this);

        if (params.url == null) {
            params.url = getUrlFromIntent();
        }
        if (params.dirPath == null) {
            params.dirPath = Uri.parse(pref.saveDownloadsIn());
        }
        if (params.retry == null) {
            params.retry = localPref.getBoolean(
                    getString(R.string.add_download_retry_flag),
                    true
            );
        }
        if (params.replaceFile == null) {
            params.replaceFile = localPref.getBoolean(
                    getString(R.string.add_download_replace_file_flag),
                    false
            );
        }
        if (params.unmeteredConnectionsOnly == null) {
            params.unmeteredConnectionsOnly = localPref.getBoolean(
                    getString(R.string.add_download_unmetered_only_flag),
                    false
            );
        }
        if (params.numPieces == null) {
            params.numPieces = localPref.getInt(
                    getString(R.string.add_download_num_pieces),
                    DownloadInfo.MIN_PIECES
            );
        }
        if (params.uncompressArchive == null) {
            params.uncompressArchive = localPref.getBoolean(
                    getString(R.string.add_download_uncompress_archive_flag),
                    false
            );
        }
    }

    private String getUrlFromIntent()
    {
        Intent i = getIntent();
        if (i != null) {
            if (i.getData() != null)
                return i.getData().toString();
            else
                return i.getStringExtra(Intent.EXTRA_TEXT);
        }

        return null;
    }

    private void showBatteryOptimizationDialog() {
        var fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(TAG_BATTERY_DIALOG) == null) {
            batteryDialog = BatteryOptimizationDialog.newInstance();
            var ft = fm.beginTransaction();
            ft.add(batteryDialog, TAG_BATTERY_DIALOG);
            ft.commitAllowingStateLoss();
        }
    }

    @Override
    public void fragmentFinished(Intent intent, ResultCode code)
    {
        finish();
    }

    @Override
    public void onBackPressed()
    {
        addDownloadDialog.onBackPressed();
    }
}
