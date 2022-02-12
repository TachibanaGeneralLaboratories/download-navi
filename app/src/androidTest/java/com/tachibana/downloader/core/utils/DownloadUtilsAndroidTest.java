/*
 * Copyright (C) 2019-2022 Tachibana General Laboratories, LLC
 * Copyright (C) 2019-2022 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.core.utils;

import com.tachibana.downloader.AbstractTest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DownloadUtilsAndroidTest extends AbstractTest
{
    @Test
    public void testGetHttpFileName()
    {
        String actual = DownloadUtils.getHttpFileName(fs, "http://example.org/file.txt", null, null, null);
        assertEquals("file.txt", actual);
        actual = DownloadUtils.getHttpFileName(fs, "http://example.org/file.txt?foo=bar", null, null, null);
        assertEquals("file.txt", actual);
    }

    @Test
    public void testGetHttpFileName_noExtension()
    {
        String actual = DownloadUtils.getHttpFileName(fs, "http://example.org/file", null, null, null);
        assertEquals("file.bin", actual);
        actual = DownloadUtils.getHttpFileName(fs, "http://example.org/file", null, null, "image/jpeg");
        assertEquals("file.jpg", actual);
        actual = DownloadUtils.getHttpFileName(fs, "http://example.org/file", null, null, "application/octet-stream");
        assertEquals("file.bin", actual);
    }

    @Test
    public void testGetHttpFileName_withDisposition()
    {
        String actual = DownloadUtils.getHttpFileName(fs, "http://example.org/file.txt",
                "attachment; filename=\"subdir/real.pdf\"", null, null);
        assertEquals("real.pdf", actual);
    }

    @Test
    public void testGetHttpFileName_withLocation()
    {
        String actual = DownloadUtils.getHttpFileName(fs, "http://example.org/file.txt",
                null, "Content-Location: subdir/real.pdf", null);
        assertEquals("real.pdf", actual);
    }

    @Test
    public void testGetHttpFileName_withDispositionAndLocation()
    {
        String actual = DownloadUtils.getHttpFileName(fs, "http://example.org/file.txt",
                "attachment; filename=\"subdir/real.pdf\"",
                "Content-Location: subdir/file.pdf", null);
        assertEquals("real.pdf", actual);
    }

    @Test
    public void testGetHttpFileName_dispositionWithEncoding()
    {
        String actual = DownloadUtils.getHttpFileName(fs, "http://example.org/file.pdf",
                "attachment;filename=\"foo.txt\";filename*=UTF-8''foo.txt", null, null);
        assertEquals("foo.txt", actual);
    }
}