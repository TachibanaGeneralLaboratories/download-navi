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

package com.tachibana.downloader.core.model;

/*
 * The class that represents the reason for completing the download.
 */

import static com.tachibana.downloader.core.model.data.StatusCode.STATUS_UNHANDLED_HTTP_CODE;
import static com.tachibana.downloader.core.model.data.StatusCode.STATUS_UNHANDLED_REDIRECT;

class StopRequest
{
    private String message;
    private final int finalStatus;
    private Throwable t;

    public StopRequest(int finalStatus)
    {
        this.finalStatus = finalStatus;
    }

    public StopRequest(int finalStatus, String message)
    {
        this.message = message;
        this.finalStatus = finalStatus;
    }

    public StopRequest(int finalStatus, Throwable t)
    {
        this(finalStatus, t.getMessage());
        this.t = t;
    }

    public StopRequest(int finalStatus, String message, Throwable t)
    {
        this(finalStatus, message);
        this.t = t;
    }

    public int getFinalStatus()
    {
        return finalStatus;
    }

    public String getMessage()
    {
        return message;
    }

    public Throwable getException()
    {
        return t;
    }

    public static StopRequest getUnhandledHttpError(int code, String message)
    {
        final String error = "Unhandled HTTP response: " + code + " " + message;
        if (code >= 400 && code < 600)
            return new StopRequest(code, error);
        else if (code >= 300 && code < 400)
            return new StopRequest(STATUS_UNHANDLED_REDIRECT, error);
        else
            return new StopRequest(STATUS_UNHANDLED_HTTP_CODE, error);
    }

    @Override
    public String toString()
    {
        return "StopRequest{" +
                "message='" + message + '\'' +
                ", finalStatus=" + finalStatus +
                ", t=" + t +
                '}';
    }
}
