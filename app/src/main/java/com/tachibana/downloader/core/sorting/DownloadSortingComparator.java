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

package com.tachibana.downloader.core.sorting;

import androidx.annotation.NonNull;

import com.tachibana.downloader.ui.main.DownloadItem;

import java.util.Comparator;

public class DownloadSortingComparator implements Comparator<DownloadItem>
{
    private DownloadSorting sorting;

    public DownloadSortingComparator(@NonNull DownloadSorting sorting)
    {
        this.sorting = sorting;
    }

    public DownloadSorting getSorting()
    {
        return sorting;
    }

    @Override
    public int compare(DownloadItem o1, DownloadItem o2)
    {
        return DownloadSorting.SortingColumns.fromValue(sorting.getColumnName())
                .compare(o1, o2, sorting.getDirection());
    }
}
