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

package com.tachibana.downloader.core.filter;

import com.tachibana.downloader.core.StatusCode;
import com.tachibana.downloader.core.utils.DateUtils;
import com.tachibana.downloader.core.utils.MimeTypeUtils;

import androidx.annotation.NonNull;

public class DownloadFilterCollection
{
    public static DownloadFilter all()
    {
        return (infoAndPieces) -> true;
    }

    public static DownloadFilter category(@NonNull MimeTypeUtils.Category category)
    {
        return (infoAndPieces) -> MimeTypeUtils.getCategory(infoAndPieces.info.mimeType).equals(category);
    }

    public static DownloadFilter statusStopped()
    {
        return (infoAndPieces) ->
                infoAndPieces.info.statusCode == StatusCode.STATUS_PAUSED ||
                        infoAndPieces.info.statusCode == StatusCode.STATUS_CANCELLED;
    }

    public static DownloadFilter statusRunning()
    {
        return (infoAndPieces) ->
                infoAndPieces.info.statusCode == StatusCode.STATUS_RUNNING ||
                        infoAndPieces.info.statusCode == StatusCode.STATUS_FETCH_METADATA;
    }

    public static DownloadFilter dateAddedToday()
    {
        return (infoAndPieces) -> {
            long dateAdded = infoAndPieces.info.dateAdded;

            return dateAdded >= DateUtils.startOfToday() && dateAdded <= DateUtils.endOfToday();
        };
    }

    public static DownloadFilter dateAddedYesterday()
    {
        return (infoAndPieces) -> {
            long dateAdded = infoAndPieces.info.dateAdded;

            return dateAdded >= DateUtils.startOfYesterday() && dateAdded <= DateUtils.endOfYesterday();
        };
    }

    public static DownloadFilter dateAddedWeek()
    {
        return (infoAndPieces) -> {
            long dateAdded = infoAndPieces.info.dateAdded;

            return dateAdded >= DateUtils.startOfWeek() && dateAdded <= DateUtils.endOfWeek();
        };
    }

    public static DownloadFilter dateAddedMonth()
    {
        return (infoAndPieces) -> {
            long dateAdded = infoAndPieces.info.dateAdded;

            return dateAdded >= DateUtils.startOfMonth() && dateAdded <= DateUtils.endOfMonth();
        };
    }

    public static DownloadFilter dateAddedYear()
    {
        return (infoAndPieces) -> {
            long dateAdded = infoAndPieces.info.dateAdded;

            return dateAdded >= DateUtils.startOfYear() && dateAdded <= DateUtils.endOfYear();
        };
    }
}
