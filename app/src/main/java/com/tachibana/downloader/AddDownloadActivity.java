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
import android.os.Bundle;

import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.dialog.AddDownloadDialog;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

public class AddDownloadActivity extends AppCompatActivity
    implements FragmentCallback
{
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
            String url = null;
            Intent i = getIntent();
            if (i != null && i.getData() != null)
                url = i.getData().toString();
            addDownloadDialog = AddDownloadDialog.newInstance(url);
            addDownloadDialog.show(fm, TAG_DOWNLOAD_DIALOG);
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
