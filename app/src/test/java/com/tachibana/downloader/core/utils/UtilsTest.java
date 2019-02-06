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

package com.tachibana.downloader.core.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class UtilsTest
{
    @Test
    public void testGetHttpFileName()
    {
        String actual = Utils.getHttpFileName("http://example.org/file.txt", null, null);
        assertEquals("file.txt", actual);
    }

    @Test
    public void testGetHttpFileName_noExtension()
    {
        String actual = Utils.getHttpFileName("http://example.org/file", null, null);
        assertEquals("file", actual);
    }

    @Test
    public void testGetHttpFileName_withDisposition()
    {
        String actual = Utils.getHttpFileName("http://example.org/file.txt",
                "attachment; filename=\"subdir/real.pdf\"", null);
        assertEquals("real.pdf", actual);
    }

    @Test
    public void testGetHttpFileName_withLocation()
    {
        String actual = Utils.getHttpFileName("http://example.org/file.txt",
                null, "Content-Location: subdir/real.pdf");
        assertEquals("real.pdf", actual);
    }

    @Test
    public void testGetHttpFileName_withDispositionAndLocation()
    {
        String actual = Utils.getHttpFileName("http://example.org/file.txt",
                "attachment; filename=\"subdir/real.pdf\"",
                "Content-Location: subdir/file.pdf");
        assertEquals("real.pdf", actual);
    }
}