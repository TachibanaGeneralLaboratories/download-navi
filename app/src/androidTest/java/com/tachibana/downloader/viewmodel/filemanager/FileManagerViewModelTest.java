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

package com.tachibana.downloader.viewmodel.filemanager;

import android.net.Uri;
import android.util.Log;

import com.tachibana.downloader.AbstractTest;
import com.tachibana.downloader.core.utils.FileUtils;
import com.tachibana.downloader.dialog.filemanager.FileManagerConfig;

import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class FileManagerViewModelTest extends AbstractTest
{
    private FileManagerViewModel viewModel;
    private FileManagerConfig config;

    @Before
    public void init()
    {
        super.init();

        config = new FileManagerConfig(FileUtils.getUserDirPath(),
                null, FileManagerConfig.DIR_CHOOSER_MODE);
        viewModel = new FileManagerViewModel(context, config);
    }

    @Test
    public void testOpenDirectory()
    {
        try {
            viewModel.jumpToDirectory(FileUtils.getUserDirPath());
            viewModel.openDirectory("Android");
            Uri path = viewModel.getCurDirectoryUri();
            assertNotNull(path);
            assertTrue(path.getPath().endsWith("Android"));

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }
    }

    @Test
    public void testJumpToDirectory()
    {
        try {
            viewModel.jumpToDirectory(FileUtils.getUserDirPath() + "/Android");
            Uri path = viewModel.getCurDirectoryUri();
            assertNotNull(path);
            assertTrue(path.getPath().endsWith("Android"));

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }
    }

    @Test
    public void testUpToParentDirectory()
    {
        try {
            viewModel.jumpToDirectory(FileUtils.getUserDirPath() + "/Android");
            viewModel.upToParentDirectory();
            Uri path = viewModel.getCurDirectoryUri();
            assertNotNull(path);
            assertFalse(path.getPath().endsWith("Android"));

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }
    }

    @Test
    public void testCreateFile()
    {
        File f = null;
        try {
            viewModel.jumpToDirectory(FileUtils.getUserDirPath());
            viewModel.createFile("test.txt");
            Uri filePath = viewModel.getFileUri("test.txt");
            assertNotNull(filePath);
            f = new File(filePath.getPath());
            assertTrue(f.exists());

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        } finally {
            if (f != null)
                f.delete();
        }
    }

    @Test
    public void testPermissionDenied()
    {
        try {
            viewModel.jumpToDirectory(FileUtils.getUserDirPath());
            viewModel.upToParentDirectory();

        } catch (SecurityException e) {
            return;
        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }

        fail("Permission available");
    }
}