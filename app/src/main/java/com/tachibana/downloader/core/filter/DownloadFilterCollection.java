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

import com.tachibana.downloader.core.model.data.StatusCode;
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
        return (infoAndPieces) -> StatusCode.isStatusStoppedOrPaused(infoAndPieces.info.statusCode);
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
            long timeMillis = System.currentTimeMillis();

            return dateAdded >= DateUtils.startOfToday(timeMillis) &&
                    dateAdded <= DateUtils.endOfToday(timeMillis);
        };
    }

    public static DownloadFilter dateAddedYesterday()
    {
        return (infoAndPieces) -> {
            long dateAdded = infoAndPieces.info.dateAdded;
            long timeMillis = System.currentTimeMillis();

            return dateAdded >= DateUtils.startOfYesterday(timeMillis) &&
                    dateAdded <= DateUtils.endOfYesterday(timeMillis);
        };
    }

    public static DownloadFilter dateAddedWeek()
    {
        return (infoAndPieces) -> {
            long dateAdded = infoAndPieces.info.dateAdded;
            long timeMillis = System.currentTimeMillis();

            return dateAdded >= DateUtils.startOfWeek(timeMillis) &&
                    dateAdded <= DateUtils.endOfWeek(timeMillis);
        };
    }

    public static DownloadFilter dateAddedMonth()
    {
        return (infoAndPieces) -> {
            long dateAdded = infoAndPieces.info.dateAdded;
            long timeMillis = System.currentTimeMillis();

            return dateAdded >= DateUtils.startOfMonth(timeMillis) &&
                    dateAdded <= DateUtils.endOfMonth(timeMillis);
        };
    }

    public static DownloadFilter dateAddedYear()
    {
        return (infoAndPieces) -> {
            long dateAdded = infoAndPieces.info.dateAdded;
            long timeMillis = System.currentTimeMillis();

            return dateAdded >= DateUtils.startOfYear(timeMillis) &&
                    dateAdded <= DateUtils.endOfYear(timeMillis);
        };
    }
}
