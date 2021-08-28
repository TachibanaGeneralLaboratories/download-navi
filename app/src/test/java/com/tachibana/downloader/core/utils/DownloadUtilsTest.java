package com.tachibana.downloader.core.utils;

import static org.junit.Assert.*;

import org.junit.Test;

public class DownloadUtilsTest {
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
}