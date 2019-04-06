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

import android.content.Context;

import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.entity.DownloadPiece;
import com.tachibana.downloader.core.entity.Header;
import com.tachibana.downloader.core.entity.UserAgent;
import com.tachibana.downloader.core.storage.converter.UUIDConverter;
import com.tachibana.downloader.core.storage.dao.DownloadDao;
import com.tachibana.downloader.core.storage.dao.UserAgentDao;
import com.tachibana.downloader.core.utils.Utils;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;
import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;

@Database(entities = {DownloadInfo.class,
        DownloadPiece.class,
        Header.class,
        UserAgent.class},
        version = 1)
@TypeConverters({UUIDConverter.class})
public abstract class AppDatabase extends RoomDatabase
{
    private static final String DATABASE_NAME = "tachibana_downloader.db";

    private static AppDatabase INSTANCE;

    public abstract DownloadDao downloadDao();

    public abstract UserAgentDao userAgentDao();

    private static final UserAgent[] defaultUserAgents = new UserAgent[] {
            new UserAgent("Mozilla/5.0 (Linux; U; Android 4.1; en-us; DV Build/Donut)"),
            new UserAgent("Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1; Trident/6.0)"),
            new UserAgent("Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.113 Safari/537.36"),
            new UserAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:54.0) Gecko/20100101 Firefox/54.0"),
            new UserAgent("Opera/9.80 (Windows NT 6.1) Presto/2.12.388 Version/12.17"),
            new UserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/603.3.8 (KHTML, like Gecko) Version/10.1.2 Safari/603.3.8"),
    };

    private final MutableLiveData<Boolean> isDatabaseCreated = new MutableLiveData<>();

    public static AppDatabase getInstance(Context context)
    {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = buildDatabase(context.getApplicationContext());
                    INSTANCE.updateDatabaseCreated(context.getApplicationContext());
                }
            }
        }

        return INSTANCE;
    }

    private static AppDatabase buildDatabase(Context appContext)
    {
        return Room.databaseBuilder(appContext, AppDatabase.class, DATABASE_NAME)
                .addCallback(new Callback() {
                    @Override
                    public void onCreate(@NonNull SupportSQLiteDatabase db) {
                        super.onCreate(db);
                       Completable.fromAction(() -> {
                            AppDatabase database = AppDatabase.getInstance(appContext);
                            database.runInTransaction(() -> {
                                UserAgent systemUserAgent = new UserAgent(Utils.getSystemUserAgent(appContext));
                                systemUserAgent.readOnly = true;
                                database.userAgentDao().add(systemUserAgent);
                                database.userAgentDao().add(defaultUserAgents);
                            });
                            database.setDatabaseCreated();
                        })
                       .subscribeOn(Schedulers.io())
                       .subscribe();
                    }
                })
                .build();
    }

    /*
     * Check whether the database already exists and expose it via getDatabaseCreated()
     */

    private void updateDatabaseCreated(final Context context)
    {
        if (context.getDatabasePath(DATABASE_NAME).exists())
            setDatabaseCreated();
    }

    private void setDatabaseCreated()
    {
        isDatabaseCreated.postValue(true);
    }


    public LiveData<Boolean> getDatabaseCreated()
    {
        return isDatabaseCreated;
    }
}