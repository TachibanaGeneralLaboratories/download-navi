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

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.tachibana.downloader.core.model.data.entity.BrowserBookmark;
import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.model.data.entity.DownloadPiece;
import com.tachibana.downloader.core.model.data.entity.Header;
import com.tachibana.downloader.core.model.data.entity.UserAgent;
import com.tachibana.downloader.core.storage.converter.UUIDConverter;
import com.tachibana.downloader.core.storage.dao.BrowserBookmarksDao;
import com.tachibana.downloader.core.storage.dao.DownloadDao;
import com.tachibana.downloader.core.storage.dao.UserAgentDao;
import com.tachibana.downloader.core.system.SystemFacade;
import com.tachibana.downloader.core.system.SystemFacadeHelper;
import com.tachibana.downloader.core.utils.UserAgentUtils;

import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;

@Database(entities = {DownloadInfo.class,
        DownloadPiece.class,
        Header.class,
        UserAgent.class,
        BrowserBookmark.class},
        version = 5)
@TypeConverters({UUIDConverter.class})
public abstract class AppDatabase extends RoomDatabase
{
    private static final String DATABASE_NAME = "tachibana_downloader.db";

    private static volatile AppDatabase INSTANCE;

    public abstract DownloadDao downloadDao();

    public abstract UserAgentDao userAgentDao();

    public abstract BrowserBookmarksDao browserBookmarksDao();

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
                    public void onCreate(@NonNull SupportSQLiteDatabase db)
                    {
                        super.onCreate(db);
                        Completable.fromAction(() -> {
                            AppDatabase database = AppDatabase.getInstance(appContext);
                            database.runInTransaction(() -> {
                                SystemFacade systemFacade = SystemFacadeHelper.getSystemFacade(appContext);
                                String userAgentStr = systemFacade.getSystemUserAgent();

                                UserAgent systemUserAgent;
                                if (userAgentStr == null)
                                    systemUserAgent = UserAgentUtils.defaultUserAgents[0];
                                else
                                    systemUserAgent = new UserAgent(userAgentStr);
                                systemUserAgent.readOnly = true;

                                database.userAgentDao().add(systemUserAgent);
                                database.userAgentDao().add(UserAgentUtils.defaultUserAgents);
                            });
                            database.setDatabaseCreated();
                        })
                       .subscribeOn(Schedulers.io())
                       .subscribe();
                    }
                })
                .addMigrations(DatabaseMigration.getMigrations())
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