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

package com.tachibana.downloader.ui.browser.bookmarks;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.tachibana.downloader.core.RepositoryHelper;
import com.tachibana.downloader.core.model.data.entity.BrowserBookmark;
import com.tachibana.downloader.core.storage.BrowserRepository;

import java.util.Collections;

import io.reactivex.Completable;
import io.reactivex.Single;

public class EditBookmarkViewModel extends AndroidViewModel
{
    BrowserRepository repo;

    public EditBookmarkViewModel(@NonNull Application application)
    {
        super(application);

        repo = RepositoryHelper.getBrowserRepository(application);
    }

    Completable applyChanges(@NonNull BrowserBookmark oldBookmark,
                             @NonNull BrowserBookmark newBookmark)
    {
        if (!oldBookmark.url.equals(newBookmark.url)) {
            return repo.deleteBookmarks(Collections.singletonList(oldBookmark))
                    .flatMap((__) -> repo.addBookmark(newBookmark))
                    .ignoreElement();
        } else {
            return repo.updateBookmark(newBookmark).ignoreElement();
        }
    }

    Single<Integer> deleteBookmark(@NonNull BrowserBookmark bookmark)
    {
        return repo.deleteBookmarks(Collections.singletonList(bookmark));
    }
}
