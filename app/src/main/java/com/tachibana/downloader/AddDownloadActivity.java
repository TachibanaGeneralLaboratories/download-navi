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

package com.tachibana.downloader;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.dialog.AddDownloadDialog;
import com.tachibana.downloader.settings.SettingsManager;
import com.tachibana.downloader.viewmodel.AddInitParams;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

public class AddDownloadActivity extends AppCompatActivity
    implements FragmentCallback
{
    public static final String TAG_INIT_PARAMS = "init_params";

    private static final String TAG_DOWNLOAD_DIALOG = "add_download_dialog";

    AddDownloadDialog addDownloadDialog;

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
            if (initParams == null)
                initParams = makeInitParams();

            addDownloadDialog = AddDownloadDialog.newInstance(initParams);
            addDownloadDialog.show(fm, TAG_DOWNLOAD_DIALOG);
        }
    }

    private AddInitParams makeInitParams()
    {
        SharedPreferences pref = SettingsManager.getInstance(getApplicationContext()).getPreferences();

        AddInitParams initParams = new AddInitParams();
        initParams.url = getUrlFromIntent();
        String path = pref.getString(getString(R.string.pref_key_last_download_dir_uri),
                SettingsManager.Default.lastDownloadDirUri);
        initParams.dirPath = Uri.parse(path);

        return initParams;
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
