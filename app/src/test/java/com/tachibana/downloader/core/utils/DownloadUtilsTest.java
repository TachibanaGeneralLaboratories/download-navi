/*
 * Copyright (C) 2021-2022 Tachibana General Laboratories, LLC
 * Copyright (C) 2021-2022 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import static org.junit.Assert.*;

import com.tachibana.downloader.core.system.FileSystemFacade;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public class DownloadUtilsTest {
    FileSystemFacade mockFs;

    @Before
    public void setUp() {
        mockFs = Mockito.mock(FileSystemFacade.class);
    }

    @Test
    public void testParseContentRangeFullSize() {
        assertEquals(
                "bytes 0-99/100",
                100,
                DownloadUtils.parseContentRangeFullSize("bytes 0-99/100")
        );
        assertEquals(
                "bytes 0-99/*",
                100,
                DownloadUtils.parseContentRangeFullSize("bytes 0-99/*")
        );
        assertEquals(
                "bytes */100",
                100,
                DownloadUtils.parseContentRangeFullSize("bytes */100")
        );
        assertEquals(
                "bytes */*",
                -1,
                DownloadUtils.parseContentRangeFullSize("bytes */*")
        );
        assertEquals(
                "",
                -1,
                DownloadUtils.parseContentRangeFullSize("")
        );
    }

    @Test
    public void getHttpFileName_contentDisposition() {
        Mockito.when(mockFs.buildValidFatFilename(Mockito.anyString()))
                .thenAnswer((Answer<String>) invocation -> invocation.getArgument(0));

        // Default file name
        assertContentDisposition("downloadfile.bin", "");

        for (String contentDisposition : DownloadUtils.CONTENT_DISPOSITION_TYPES) {
            // continuing with default filenames
            assertContentDisposition("downloadfile.bin", contentDisposition);
            assertContentDisposition("downloadfile.bin", contentDisposition + ";");
            assertContentDisposition("downloadfile.bin", contentDisposition + "; filename");
            assertContentDisposition(".bin", contentDisposition + "; filename=");
            assertContentDisposition(".bin", contentDisposition + "; filename=\"\"");

            // Provided filename field
            assertContentDisposition("filename.jpg", contentDisposition + "; filename=\"filename.jpg\"");
            assertContentDisposition("file\"name.jpg", contentDisposition + "; filename=\"file\\\"name.jpg\"");
            assertContentDisposition("file\\name.jpg", contentDisposition + "; filename=\"file\\\\name.jpg\"");
            assertContentDisposition("file\\\"name.jpg", contentDisposition + "; filename=\"file\\\\\\\"name.jpg\"");
            assertContentDisposition("filename.jpg", contentDisposition + "; filename=filename.jpg");
            assertContentDisposition("filename.jpg", contentDisposition + "; filename=filename.jpg; foo");
            assertContentDisposition("filename.jpg", contentDisposition + "; filename=\"filename.jpg\"; foo");

            // UTF-8 encoded filename* field
            assertContentDisposition(
                    "\uD83E\uDD8A + x.jpg",
                    contentDisposition + "; filename=\"_.jpg\"; filename*=utf-8'en'%F0%9F%A6%8A%20+%20x.jpg"
            );
            assertContentDisposition(
                    "filename 的副本.jpg",
                    contentDisposition + ";filename=\"_.jpg\";" +
                            "filename*=UTF-8''filename%20%E7%9A%84%E5%89%AF%E6%9C%AC.jpg"
            );
            assertContentDisposition(
                    "filename.jpg",
                    contentDisposition + "; filename=_.jpg; filename*=utf-8'en'filename.jpg"
            );
            // Wrong order of the "filename*" segment
            assertContentDisposition(
                    "filename.jpg",
                    contentDisposition + "; filename*=utf-8'en'filename.jpg; filename=_.jpg"
            );
            // Semicolon at the end
            assertContentDisposition(
                    "filename.jpg",
                    contentDisposition + "; filename*=utf-8'en'filename.jpg; foo"
            );

            // ISO-8859-1 encoded filename* field
            assertContentDisposition(
                    "file' 'name.jpg",
                    contentDisposition + "; filename=\"_.jpg\"; filename*=iso-8859-1'en'file%27%20%27name.jpg"
            );

            assertContentDisposition("success.html", contentDisposition + "; filename*=utf-8''success.html; foo");
            assertContentDisposition("success.html", contentDisposition + "; filename*=utf-8''success.html");

            // Multibyte characters
            assertContentDisposition("şıö.txt", contentDisposition + "; filename=\"şıö.txt\"");
            assertContentDisposition("şıö.txt", contentDisposition + "; filename=\"%C5%9F%C4%B1%C3%B6.txt\"");
        }
    }

    @Test
    public void getHttpFileName_urlFilename() {
        Mockito.when(mockFs.buildValidFatFilename(Mockito.anyString()))
                .thenAnswer((Answer<String>) invocation -> invocation.getArgument(0));

        // Default file name
        assertContentDisposition("downloadfile.bin", "");

        assertUrlFilename("file.txt", "http://example.org/file.txt");
        assertUrlFilename("file.tar.gz", "http://example.org/file.tar.gz");
        assertUrlFilename("file.txt", "http://example.org/file.txt;somedata");
        assertUrlFilename("file.tar.gz", "http://example.org/file.tar.gz;somedata");
        assertUrlFilename("file.txt", "http://example.org/file.txt&query");
        assertUrlFilename("[example.org] file.txt", "http://example.org/[example.org] file.txt");
        assertUrlFilename("[example.org] file.tar.gz", "http://example.org/[example.org] file.tar.gz");

        // Multibyte characters
        assertUrlFilename("şıö.txt", "http://example.org/şıö.txt");
        assertUrlFilename("şıö.txt", "http://example.org/%C5%9F%C4%B1%C3%B6.txt");
    }

    private void assertContentDisposition(String expected, String contentDisposition) {
        assertEquals(
                expected,
                DownloadUtils.getHttpFileName(mockFs, "", contentDisposition, null, null)
        );
    }

    private void assertUrlFilename(String expected, String decodedUrl) {
        assertEquals(
                expected,
                DownloadUtils.getHttpFileName(mockFs, decodedUrl, null, null, null)
        );
    }
}