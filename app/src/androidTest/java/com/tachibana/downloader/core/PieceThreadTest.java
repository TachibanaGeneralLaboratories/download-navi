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

package com.tachibana.downloader.core;

import android.net.Uri;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.tachibana.downloader.AbstractTest;
import com.tachibana.downloader.core.model.PieceThread;
import com.tachibana.downloader.core.model.PieceThreadImpl;
import com.tachibana.downloader.core.model.data.PieceResult;
import com.tachibana.downloader.core.model.data.StatusCode;
import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.model.data.entity.DownloadPiece;
import com.tachibana.downloader.core.utils.DigestUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class PieceThreadTest extends AbstractTest
{
    private ExecutorService exec = Executors.newSingleThreadExecutor();
    private String linuxName = "linux-1.0.tar.gz";
    private String linuxUrl = "https://mirrors.edge.kernel.org/pub/linux/kernel/v1.0/linux-1.0.tar.gz";
    private Uri dir;

    @Override
    public void init()
    {
        super.init();

        dir = Uri.parse("file://" + fs.getDefaultDownloadPath());
    }

    @Test
    public void downloadPieceTest()
    {
        long size = 1024L;
        /* Hash of 1 Kb data */
        String md5Hash = "68a17dd8eff5ba6abc70efd75705270f";

        /* Write download info */
        DownloadInfo info = new DownloadInfo(dir, linuxUrl, linuxName);
        UUID id = info.id;
        repo.addInfo(info, new ArrayList<>());
        DownloadPiece piece = repo.getPiece(0, id);
        piece.size = size;
        repo.updatePiece(piece);

        /* Create file for writing */
        File file = new File(dir.getPath(), linuxName);
        try {
            if (!file.exists())
                assertTrue(file.createNewFile());
            assertTrue(file.exists());

            /* Run piece task */
            runTask(new PieceThreadImpl(context, id, 0, repo, fs));

            /* Read piece info */
            piece = repo.getPiece(0, id);
            assertNotNull(piece);
            assertEquals(getStatus(piece), StatusCode.STATUS_SUCCESS, piece.statusCode);

            /* Read and check piece chunk */
            try (FileInputStream is = new FileInputStream(file)) {
                assertEquals(md5Hash, DigestUtils.makeMd5Hash(is));
            }

        } catch (Throwable e) {
            fail(Log.getStackTraceString(e));
        } finally {
            file.delete();
        }

        assertEquals(size, piece.size);
    }

    private String getStatus(DownloadPiece piece)
    {
        return "{code=" + piece.statusCode + ", msg=" + piece.statusMsg + "}";
    }

    @Test
    public void zeroLengthTest()
    {
        /* Write download info */
        DownloadInfo info = new DownloadInfo(dir, linuxUrl, linuxName);
        UUID id = info.id;
        repo.addInfo(info, new ArrayList<>());
        DownloadPiece piece = repo.getPiece(0, id);
        piece.size = 0L;
        repo.updatePiece(piece);

        /* Create file for writing */
        File file = new File(dir.getPath(), linuxName);
        try {
            if (!file.exists())
                assertTrue(file.createNewFile());
            assertTrue(file.exists());

            /* Run piece task */
            runTask(new PieceThreadImpl(context, id, 0, repo, fs));

            /* Read piece info */
            piece = repo.getPiece(0, id);
            assertNotNull(piece);
            assertEquals(getStatus(piece), StatusCode.STATUS_SUCCESS, piece.statusCode);

            assertEquals(0, piece.curBytes);
            assertEquals(0, file.length());

        } catch (Throwable e) {
            fail(Log.getStackTraceString(e));
        } finally {
            file.delete();
        }
    }

    private void runTask(PieceThread task) throws InterruptedException
    {
        exec.submit(task);
        exec.shutdownNow();
        /* Wait 5 minutes */
        exec.awaitTermination(5, TimeUnit.MINUTES);
    }
}