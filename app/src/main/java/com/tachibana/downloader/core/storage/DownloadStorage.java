/*
 * Copyright (C) 2018 Tachibana General Laboratories, LLC
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import com.tachibana.downloader.core.DownloadInfo;
import com.tachibana.downloader.core.DownloadPiece;

import java.io.FileNotFoundException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * The class provides a single access to the download info repository.
 */

public class DownloadStorage
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadStorage.class.getSimpleName();

    private String[] allInfoColumns = {
            DatabaseHelper.COLUMN_ID,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_FILE_PATH,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_URL,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_FILENAME,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_DESCRIPTION,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_MIME_TYPE,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_TOTAL_BYTES,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_WIFI_ONLY,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_STATUS_MSG,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_STATUS_CODE,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_NUM_PIECES,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_RETRY,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_PARTIAL_SUPPORT
    };

    private String[] allPieceColumns = {
            DatabaseHelper.COLUMN_ID,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID,
            DatabaseHelper.COLUMN_DOWNLOAD_PIECE_INDEX,
            DatabaseHelper.COLUMN_DOWNLOAD_PIECE_CUR_BYTES,
            DatabaseHelper.COLUMN_DOWNLOAD_PIECE_SIZE,
            DatabaseHelper.COLUMN_DOWNLOAD_PIECE_NUM_FAILED,
            DatabaseHelper.COLUMN_DOWNLOAD_PIECE_STATUS_MSG,
            DatabaseHelper.COLUMN_DOWNLOAD_PIECE_STATUS_CODE
    };

    private String[] allHeadersColumns = {
            DatabaseHelper.COLUMN_ID,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_HEADER_NAME,
            DatabaseHelper.COLUMN_DOWNLOAD_INFO_HEADER_VAL
    };

    private Context context;

    public DownloadStorage(Context context)
    {
        this.context = context;
    }

    public boolean addInfo(DownloadInfo info)
    {
        takeUriPermission(info);

        ContentValues infoValues = new ContentValues();

        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID, info.getId().toString());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_FILE_PATH, info.getFilePath().toString());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_URL, info.getUrl());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_FILENAME, info.getFileName());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_DESCRIPTION, info.getDescription());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_MIME_TYPE, info.getMimeType());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_TOTAL_BYTES, info.getTotalBytes());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_WIFI_ONLY, (info.isWifiOnly() ? 1 : 0));
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_STATUS_MSG, info.getStatusMsg());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_STATUS_CODE, info.getStatusCode());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_NUM_PIECES, info.getNumPieces());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_RETRY, (info.isRetry() ? 1 : 0));
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_PARTIAL_SUPPORT, (info.isPartialSupport() ? 1 : 0));

        if (ConnectionManager.getDatabase(context)
                .replace(DatabaseHelper.DOWNLOAD_INFO_TABLE, null, infoValues) < 0)
            return false;

        addHeaders(info);
        addPieces(info);

        return true;
    }

    public void addHeaders(@NonNull DownloadInfo info)
    {
        SQLiteDatabase sqLiteDatabase = ConnectionManager.getDatabase(context);

        try {
            sqLiteDatabase.beginTransaction();
            for (Map.Entry<String, String> header : info.getHeaders().entrySet()) {
                ContentValues headerValues = new ContentValues();
                headerValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID, info.getId().toString());
                headerValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_HEADER_NAME, header.getKey());
                headerValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_HEADER_VAL, header.getValue());
                sqLiteDatabase.replace(DatabaseHelper.DOWNLOAD_INFO_HEADERS_TABLE, null, headerValues);
            }
            sqLiteDatabase.setTransactionSuccessful();

        } finally {
            sqLiteDatabase.endTransaction();
        }
    }

    public boolean updateInfo(DownloadInfo info, boolean filePathChanged, boolean numPiecesChanged)
    {
        if (filePathChanged) {
            DownloadInfo oldInfo = getInfoById(info.getId());
            if (oldInfo == null)
                return false;
            releaseUriPermission(oldInfo);
            takeUriPermission(info);
        }

        ContentValues infoValues = new ContentValues();

        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_FILE_PATH, info.getFilePath().toString());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_URL, info.getUrl());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_FILENAME, info.getFileName());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_DESCRIPTION, info.getDescription());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_MIME_TYPE, info.getMimeType());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_TOTAL_BYTES, info.getTotalBytes());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_WIFI_ONLY, (info.isWifiOnly() ? 1 : 0));
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_STATUS_MSG, info.getStatusMsg());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_STATUS_CODE, info.getStatusCode());
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_RETRY, (info.isRetry() ? 1 : 0));
        infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_PARTIAL_SUPPORT, (info.isPartialSupport() ? 1 : 0));
        if (numPiecesChanged)
            infoValues.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_NUM_PIECES, info.getNumPieces());

        SQLiteDatabase sqLiteDatabase = ConnectionManager.getDatabase(context);

        int ret = sqLiteDatabase.update(DatabaseHelper.DOWNLOAD_INFO_TABLE, infoValues,
                DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID + " = ? ",
                new String[]{ info.getId().toString() });

        deleteHeaders(info);
        addHeaders(info);
        if (numPiecesChanged) {
            deletePieces(info);
            addPieces(info);
        }

        return ret > 0;
    }

    public void deleteInfo(UUID id, boolean withFile)
    {
        DownloadInfo info = getInfoById(id);
        if (info == null)
            return;

        deleteInfo(info, withFile);
    }

    public void deleteInfo(DownloadInfo info, boolean withFile)
    {
        ConnectionManager.getDatabase(context)
                .delete(DatabaseHelper.DOWNLOAD_INFO_TABLE,
                        DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID + " = ? ",
                        new String[]{ info.getId().toString() });
        deleteHeaders(info);
        deletePieces(info);

        if (withFile) {
            try {
                DocumentsContract.deleteDocument(context.getContentResolver(), info.getFilePath());

            } catch (FileNotFoundException | SecurityException e) {
                Log.w(TAG, Log.getStackTraceString(e));
            }

        } else {
            try {
                releaseUriPermission(info);

            } catch (SecurityException e) {
                /* Ignore */
            }
        }
    }

    public void deleteHeaders(DownloadInfo info)
    {
        ConnectionManager.getDatabase(context)
                .delete(DatabaseHelper.DOWNLOAD_INFO_HEADERS_TABLE,
                        DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID + " = ? ",
                        new String[]{ info.getId().toString() });
    }

    public DownloadInfo getInfoById(UUID id)
    {
        Cursor cursor = ConnectionManager.getDatabase(context)
                .query(DatabaseHelper.DOWNLOAD_INFO_TABLE,
                        allInfoColumns,
                        DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID + " = ? ",
                        new String[]{ id.toString() },
                        null,
                        null,
                        null);

        DownloadInfo info = null;

        ColumnIndexCache indexCache = new ColumnIndexCache();
        if (cursor.moveToNext())
            info = cursorToInfo(cursor, indexCache);
        cursor.close();
        indexCache.clear();

        if (info != null)
            info.setHeaders(getHeadersById(id));

        return info;
    }

    public Map<String, String> getHeadersById(UUID id)
    {
        Cursor cursor = ConnectionManager.getDatabase(context)
                .query(DatabaseHelper.DOWNLOAD_INFO_HEADERS_TABLE,
                        allHeadersColumns,
                        DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID + " = ? ",
                        new String[]{ id.toString() },
                        null,
                        null,
                        null);

        Map<String, String> headers = new HashMap<>();

        ColumnIndexCache indexCache = new ColumnIndexCache();
        while (cursor.moveToNext()) {
            Map.Entry<String, String> header = cursorToHeader(cursor, indexCache);
            headers.put(header.getKey(), header.getValue());
        }
        cursor.close();
        indexCache.clear();

        return headers;
    }

    public List<DownloadInfo> getAllInfo()
    {
        List<DownloadInfo> infoList = new ArrayList<>();

        Cursor cursor = ConnectionManager.getDatabase(context).query(DatabaseHelper.DOWNLOAD_INFO_TABLE,
                allInfoColumns,
                null,
                null,
                null,
                null,
                null);

        ColumnIndexCache indexCache = new ColumnIndexCache();
        while (cursor.moveToNext()) {
            DownloadInfo info = cursorToInfo(cursor, indexCache);
            if (info == null)
                continue;
            info.setHeaders(getHeadersById(info.getId()));
            infoList.add(info);
        }
        cursor.close();
        indexCache.clear();

        return infoList;
    }

    /*
     * Returns map with id as key.
     */

    public Map<UUID, DownloadInfo> getAllInfoAsMap()
    {
        Map<UUID, DownloadInfo> infoList = new HashMap<>();

        Cursor cursor = ConnectionManager.getDatabase(context).query(DatabaseHelper.DOWNLOAD_INFO_TABLE,
                allInfoColumns,
                null,
                null,
                null,
                null,
                null);

        ColumnIndexCache indexCache = new ColumnIndexCache();
        while (cursor.moveToNext()) {
            DownloadInfo info = cursorToInfo(cursor, indexCache);
            if (info == null)
                continue;
            info.setHeaders(getHeadersById(info.getId()));
            infoList.put(info.getId(), info);
        }
        cursor.close();
        indexCache.clear();

        return infoList;
    }

    public boolean infoExists(DownloadInfo info)
    {
        Cursor cursor = ConnectionManager.getDatabase(context)
                .query(DatabaseHelper.DOWNLOAD_INFO_TABLE,
                        allInfoColumns,
                        DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID + " = ? ",
                        new String[]{ info.getId().toString() },
                        null,
                        null,
                        null);

        if (cursor.moveToNext()) {
            cursor.close();

            return true;
        }

        cursor.close();

        return false;
    }

    public void addPieces(DownloadInfo info)
    {
        SQLiteDatabase sqLiteDatabase = ConnectionManager.getDatabase(context);

        try {
            sqLiteDatabase.beginTransaction();
            for (DownloadPiece piece : info.makePieces()) {
                ContentValues values = new ContentValues();
                values.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID, piece.getInfoId().toString());
                values.put(DatabaseHelper.COLUMN_DOWNLOAD_PIECE_INDEX, piece.getIndex());
                values.put(DatabaseHelper.COLUMN_DOWNLOAD_PIECE_SIZE, piece.getSize());
                values.put(DatabaseHelper.COLUMN_DOWNLOAD_PIECE_CUR_BYTES, piece.getCurBytes());
                values.put(DatabaseHelper.COLUMN_DOWNLOAD_PIECE_NUM_FAILED, piece.getNumFailed());
                values.put(DatabaseHelper.COLUMN_DOWNLOAD_PIECE_STATUS_MSG, piece.getStatusMsg());
                values.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_STATUS_CODE, piece.getStatusCode());
                sqLiteDatabase.replace(DatabaseHelper.DOWNLOAD_PIECE_TABLE, null, values);
            }
            sqLiteDatabase.setTransactionSuccessful();

        } finally {
            sqLiteDatabase.endTransaction();
        }
    }

    public boolean addPieces(DownloadPiece piece)
    {
        ContentValues values = new ContentValues();

        values.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID, piece.getInfoId().toString());
        values.put(DatabaseHelper.COLUMN_DOWNLOAD_PIECE_INDEX, piece.getIndex());
        values.put(DatabaseHelper.COLUMN_DOWNLOAD_PIECE_SIZE, piece.getSize());
        values.put(DatabaseHelper.COLUMN_DOWNLOAD_PIECE_CUR_BYTES, piece.getCurBytes());
        values.put(DatabaseHelper.COLUMN_DOWNLOAD_PIECE_NUM_FAILED, piece.getNumFailed());
        values.put(DatabaseHelper.COLUMN_DOWNLOAD_PIECE_STATUS_MSG, piece.getStatusMsg());
        values.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_STATUS_CODE, piece.getStatusCode());

        if (ConnectionManager.getDatabase(context)
                .replace(DatabaseHelper.DOWNLOAD_PIECE_TABLE, null, values) < 0)
            return false;

        return true;
    }

    public boolean updatePiece(DownloadPiece piece)
    {
        ContentValues values = new ContentValues();

        values.put(DatabaseHelper.COLUMN_DOWNLOAD_PIECE_CUR_BYTES, piece.getCurBytes());
        values.put(DatabaseHelper.COLUMN_DOWNLOAD_PIECE_NUM_FAILED, piece.getNumFailed());
        values.put(DatabaseHelper.COLUMN_DOWNLOAD_PIECE_STATUS_MSG, piece.getStatusMsg());
        values.put(DatabaseHelper.COLUMN_DOWNLOAD_INFO_STATUS_CODE, piece.getStatusCode());

        SQLiteDatabase sqLiteDatabase = ConnectionManager.getDatabase(context);

        int ret = sqLiteDatabase.update(DatabaseHelper.DOWNLOAD_PIECE_TABLE, values,
                DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID + " = ? AND "
                + DatabaseHelper.COLUMN_DOWNLOAD_PIECE_INDEX + " = ? ",
                new String[]{ piece.getInfoId().toString(),
                        Integer.toString(piece.getIndex()) });

        return ret > 0;
    }

    public void deletePiece(DownloadPiece piece)
    {
        ConnectionManager.getDatabase(context)
                .delete(DatabaseHelper.DOWNLOAD_PIECE_TABLE,
                        DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID + " = ? AND "
                        + DatabaseHelper.COLUMN_DOWNLOAD_PIECE_INDEX + " = ? ",
                        new String[]{ piece.getInfoId().toString(),
                                Integer.toString(piece.getIndex()) });
    }

    public void deletePieces(DownloadInfo info)
    {
        deletePieces(info.getId());
    }

    public void deletePieces(UUID infoId)
    {
        ConnectionManager.getDatabase(context)
                .delete(DatabaseHelper.DOWNLOAD_PIECE_TABLE,
                        DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID + " = ? ",
                        new String[]{ infoId.toString() });
    }

    public DownloadPiece getPieceByIndex(UUID infoId, int index)
    {
        Cursor cursor = ConnectionManager.getDatabase(context)
                .query(DatabaseHelper.DOWNLOAD_PIECE_TABLE,
                        allPieceColumns,
                        DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID + " = ? AND "
                        + DatabaseHelper.COLUMN_DOWNLOAD_PIECE_INDEX + " = ? ",
                        new String[]{ infoId.toString(),
                                Integer.toString(index) },
                        null,
                        null,
                        null);

        DownloadPiece piece = null;

        ColumnIndexCache indexCache = new ColumnIndexCache();
        if (cursor.moveToNext())
            piece = cursorToPiece(cursor, indexCache);
        cursor.close();
        indexCache.clear();

        return piece;
    }

    public List<DownloadPiece> getPiecesById(UUID infoId)
    {
        List<DownloadPiece> piecesList = new ArrayList<>();

        Cursor cursor = ConnectionManager.getDatabase(context)
                .query(DatabaseHelper.DOWNLOAD_PIECE_TABLE,
                        allPieceColumns,
                        DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID + " = ? ",
                        new String[]{ infoId.toString() },
                        null,
                        null,
                        null);

        ColumnIndexCache indexCache = new ColumnIndexCache();
        while (cursor.moveToNext()) {
            DownloadPiece piece = cursorToPiece(cursor, indexCache);
            if (piece == null)
                continue;
            piecesList.add(piece);
        }
        cursor.close();
        indexCache.clear();

        return piecesList;
    }

    public SparseArray<DownloadPiece> getPiecesMapById(UUID infoId)
    {
        SparseArray<DownloadPiece> piecesMap = new SparseArray<>();

        Cursor cursor = ConnectionManager.getDatabase(context)
                .query(DatabaseHelper.DOWNLOAD_PIECE_TABLE,
                        allPieceColumns,
                        DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID + " = ? ",
                        new String[]{ infoId.toString() },
                        null,
                        null,
                        null);

        ColumnIndexCache indexCache = new ColumnIndexCache();
        while (cursor.moveToNext()) {
            DownloadPiece piece = cursorToPiece(cursor, indexCache);
            if (piece == null)
                continue;
            piecesMap.put(piece.getIndex(), piece);
        }
        cursor.close();
        indexCache.clear();

        return piecesMap;
    }

    private DownloadInfo cursorToInfo(Cursor cursor, ColumnIndexCache indexCache)
    {
        String id = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID));
        UUID uuid;
        try {
            uuid = UUID.fromString(id);

        } catch (IllegalArgumentException e) {
            return null;
        }
        String filePath = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_FILE_PATH));
        Uri uri;
        try {
            uri = Uri.parse(filePath);

        } catch (NullPointerException e) {
            return null;
        }
        String url = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_URL));
        String fileName = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_FILENAME));
        String description = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_DESCRIPTION));
        String mimeType = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_MIME_TYPE));
        long totalBytes = cursor.getLong(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_TOTAL_BYTES));
        boolean wifiOnly = cursor.getInt(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_WIFI_ONLY)) > 0;
        int statusCode = cursor.getInt(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_STATUS_CODE));
        int numPieces = cursor.getInt(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_NUM_PIECES));
        boolean retry = cursor.getInt(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_RETRY)) > 0;
        boolean partialSupport = cursor.getInt(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_PARTIAL_SUPPORT)) > 0;
        String errMsg = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_STATUS_MSG));

        DownloadInfo info = new DownloadInfo(uuid, uri, url, fileName, mimeType);
        try {
            info.setNumPieces(numPieces);

        } catch (Exception e) {
            return null;
        }
        info.setTotalBytes(totalBytes);
        info.setDescription(description);
        info.setWiFiOnly(wifiOnly);
        info.setStatusCode(statusCode);
        info.setStatusMsg(errMsg);
        info.setRetry(retry);
        info.setPartialSupport(partialSupport);

        return info;
    }

    private Map.Entry<String, String> cursorToHeader(Cursor cursor, ColumnIndexCache indexCache)
    {
        String name = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_HEADER_NAME));
        String val = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_HEADER_VAL));

        return new AbstractMap.SimpleEntry<>(name, val);
    }

    private DownloadPiece cursorToPiece(Cursor cursor, ColumnIndexCache indexCache)
    {
        String infoId = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_INFO_ID));
        UUID uuid;
        try {
            uuid = UUID.fromString(infoId);

        } catch (IllegalArgumentException e) {
            return null;
        }
        int index = cursor.getInt(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_PIECE_INDEX));
        long size = cursor.getLong(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_PIECE_SIZE));
        long curBytes = cursor.getLong(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_PIECE_CUR_BYTES));
        int numFailed = cursor.getInt(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_PIECE_NUM_FAILED));
        String errMsg = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_PIECE_STATUS_MSG));
        int statusCode = cursor.getInt(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_DOWNLOAD_PIECE_STATUS_CODE));

        DownloadPiece piece = new DownloadPiece(uuid, index, size, curBytes);
        piece.setNumFailed(numFailed);
        piece.setStatusMsg(errMsg);
        piece.setStatusCode(statusCode);

        return piece;
    }

    private void takeUriPermission(DownloadInfo info)
    {
        int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        context.getContentResolver().takePersistableUriPermission(info.getFilePath(), takeFlags);
    }

    private void releaseUriPermission(DownloadInfo info)
    {
        int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        context.getContentResolver().releasePersistableUriPermission(info.getFilePath(), takeFlags);
    }

    /*
     * Using a cache to speed up data retrieval from Cursors.
     */

    private class ColumnIndexCache
    {
        private ArrayMap<String, Integer> map = new ArrayMap<>();

        public int getColumnIndex(Cursor cursor, String columnName)
        {
            if (!map.containsKey(columnName))
                map.put(columnName, cursor.getColumnIndex(columnName));

            return map.get(columnName);
        }

        public void clear()
        {
            map.clear();
        }
    }
}
