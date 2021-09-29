package com.tachibana.downloader.core.utils;

import static org.junit.Assert.*;

import com.tachibana.downloader.core.system.FileSystemFacade;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
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
        }
    }

    private void assertContentDisposition(String expected, String contentDisposition) {
        assertEquals(
                expected,
                DownloadUtils.getHttpFileName(mockFs, "", contentDisposition, null, null)
        );
    }
}