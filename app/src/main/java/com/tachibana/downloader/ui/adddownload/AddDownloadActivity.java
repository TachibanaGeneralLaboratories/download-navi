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

package com.tachibana.downloader.ui.adddownload;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.RepositoryHelper;
import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.settings.SettingsRepository;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.ui.FragmentCallback;

public class AddDownloadActivity extends AppCompatActivity
    implements FragmentCallback
{
    public static final String TAG_INIT_PARAMS = "init_params";

    private static final String TAG_DOWNLOAD_DIALOG = "add_download_dialog";

    private AddDownloadDialog addDownloadDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        setTheme(Utils.getTranslucentAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

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
