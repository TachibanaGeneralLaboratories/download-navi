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

import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.tachibana.downloader.AbstractTest;
import com.tachibana.downloader.core.model.DownloadThreadImpl;
import com.tachibana.downloader.core.model.data.DownloadResult;
import com.tachibana.downloader.core.model.data.StatusCode;
import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.model.data.entity.DownloadPiece;
import com.tachibana.downloader.core.utils.DigestUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class DownloadThreadTest extends AbstractTest
{
    private ExecutorService exec = Executors.newSingleThreadExecutor();
    private String linuxName = "linux-1.0.tar.gz";
    private String linuxUrl = "https://mirrors.edge.kernel.org/pub/linux/kernel/v1.0/linux-1.0.tar.gz";
    private String linuxSha256Hash = "014cc3ea69a3db6a483eb743e3140e0e45a4a8c168c59f8c3b090cd72ab01802";
    private long linuxSize = 1259161L;
    private Uri dir;

    @Override
    public void init()
    {
        super.init();

        dir = Uri.parse("file://" + fs.getDefaultDownloadPath());
    }

    @Test
    public void testDownload()
    {
        /* Write download info */
        DownloadInfo info = new DownloadInfo(dir, linuxUrl, linuxName);
        UUID id = info.id;
        info.hasMetadata = false;
        repo.addInfo(info, new ArrayList<>());

        /* Run download task and get result */
        DownloadResult result = runTask(new DownloadThreadImpl(id, repo, pref, fs, systemFacade));
        assertNotNull(result);

        /* Read download info */
        info = repo.getInfoById(id);
        assertNotNull(info);
        assertEquals(getStatus(info), DownloadResult.Status.FINISHED, result.status);

        /* Read and check downloaded file */
        File file = new File(dir.getPath(), linuxName);
        try {
            assertTrue(file.exists());

            try (FileInputStream is = new FileInputStream(file)) {
                assertEquals(linuxSize, file.length());
                assertEquals(linuxSha256Hash, DigestUtils.makeSha256Hash(is));

            } catch (FileNotFoundException e) {
                fail("File not found");
            } catch (IOException e) {
                fail(Log.getStackTraceString(e));
            }
        } finally {
            file.delete();
        }

        /* Check metadata */
        assertEquals(StatusCode.STATUS_SUCCESS, info.statusCode);
        assertEquals("application/x-gzip", info.mimeType);
        assertEquals(linuxSize, info.totalBytes);
    }

    private String getStatus(DownloadInfo info)
    {
        return "{code=" + info.statusCode + ", msg=" + info.statusMsg + "}";
    }

    @Test
    public void testZeroLength()
    {
        /* Write download info */
        DownloadInfo info = new DownloadInfo(dir, linuxUrl, linuxName);
        UUID id = info.id;
        info.totalBytes = 0;
        info.hasMetadata = true;
        repo.addInfo(info, new ArrayList<>());

        /* Run download task and get result */
        DownloadResult result = runTask(new DownloadThreadImpl(id, repo, pref, fs, systemFacade));
        assertNotNull(result);

        /* Read download info */
        info = repo.getInfoById(id);
        assertNotNull(info);
        assertEquals(getStatus(info), DownloadResult.Status.FINISHED, result.status);

        /* Read and check downloaded file */
        File file = new File(dir.getPath(), linuxName);
        try {
            assertTrue(file.exists());
            assertEquals(0, file.length());
        } finally {
            file.delete();
        }

        /* Check metadata */
        assertEquals(StatusCode.STATUS_SUCCESS, info.statusCode);
        long downloadBytes = 0;
        for (DownloadPiece piece : repo.getPiecesById(id))
            downloadBytes += info.getDownloadedBytes(piece);
        assertEquals(0, downloadBytes);
    }

    @Test
    public void testDownloadMultipart()
    {
        /* Write download info */
        DownloadInfo info = new DownloadInfo(dir, linuxUrl, linuxName);
        UUID id = info.id;
        info.totalBytes = linuxSize;
        info.setNumPieces(DownloadInfo.MAX_PIECES);
        repo.addInfo(info, new ArrayList<>());

        /* Run download task and get result */
        DownloadResult result = runTask(new DownloadThreadImpl(id, repo, pref, fs, systemFacade));
        assertNotNull(result);

        /* Read download info */
        info = repo.getInfoById(id);
        assertNotNull(info);
        assertEquals(getStatus(info), DownloadResult.Status.FINISHED, result.status);

        /* Read and check downloaded file */
        File file = new File(dir.getPath(), linuxName);
        try {
            assertTrue(file.exists());

            try (FileInputStream is = new FileInputStream(file)) {
                assertEquals(linuxSize, file.length());
                assertEquals(linuxSha256Hash, DigestUtils.makeSha256Hash(is));

            } catch (FileNotFoundException e) {
                fail("File not found");
            } catch (IOException e) {
                fail(Log.getStackTraceString(e));
            }
        } finally {
            file.delete();
        }

        assertEquals(StatusCode.STATUS_SUCCESS, info.statusCode);

        /* Check pieces */
        List<DownloadPiece> pieces = repo.getPiecesById(id);
        assertEquals(info.getNumPieces(), pieces.size());

        long downloadedBytes = 0;
        for (DownloadPiece piece : pieces)
            downloadedBytes += info.getDownloadedBytes(piece);
        assertEquals(info.totalBytes, downloadedBytes);
    }

    @Test
    public void testDownload_withoutPartialSupport()
    {
        String url = "http://example.org";
        String name = "example.html";

        /* Write download info */
        DownloadInfo info = new DownloadInfo(dir, url, name);
        UUID id = info.id;
        info.hasMetadata = false;
        repo.addInfo(info, new ArrayList<>());

        /* Run download task and get result */
        DownloadResult result = runTask(new DownloadThreadImpl(id, repo, pref, fs, systemFacade));
        assertNotNull(result);

        /* Read download info */
        info = repo.getInfoById(id);
        assertNotNull(info);
        assertEquals(getStatus(info), DownloadResult.Status.FINISHED, result.status);

        /* Read and check downloaded file */
        File file = new File(dir.getPath(), name);
        try {
            assertTrue(file.exists());
            assertNotEquals(0, file.length());

        } finally {
            file.delete();
        }

        /* Check metadata */
        assertEquals(StatusCode.STATUS_SUCCESS, info.statusCode);
        long downloadBytes = 0;
        for (DownloadPiece piece : repo.getPiecesById(id))
            downloadBytes += info.getDownloadedBytes(piece);
        assertNotEquals(0, downloadBytes);
    }

    @Test
    public void testDownload_withoutPartialSupport_withLength()
    {
        String url = "https://google.com";
        String name = "google.html";

        /* Write download info */
        DownloadInfo info = new DownloadInfo(dir, url, name);
        UUID id = info.id;
        info.hasMetadata = false;
        repo.addInfo(info, new ArrayList<>());

        /* Run download task and get result */
        DownloadResult result = runTask(new DownloadThreadImpl(id, repo, pref, fs, systemFacade));
        assertNotNull(result);

        /* Read download info */
        info = repo.getInfoById(id);
        assertNotNull(info);
        assertEquals(getStatus(info), DownloadResult.Status.FINISHED, result.status);

        /* Read and check downloaded file */
        File file = new File(dir.getPath(), name);
        try {
            assertTrue(file.exists());
            assertNotEquals(0, file.length());

        } finally {
            file.delete();
        }

        /* Check metadata */
        assertEquals(StatusCode.STATUS_SUCCESS, info.statusCode);
        long downloadBytes = 0;
        for (DownloadPiece piece : repo.getPiecesById(id))
            downloadBytes += info.getDownloadedBytes(piece);
        assertNotEquals(0, downloadBytes);
    }

    @Test
    public void testDownload_networkConnection()
    {
        /* Write download info */
        DownloadInfo info = new DownloadInfo(dir, linuxUrl, linuxName);
        UUID id = info.id;
        info.totalBytes = linuxSize;
        repo.addInfo(info, new ArrayList<>());

        /* Reset values */
        turnUnmeteredOnlyPref(false);
        turnRoamingPref(false);

        try {
            /* Check unmetered connections only */
            turnUnmeteredOnlyPref(true);
            turnRoamingPref(false);
            enableMobile();
            turnRoaming(false);
            runTask_checkWaitingNetwork("Unmetered connections only test failed", id);

            /* Check roaming */
            turnUnmeteredOnlyPref(false);
            turnRoamingPref(true);
            enableMobile();
            turnRoaming(true);
            runTask_checkWaitingNetwork("Roaming test failed", id);

            /* Check unmetered connections only and roaming */
            turnUnmeteredOnlyPref(true);
            turnRoamingPref(true);
            enableMobile();
            turnRoaming(true);
            runTask_checkWaitingNetwork("Unmetered connections only and roaming test failed", id);

            /* Check unmetered connections only and roaming (with enabled wifi) */
            turnUnmeteredOnlyPref(true);
            turnRoamingPref(true);
            enableWiFi();
            runTask_checkWaitingNetwork("Unmetered connections only and roaming (with enabled wifi) test failed", id);

        } finally {
            new File(dir.getPath(), linuxName).delete();

            /* Restore state */
            turnUnmeteredOnlyPref(false);
            turnRoamingPref(false);
        }
    }

    private void runTask_checkWaitingNetwork(String msg, UUID id)
    {
        runTask(new DownloadThreadImpl(id, repo, pref, fs, systemFacade));
        DownloadInfo info = repo.getInfoById(id);
        assertEquals(msg, StatusCode.STATUS_WAITING_FOR_NETWORK, info.statusCode);
    }

    private void turnUnmeteredOnlyPref(boolean enable)
    {
        pref.unmeteredConnectionsOnly(enable);
    }

    private void turnRoamingPref(boolean enable)
    {
        pref.enableRoaming(enable);
    }

    private void enableWiFi()
    {
        systemFacade.activeNetworkType = ConnectivityManager.TYPE_WIFI;
        systemFacade.isMetered = false;
    }

    private void enableMobile()
    {
        systemFacade.activeNetworkType = ConnectivityManager.TYPE_MOBILE;
        systemFacade.isMetered = true;
    }

    private void turnRoaming(boolean enable)
    {
        systemFacade.isRoaming = enable;
    }

    private DownloadResult runTask(DownloadThreadImpl task)
    {
        Future<DownloadResult> f = exec.submit(task);
        DownloadResult result = null;
        try {
            result = f.get();

        } catch (Throwable e) {
            fail(Log.getStackTraceString(e));
        }

        return result;
    }
}