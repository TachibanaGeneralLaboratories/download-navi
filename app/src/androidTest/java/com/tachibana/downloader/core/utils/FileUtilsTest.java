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

import android.net.Uri;
import android.util.Log;

import com.tachibana.downloader.AbstractTest;
import com.tachibana.downloader.core.exception.FileAlreadyExistsException;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.*;

public class FileUtilsTest extends AbstractTest
{
    private static final long FILE_10_MB_SIZE = 1024 * 1024 * 10;
    private static final long FILE_5_MB_SIZE = 1024 * 1024 * 5;

    private Uri dir;

    @Override
    public void init()
    {
        super.init();

        dir = Uri.parse("file://" + FileUtils.getDefaultDownloadPath());
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

            FileUtils.copyFile(context, Uri.fromFile(f1), Uri.fromFile(f2), true);

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
            FileUtils.copyFile(context, uri, uri, true);

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

            FileUtils.moveFile(context, dir, f1Name, dir, f2Name, null, true);

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
}