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

import com.tachibana.downloader.ui.main.DownloadItem;

public class DownloadSorting extends BaseSorting
{
    public enum SortingColumns implements SortingColumnsInterface<DownloadItem>
    {
        none {
            @Override
            public int compare(DownloadItem item1, DownloadItem item2,
                               Direction direction)
            {
                return 0;
            }
        },
        name {
            @Override
            public int compare(DownloadItem item1, DownloadItem item2,
                               Direction direction)
            {
                if (direction == Direction.ASC)
                    return item1.info.fileName.compareTo(item2.info.fileName);
                else
                    return item2.info.fileName.compareTo(item1.info.fileName);
            }
        },
        size {
            @Override
            public int compare(DownloadItem item1, DownloadItem item2,
                               Direction direction)
            {
                if (direction == Direction.ASC)
                    return Long.compare(item2.info.totalBytes, item1.info.totalBytes);
                else
                    return Long.compare(item1.info.totalBytes, item2.info.totalBytes);
            }
        },
        dateAdded {
            @Override
            public int compare(DownloadItem item1, DownloadItem item2,
                               Direction direction)
            {
                if (direction == Direction.ASC)
                    return Long.compare(item2.info.dateAdded, item1.info.dateAdded);
                else
                    return Long.compare(item1.info.dateAdded, item2.info.dateAdded);
            }
        },
        category {
            @Override
            public int compare(DownloadItem item1, DownloadItem item2,
                               Direction direction)
            {
                if (direction == Direction.ASC)
                    return item1.info.mimeType.compareTo(item2.info.mimeType);
                else
                    return item2.info.mimeType.compareTo(item1.info.mimeType);
            }
        };

        public static String[] valuesToStringArray()
        {
            SortingColumns[] values = SortingColumns.class.getEnumConstants();
            String[] arr = new String[values.length];

            for (int i = 0; i < values.length; i++)
                arr[i] = values[i].toString();

            return arr;
        }

        public static SortingColumns fromValue(String value)
        {
            for (SortingColumns column : SortingColumns.class.getEnumConstants())
                if (column.toString().equalsIgnoreCase(value))
                    return column;

            return SortingColumns.none;
        }
    }

    public DownloadSorting(SortingColumns columnName, Direction direction)
    {
        super(columnName.name(), direction);
    }

    public DownloadSorting()
    {
        this(SortingColumns.name , Direction.DESC);
    }
}
