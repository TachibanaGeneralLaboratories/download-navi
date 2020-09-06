/*
 * Copyright (C) 2020 Tachibana General Laboratories, LLC
 * Copyright (C) 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import androidx.annotation.NonNull;

import com.tachibana.downloader.core.model.data.entity.BrowserBookmark;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;

public class BrowserRepositoryImpl implements BrowserRepository
{
    private AppDatabase db;

    public BrowserRepositoryImpl(@NonNull AppDatabase db)
    {
        this.db = db;
    }

    @Override
    public Single<Long> addBookmark(BrowserBookmark bookmark)
    {
        return db.browserBookmarksDao().add(bookmark);
    }

    @Override
    public Single<Integer> deleteBookmarks(List<BrowserBookmark> bookmarks)
    {
        return db.browserBookmarksDao().delete(bookmarks);
    }

    @Override
    public Single<Integer> updateBookmark(BrowserBookmark bookmark)
    {
        return db.browserBookmarksDao().update(bookmark);
    }

    @Override
    public Single<BrowserBookmark> getBookmarkByUrlSingle(String url)
    {
        return db.browserBookmarksDao().getByUrlSingle(url);
    }

    @Override
    public Flowable<List<BrowserBookmark>> observeAllBookmarks()
    {
        return db.browserBookmarksDao().observeAll();
    }
}
