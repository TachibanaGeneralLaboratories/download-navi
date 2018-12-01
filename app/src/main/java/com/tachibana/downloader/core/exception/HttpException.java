/*
 * Copyright (C) 2018 Tachibana General Laboratories, LLC
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.core.exception;

public class HttpException extends Exception
{
    private final int responseCode;

    public HttpException(String message)
    {
        this(message, 0);
    }

    public HttpException(String message, int responseCode)
    {
        super(message);
        this.responseCode = responseCode;
    }

    public HttpException(String message, int responseCode, Exception e)
    {
        this(message, responseCode);
        initCause(e);
    }

    public int getResponseCode()
    {
        return responseCode;
    }
}