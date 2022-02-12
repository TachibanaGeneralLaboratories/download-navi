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

package com.tachibana.downloader.core.system;

import android.net.Uri;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.tachibana.downloader.AbstractTest;
import com.tachibana.downloader.core.exception.FileAlreadyExistsException;
import com.tachibana.downloader.core.utils.DigestUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class FileSystemFacadeTest extends AbstractTest
{
    private FileSystemFacadeImpl fakeFs;
    private FakeFsModuleResolver fsResolver;
    private Uri dirUri = Uri.parse("file:///root");

    private static final long FILE_10_MB_SIZE = 1024 * 1024 * 10;
    private static final long FILE_5_MB_SIZE = 1024 * 1024 * 5;
    private Uri dir;

    @Override
    public void init()
    {
        super.init();

        dir = Uri.parse("file://" + fs.getDefaultDownloadPath());
        fsResolver = new FakeFsModuleResolver();
        fakeFs = new FileSystemFacadeImpl(new FakeSysCall(), fsResolver, context);
    }

    @Test
    public void testCopyFile_AlreadyExistsDest()
    {
        File f1 = new File(dir.getPath(), "test1");
        File f2 = new File(dir.getPath(), "test2");
        String f1Md5Hash = null;
        String f2Md5Hash = null;

        try {
            generateFileContent(f1, FILE_5_MB_SIZE);
            generateFileContent(f2, FILE_10_MB_SIZE);

            try (FileInputStream is = new FileInputStream(f1)) {
                f1Md5Hash = DigestUtils.makeMd5Hash(is);
            }

            fs.copyFile(Uri.fromFile(f1), Uri.fromFile(f2), true);

            try (FileInputStream is = new FileInputStream(f2)) {
                f2Md5Hash = DigestUtils.makeMd5Hash(is);
            }

            assertEquals(f1Md5Hash, f2Md5Hash);

        } catch (IOException e) {
            fail(Log.getStackTraceString(e));
        } finally {
            f1.delete();
            f2.delete();
        }
    }

    @Test
    public void testCopyFile_SameFile()
    {
        File f = new File(dir.getPath(), "test");

        try {
            generateFileContent(f, 1);

            Uri uri = Uri.fromFile(f);
            fs.copyFile(uri, uri, true);

        } catch (IllegalArgumentException e) {
            return;
        } catch (IOException e) {
            fail(Log.getStackTraceString(e));
        } finally {
            f.delete();
        }

        fail();
    }

    @Test
    public void testMoveFile_AlreadyExistsDest()
    {
        String f1Name = "test1";
        String f2Name = "test2";
        File f1 = new File(dir.getPath(), f1Name);
        File f2 = new File(dir.getPath(), f2Name);
        String f1Md5Hash = null;
        String f2Md5Hash = null;

        try {
            generateFileContent(f1, FILE_5_MB_SIZE);
            generateFileContent(f2, FILE_10_MB_SIZE);

            try (FileInputStream is = new FileInputStream(f1)) {
                f1Md5Hash = DigestUtils.makeMd5Hash(is);
            }

            fs.moveFile(dir, f1Name, dir, f2Name, true);

            try (FileInputStream is = new FileInputStream(f2)) {
                f2Md5Hash = DigestUtils.makeMd5Hash(is);
            }

            assertFalse(f1.exists());
            assertTrue(f2.exists());
            assertEquals(f1Md5Hash, f2Md5Hash);

        } catch (IOException | FileAlreadyExistsException e) {
            fail(Log.getStackTraceString(e));
        } finally {
            f1.delete();
            f2.delete();
        }
    }

    private void generateFileContent(File file, long size) throws IOException
    {
        int blockSize = 1024 * 1024;
        if (blockSize < size)
            blockSize = (int)size;
        byte[] block = new byte[blockSize];
        Random random = new Random();

        try (FileOutputStream os = new FileOutputStream(file)) {
            for (long off = 0; off <= size; off += blockSize) {
                random.nextBytes(block);
                os.write(block);
            }
        }
    }

    @Test
    public void closeQuietly()
    {
        FakeCloseable c = new FakeCloseable();

        fakeFs.closeQuietly(c);
        assertTrue(c.closed);
    }

    @Test
    public void makeFilename()
    {
        fsResolver.existsFileNames = Arrays.asList("foo");
        assertEquals("test.txt", fakeFs.makeFilename(dirUri, "test.txt"));
        fsResolver.existsFileNames = Arrays.asList("test.txt");
        assertEquals("test (1).txt", fakeFs.makeFilename(dirUri, "test.txt"));

        fsResolver.existsFileNames = null;
    }

    @Test
    public void moveFile()
    {
        try {
            try {
                fsResolver.existsFileNames = Arrays.asList("foo.txt");
                fakeFs.moveFile(dirUri, "foo.txt", dirUri, "bar.txt", false);

            } catch (Exception e) {
                fail(Log.getStackTraceString(e));
            }

            try {
                fsResolver.existsFileNames = Arrays.asList("foo.txt", "bar.txt");
                fakeFs.moveFile(dirUri, "foo.txt", dirUri, "bar.txt", true);

            } catch (Exception e) {
                fail(Log.getStackTraceString(e));
            }

            boolean success = false;
            try {
                fsResolver.existsFileNames = Arrays.asList("foo.txt", "bar.txt");
                fakeFs.moveFile(dirUri, "foo.txt", dirUri, "bar.txt", false);

            } catch (IOException e) {
                fail(Log.getStackTraceString(e));

            } catch (FileAlreadyExistsException e) {
                success = true;
            }
            assertTrue(success);

            Uri destDir = Uri.parse("file:///root/foo");
            try {
                fsResolver.existsFileNames = Arrays.asList("foo.txt");
                fakeFs.moveFile(dirUri, "foo.txt", destDir, "bar.txt", false);

            } catch (Exception e) {
                fail(Log.getStackTraceString(e));
            }

        } finally {
            fsResolver.existsFileNames = null;
        }
    }

    @Test
    public void copyFile()
    {
        Uri src, dest;
        fsResolver.existsFileNames = Arrays.asList("foo.txt", "dest.txt");
        try {
            src = Uri.parse("file:///root/foo.txt");
            dest = Uri.parse("file:///root/dest.txt");
            fakeFs.copyFile(src, dest, false);

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }

        try {
            src = Uri.parse("file:///root/foo.txt");
            dest = Uri.parse("file:///root/dest.txt");
            fakeFs.copyFile(src, dest, true);

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }

        try {
            src = Uri.parse("file:///root/foo.txt");
            dest = Uri.parse("file:///root/dest.txt");
            fakeFs.copyFile(src, dest, false);

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }

        boolean success = false;
        try {
            src = Uri.parse("file:///root/foo.txt");
            dest = Uri.parse("file:///root/foo.txt");
            fsResolver.existsFileNames = Arrays.asList("foo.txt");
            fakeFs.copyFile(src, dest, false);

        } catch (IllegalArgumentException e) {
            success = true;
        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }
        assertTrue(success);

        fsResolver.existsFileNames = null;
    }

    @Test
    public void appendExtension()
    {
        assertEquals("test.txt", fakeFs.appendExtension("test", "text/plain"));
        assertEquals("test.png", fakeFs.appendExtension("test", "image/png"));
        assertEquals("test.png", fakeFs.appendExtension("test.png", "image/png"));
        assertEquals("test", fakeFs.appendExtension("test", "application/octet-stream"));
    }

    @Test
    public void deleteFile()
    {
        fsResolver.existsFileNames = Arrays.asList("test.txt");
        try {
            assertTrue(fakeFs.deleteFile(Uri.parse("file:///root/test.txt")));

        } catch (FileNotFoundException e) {
            fail(Log.getStackTraceString(e));

        } finally {
            fsResolver.existsFileNames = null;
        }
    }

    @Test
    public void getFileUri()
    {
        fsResolver.existsFileNames = Arrays.asList("test.txt");
        assertEquals("file:///root/test.txt", fakeFs.getFileUri(dirUri, "test.txt").toString());
        fsResolver.existsFileNames = Arrays.asList("bar");
        assertNull(fakeFs.getFileUri(dirUri, "test.txt"));
        fsResolver.existsFileNames = null;
    }

    @Test
    public void getFileUri_relativePath()
    {
        fsResolver.existsFileNames = Arrays.asList("bar.txt");
        assertEquals("file:///root/foo/bar.txt", fakeFs.getFileUri("foo/bar.txt", dirUri).toString());
        fsResolver.existsFileNames = Arrays.asList("test.txt");
        assertNull(fakeFs.getFileUri("foo/bar.txt", dirUri));
        fsResolver.existsFileNames = null;
    }

    @Test
    public void createFile()
    {
        try {
            fsResolver.existsFileNames = Arrays.asList("test.txt");
            assertEquals("file:///root/test.txt", fakeFs.createFile(dirUri, "test.txt", false).toString());
            assertEquals("file:///root/test.txt", fakeFs.createFile(dirUri, "test.txt", true).toString());
            fsResolver.existsFileNames = Arrays.asList("foo");
            assertEquals("file:///root/test.txt", fakeFs.createFile(dirUri, "test.txt", false).toString());
            assertEquals("file:///root/test.txt", fakeFs.createFile(dirUri, "test.txt", true).toString());

        } catch (IOException e) {
            fail(Log.getStackTraceString(e));

        } finally {
            fsResolver.existsFileNames = null;
        }
    }

    @Test
    public void getExtension()
    {
        assertEquals("txt", fakeFs.getExtension("test.txt"));
        assertEquals("png", fakeFs.getExtension("test.png"));
        assertEquals("", fakeFs.getExtension("test"));
        assertEquals("png", fakeFs.getExtension("test.foo.png"));
    }

    @Test
    public void isValidFatFilename()
    {
        assertTrue(fakeFs.isValidFatFilename("Valid_012345"));
        assertFalse(fakeFs.isValidFatFilename("IVALID*012345"));
        assertFalse(fakeFs.isValidFatFilename(""));
        assertFalse(fakeFs.isValidFatFilename(String.join(" ", Collections.nCopies(256, "long string"))));
    }

    @Test
    public void buildValidFatFilename()
    {
        assertEquals("Valid_012345", fakeFs.buildValidFatFilename("Valid_012345"));
        assertEquals("(invalid)", fakeFs.buildValidFatFilename(""));
        assertEquals("IVALID_012345", fakeFs.buildValidFatFilename("IVALID*012345"));
    }

    @Test
    public void getDirName()
    {
        fsResolver.existsFileNames = Arrays.asList("bar");
        assertEquals("bar", fakeFs.getDirName(Uri.parse("file///root/bar")));
        fsResolver.existsFileNames = null;
    }

    @Test
    public void getDirPath()
    {
        assertEquals("/bar", fakeFs.getDirPath(Uri.parse("file://foo/bar")));
    }
}