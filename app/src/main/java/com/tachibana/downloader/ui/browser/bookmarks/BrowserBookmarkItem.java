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

import androidx.annotation.NonNull;

import com.tachibana.downloader.core.model.data.entity.BrowserBookmark;

public class BrowserBookmarkItem extends BrowserBookmark implements Comparable<BrowserBookmarkItem>
{
    public BrowserBookmarkItem(@NonNull BrowserBookmark bookmark)
    {
        super(bookmark.url, bookmark.name, bookmark.dateAdded);
    }

    @Override
    public int compareTo(BrowserBookmarkItem o)
    {
        return Long.compare(o.dateAdded, dateAdded);
    }

    public boolean equalsContent(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BrowserBookmark that = (BrowserBookmark) o;

        if (dateAdded != that.dateAdded) return false;
        if (!url.equals(that.url)) return false;
        return name.equals(that.name);
    }
}
