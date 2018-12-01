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
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

import androidx.collection.ArrayMap;

/*
 * The class provides a single access to the user agent repository.
 */

public class UserAgentStorage
{
    @SuppressWarnings("unused")
    private static final String TAG = UserAgentStorage.class.getSimpleName();

    private String[] allColumns = {
            DatabaseHelper.COLUMN_ID,
            DatabaseHelper.COLUMN_USER_AGENT_STR
    };

    private Context context;

    public UserAgentStorage(Context context)
    {
        this.context = context;
    }

    public boolean add(String userAgent)
    {
        ContentValues infoValues = new ContentValues();

        infoValues.put(DatabaseHelper.COLUMN_USER_AGENT_STR, userAgent);

        return ConnectionManager.getDatabase(context)
                .replace(DatabaseHelper.USER_AGENT_TABLE, null, infoValues) < 0;
    }

    public void delete(String userAgent)
    {
        ConnectionManager.getDatabase(context)
                .delete(DatabaseHelper.USER_AGENT_TABLE,
                        DatabaseHelper.COLUMN_USER_AGENT_STR + " = ? ",
                        new String[]{ userAgent });
    }

    public List<String> getAll()
    {
        List<String> userAgentList = new ArrayList<>();

        Cursor cursor = ConnectionManager.getDatabase(context).query(DatabaseHelper.USER_AGENT_TABLE,
                allColumns,
                null,
                null,
                null,
                null,
                null);

        ColumnIndexCache indexCache = new ColumnIndexCache();
        while (cursor.moveToNext()) {
            String userAgent = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_USER_AGENT_STR));
            if (userAgent == null)
                continue;
            userAgentList.add(userAgent);
        }
        cursor.close();
        indexCache.clear();

        return userAgentList;
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
