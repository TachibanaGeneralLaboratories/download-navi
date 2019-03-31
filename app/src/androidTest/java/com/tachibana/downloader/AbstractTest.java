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

package com.tachibana.downloader;

import android.Manifest;
import android.content.Context;

import com.tachibana.downloader.core.FakeSystemFacade;
import com.tachibana.downloader.core.storage.AppDatabase;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.GrantPermissionRule;

public class AbstractTest
{
    @Rule
    public GrantPermissionRule runtimePermissionRule = GrantPermissionRule.grant(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE);

    protected Context context;
    protected AppDatabase db;
    protected DataRepository repo;

    @Before
    public void init()
    {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context,
                AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        ((MainApplication)context).setDatabase(db);
        repo = DataRepository.getInstance(db);
        FakeSystemFacade systemFacade = new FakeSystemFacade(context);
        Utils.setSystemFacade(systemFacade);
    }

    @After
    public void finish()
    {
        db.close();
    }
}
