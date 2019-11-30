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

package com.tachibana.downloader.core.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.tachibana.downloader.core.model.data.StatusCode;
import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.model.data.entity.DownloadPiece;
import com.tachibana.downloader.core.system.SystemFacadeHelper;
import com.tachibana.downloader.core.system.FileSystemFacade;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest
{
    private static final String TEST_DATABASE_NAME = "tachibana_downloader_test.db";

    private Context context = ApplicationProvider.getApplicationContext();
    @Rule
    public MigrationTestHelper helper= new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class.getCanonicalName(),
            new FrameworkSQLiteOpenHelperFactory());

    private FileSystemFacade fs;

    @Before
    public void init()
    {
        fs = SystemFacadeHelper.getFileSystemFacade(context);
    }

    @Test
    public void testMigration1to2_Torrent() throws IOException
    {
        SupportSQLiteDatabase sqliteDb = helper.createDatabase(TEST_DATABASE_NAME, 1);

        UUID infoId = UUID.randomUUID();
        int pieceIndex = 1;

        ContentValues values1 = new ContentValues();
        values1.put("id", infoId.toString());
        values1.put("dirPath", fs.getDefaultDownloadPath());
        values1.put("url", "http://example.org");
        values1.put("fileName", "example");
        values1.put("mimeType", "application/octet-stream");
        values1.put("totalBytes", 10);
        values1.put("numPieces", 1);
        values1.put("statusCode", StatusCode.STATUS_SUCCESS);
        values1.put("unmeteredConnectionsOnly", 0);
        values1.put("retry", 1);
        values1.put("partialSupport", 1);
        values1.put("dateAdded", System.currentTimeMillis());
        values1.put("visibility", DownloadInfo.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        values1.put("hasMetadata", 1);
        assertNotEquals(sqliteDb.insert("DownloadInfo", SQLiteDatabase.CONFLICT_REPLACE, values1), -1);

        ContentValues values2 = new ContentValues();
        values2.put("pieceIndex", pieceIndex);
        values2.put("infoId", infoId.toString());
        values2.put("size", 10);
        values2.put("curBytes", 10);
        values2.put("statusCode", StatusCode.STATUS_SUCCESS);
        values2.put("statusMsg", "Success");
        values2.put("speed", 5);
        values2.put("numFailed", 0);
        assertNotEquals(sqliteDb.insert("DownloadPiece", SQLiteDatabase.CONFLICT_REPLACE, values2), -1);

        sqliteDb.close();

        helper.runMigrationsAndValidate(TEST_DATABASE_NAME, 2, true,
                AppDatabase.MIGRATION_1_2);

        AppDatabase db = getMigratedRoomDatabase();

        DownloadPiece piece = db.downloadDao().getPiece(pieceIndex, infoId);
        DownloadInfo info = db.downloadDao().getInfoById(infoId);
        assertNotNull(piece);
        assertNotNull(info);

        assertEquals(pieceIndex, piece.index);
        assertEquals(infoId, piece.infoId);
        assertEquals(10, piece.size);
        assertEquals(10, piece.curBytes);
        assertEquals(StatusCode.STATUS_SUCCESS, piece.statusCode);
        assertEquals("Success", piece.statusMsg);
        assertEquals(5, piece.speed);

        /* Check index table */
        db.downloadDao().deleteInfo(info);
        assertNull(db.downloadDao().getPiece(pieceIndex, infoId));
    }

    private AppDatabase getMigratedRoomDatabase()
    {
        AppDatabase db = Room.databaseBuilder(context,
                AppDatabase.class, TEST_DATABASE_NAME)
                .addMigrations(
                        AppDatabase.MIGRATION_1_2,
                        AppDatabase.MIGRATION_2_3,
                        AppDatabase.MIGRATION_3_4)
                .build();
        /* Close the database and release any stream resources when the test finishes */
        helper.closeWhenFinished(db);

        return db;
    }
}