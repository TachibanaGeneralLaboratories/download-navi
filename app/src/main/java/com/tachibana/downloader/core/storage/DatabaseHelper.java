/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of DownloadNavi.
 *
 * DownloadNavi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DownloadNavi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DownloadNavi.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tachibana.downloader.core.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/*
 * A database model for store torrents.
 */

public class DatabaseHelper extends SQLiteOpenHelper
{
    @SuppressWarnings("unused")
    private static final String TAG = DatabaseHelper.class.getSimpleName();

    private static final String DATABASE_NAME = "tachibana_downloader.db";
    private static final int DATABASE_VERSION = 1;
    public static final String COLUMN_ID = "_id";

    /* Download info storage */
    public static final String DOWNLOAD_INFO_TABLE = "download_info";
    public static final String COLUMN_DOWNLOAD_INFO_ID = "id";
    public static final String COLUMN_DOWNLOAD_INFO_FILE_PATH = "file_path";
    public static final String COLUMN_DOWNLOAD_INFO_URL = "url";
    public static final String COLUMN_DOWNLOAD_INFO_FILENAME = "filename";
    public static final String COLUMN_DOWNLOAD_INFO_DESCRIPTION = "description";
    public static final String COLUMN_DOWNLOAD_INFO_MIME_TYPE = "mime_type";
    public static final String COLUMN_DOWNLOAD_INFO_TOTAL_BYTES = "total_bytes";
    public static final String COLUMN_DOWNLOAD_INFO_STATUS_CODE = "status_code";
    public static final String COLUMN_DOWNLOAD_INFO_WIFI_ONLY = "wifi_only";
    public static final String COLUMN_DOWNLOAD_INFO_NUM_PIECES = "num_pieces";
    public static final String COLUMN_DOWNLOAD_INFO_RETRY = "retry";
    public static final String COLUMN_DOWNLOAD_INFO_PARTIAL_SUPPORT = "partial_support";
    public static final String COLUMN_DOWNLOAD_INFO_STATUS_MSG = "status_msg";

    public static final String DOWNLOAD_PIECE_TABLE = "download_piece";
    public static final String COLUMN_DOWNLOAD_PIECE_INDEX = "piece_index";
    public static final String COLUMN_DOWNLOAD_PIECE_SIZE = "size";
    public static final String COLUMN_DOWNLOAD_PIECE_CUR_BYTES = "cur_bytes";
    public static final String COLUMN_DOWNLOAD_PIECE_NUM_FAILED = "num_failed";
    public static final String COLUMN_DOWNLOAD_PIECE_STATUS_CODE = "status_code";
    public static final String COLUMN_DOWNLOAD_PIECE_STATUS_MSG = "status_msg";

    public static final String DOWNLOAD_INFO_HEADERS_TABLE = "download_info_headers";
    public static final String COLUMN_DOWNLOAD_INFO_HEADER_NAME = "header_name";
    public static final String COLUMN_DOWNLOAD_INFO_HEADER_VAL = "header_val";

    /* User agent storage */
    public static final String USER_AGENT_TABLE = "user_agent";
    public static final String COLUMN_USER_AGENT_STR = "user_agent_str";
    private static final String[] defaultUserAgents = new String[] {
            "Mozilla/5.0 (Linux; U; Android 4.1; en-us; DV Build/Donut)",
            "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1; Trident/6.0)",
            "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.113 Safari/537.36",
            "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:54.0) Gecko/20100101 Firefox/54.0",
            "Opera/9.80 (Windows NT 6.1) Presto/2.12.388 Version/12.17",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/603.3.8 (KHTML, like Gecko) Version/10.1.2 Safari/603.3.8",
    };

    private static final String CREATE_DOWNLOAD_INFO_TABLE = "create table "
            + DOWNLOAD_INFO_TABLE +
            "(" + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_DOWNLOAD_INFO_ID + " text unique, "
            + COLUMN_DOWNLOAD_INFO_FILE_PATH + " text, "
            + COLUMN_DOWNLOAD_INFO_URL + " text, "
            + COLUMN_DOWNLOAD_INFO_FILENAME + " text, "
            + COLUMN_DOWNLOAD_INFO_DESCRIPTION + " text, "
            + COLUMN_DOWNLOAD_INFO_MIME_TYPE + " text, "
            + COLUMN_DOWNLOAD_INFO_TOTAL_BYTES + " integer, "
            + COLUMN_DOWNLOAD_INFO_WIFI_ONLY + " integer, "
            + COLUMN_DOWNLOAD_INFO_NUM_PIECES + " integer, "
            + COLUMN_DOWNLOAD_INFO_RETRY + " integer, "
            + COLUMN_DOWNLOAD_INFO_PARTIAL_SUPPORT + " integer, "
            + COLUMN_DOWNLOAD_INFO_STATUS_MSG + " text, "
            + COLUMN_DOWNLOAD_INFO_STATUS_CODE + " integer );";

    private static final String CREATE_DOWNLOAD_PIECE_TABLE = "create table "
            + DOWNLOAD_PIECE_TABLE +
            "(" + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_DOWNLOAD_INFO_ID + " text, "
            + COLUMN_DOWNLOAD_PIECE_INDEX + " integer unique, "
            + COLUMN_DOWNLOAD_PIECE_SIZE + " integer, "
            + COLUMN_DOWNLOAD_PIECE_CUR_BYTES + " integer, "
            + COLUMN_DOWNLOAD_PIECE_NUM_FAILED + " integer, "
            + COLUMN_DOWNLOAD_PIECE_STATUS_MSG + " text, "
            + COLUMN_DOWNLOAD_PIECE_STATUS_CODE + " integer );";

    private static final String CREATE_DOWNLOAD_INFO_HEADERS_TABLE = "create table "
            + DOWNLOAD_INFO_HEADERS_TABLE +
            "(" + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_DOWNLOAD_INFO_ID + " text, "
            + COLUMN_DOWNLOAD_INFO_HEADER_NAME + " text, "
            + COLUMN_DOWNLOAD_INFO_HEADER_VAL + " text );";

    private static final String CREATE_USER_AGENT_TABLE = "create table "
            + USER_AGENT_TABLE +
            "(" + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_USER_AGENT_STR + " text );";

    public DatabaseHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase)
    {
        sqLiteDatabase.execSQL(CREATE_DOWNLOAD_INFO_TABLE);
        sqLiteDatabase.execSQL(CREATE_DOWNLOAD_INFO_HEADERS_TABLE);
        sqLiteDatabase.execSQL(CREATE_DOWNLOAD_PIECE_TABLE);
        sqLiteDatabase.execSQL(CREATE_USER_AGENT_TABLE);
        initUserAgentTable(sqLiteDatabase);
    }

    private void initUserAgentTable(SQLiteDatabase sqLiteDatabase)
    {
        for (String userAgent : defaultUserAgents) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_USER_AGENT_STR, userAgent);
            sqLiteDatabase.insert(USER_AGENT_TABLE, null, values);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion)
    {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to version "+ newVersion);

        /* Nothing */
    }
}
