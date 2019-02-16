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

package com.tachibana.downloader.core;

public class StatusCode
{
    /*
     * Lists the states that the download task can set on a download
     * to notify application of the download progress.
     * Status codes are based on DownloadManager system.
     * The codes follow the HTTP families:
     * 1xx: informational
     * 2xx: success
     * 3xx: redirects (not used)
     * 4xx: client errors
     * 5xx: server errors
     */
    /* This download hasn't started yet */
    public static final int STATUS_PENDING = 190;
    /* This download has started */
    public static final int STATUS_RUNNING = 192;
    /*
     * Download was added, but missing some metadata
     * (e.g. MIME-type, size, headers, etc)
     */
    public static final int STATUS_FETCH_METADATA = 193;
    /* This download encountered some network error and is waiting before retrying the request */
    public static final int STATUS_WAITING_TO_RETRY = 194;
    /* This download is waiting for network connectivity to proceed */
    public static final int STATUS_WAITING_FOR_NETWORK = 195;
    public static final int STATUS_PAUSED = 197;
    /* This download was cancelled */
    public static final int STATUS_STOPPED = 198;
    /*
     * This download has successfully completed.
     * Warning: there might be other status values that indicate success
     * in the future.
     * Use isStatusSuccess() to capture the entire category
     */
    public static final int STATUS_SUCCESS = 200;
    /*
     * This request couldn't be parsed. This is also used when processing
     * requests with unknown/unsupported URI schemes
     */
    public static final int STATUS_BAD_REQUEST = 400;
    /* Some possibly transient error occurred, but we can't resume the download */
    public static final int STATUS_CANNOT_RESUME = 489;
    /*
     * This download has completed with an error.
     * Warning: there will be other status values that indicate errors in
     * the future. Use isStatusError() to capture the entire category
     */
    public static final int STATUS_UNKNOWN_ERROR = 491;
    /*
     * This download couldn't be completed because of a storage issue.
     * Typically, that's because the filesystem is missing or full
     */
    public static final int STATUS_FILE_ERROR = 492;
    /*
     * This download couldn't be completed because of an HTTP
     * redirect response that the download manager couldn't
     * handle
     */
    public static final int STATUS_UNHANDLED_REDIRECT = 493;
    /*
     * This download couldn't be completed because of an
     * unspecified unhandled HTTP code.
     */
    public static final int STATUS_UNHANDLED_HTTP_CODE = 494;
    /*
     * This download couldn't be completed because of an
     * error receiving or processing data at the HTTP level
     */
    public static final int STATUS_HTTP_DATA_ERROR = 495;
    /*
     * This download couldn't be completed because there were
     * too many redirects.
     */
    public static final int STATUS_TOO_MANY_REDIRECTS = 497;
    /*
     * This download couldn't be completed due to insufficient storage
     * space. Typically, this is because the SD card is full
     */
    public static final int STATUS_INSUFFICIENT_SPACE_ERROR = 498;

    /*
     * Returns whether the status is informational (i.e. 1xx)
     */

    public static boolean isStatusInformational(int statusCode)
    {
        return statusCode >= 100 && statusCode < 200;
    }

    /*
     * Returns whether the status is a success (i.e. 2xx)
     */

    public static boolean isStatusSuccess(int statusCode)
    {
        return statusCode >= 200 && statusCode < 300;
    }

    /*
     * Returns whether the status is an error (i.e. 4xx or 5xx).
     */

    public static boolean isStatusError(int statusCode)
    {
        return statusCode >= 400 && statusCode < 600;
    }

    /*
     * Returns whether the status is a client error (i.e. 4xx)
     */

    public static boolean isStatusClientError(int statusCode)
    {
        return statusCode >= 400 && statusCode < 500;
    }

    /*
     * Returns whether the status is a server error (i.e. 5xx)
     */

    public static boolean isStatusServerError(int statusCode)
    {
        return statusCode >= 500 && statusCode < 600;
    }

    /*
     * Returns whether the download has completed (either with success or
     * error)
     */

    public static boolean isStatusCompleted(int statusCode)
    {
        return statusCode >= 200 && statusCode < 300 || statusCode >= 400 && statusCode < 600;
    }

    public static boolean isStatusStoppedOrPaused(int statusCode)
    {
        return statusCode == STATUS_PAUSED || statusCode == STATUS_STOPPED;
    }
}
