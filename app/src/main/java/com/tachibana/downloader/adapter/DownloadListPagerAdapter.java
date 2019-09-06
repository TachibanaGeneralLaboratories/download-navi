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

package com.tachibana.downloader.adapter;

import android.content.Context;

import com.tachibana.downloader.R;
import com.tachibana.downloader.fragment.FinishedDownloadsFragment;
import com.tachibana.downloader.fragment.QueuedDownloadsFragment;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

public class DownloadListPagerAdapter extends FragmentPagerAdapter
{
    public static final int NUM_FRAGMENTS = 2;
    public static final int QUEUED_FRAG_POS = 0;
    public static final int COMPLETED_FRAG_POS = 1;

    private Context context;

    public DownloadListPagerAdapter(Context context, FragmentManager fm)
    {
        super(fm);

        this.context = context;
    }

    @NonNull
    @Override
    public Fragment getItem(int position)
    {
        /* Stubs */
        switch (position) {
            case QUEUED_FRAG_POS:
                return QueuedDownloadsFragment.newInstance();
            case COMPLETED_FRAG_POS:
                return FinishedDownloadsFragment.newInstance();
            default:
                return new Fragment();
        }
    }

    @Override
    public CharSequence getPageTitle(int position)
    {
        if (position < 0 || position >= getCount())
            return null;

        switch (position) {
            case QUEUED_FRAG_POS:
                return context.getString(R.string.fragment_title_queued);
            case COMPLETED_FRAG_POS:
                return context.getString(R.string.fragment_title_completed);
            default:
                return null;
        }
    }

    @Override
    public int getCount()
    {
        return NUM_FRAGMENTS;
    }
}
