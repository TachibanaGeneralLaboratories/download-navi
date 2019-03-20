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

package com.tachibana.downloader.fragment;

import android.os.Bundle;

import com.tachibana.downloader.adapter.DownloadItem;
import com.tachibana.downloader.adapter.DownloadListAdapter;
import com.tachibana.downloader.core.StatusCode;
import com.tachibana.downloader.dialog.DownloadDetailsDialog;

import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import io.reactivex.schedulers.Schedulers;

public class QueuedDownloadsFragment extends DownloadsFragment
    implements DownloadListAdapter.QueueClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = QueuedDownloadsFragment.class.getSimpleName();

    public static QueuedDownloadsFragment newInstance()
    {
        QueuedDownloadsFragment fragment = new QueuedDownloadsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);

        return fragment;
    }

    public QueuedDownloadsFragment()
    {
        super((item) -> !StatusCode.isStatusCompleted(item.info.statusCode));
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAdapter();
    }

    @Override
    public void onItemClicked(@NonNull DownloadItem item)
    {
        showDetailsDialog(item.info.id);
    }

    @Override
    public void onItemPauseClicked(@NonNull DownloadItem item)
    {
        viewModel.pauseResumeDownload(item.info);
    }

    @Override
    public void onItemCancelClicked(@NonNull DownloadItem item)
    {
        viewModel.deleteDownload(item.info, true);
    }
}
