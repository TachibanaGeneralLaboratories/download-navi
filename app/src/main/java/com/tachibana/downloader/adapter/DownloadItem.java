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

package com.tachibana.downloader.adapter;

import com.tachibana.downloader.core.entity.InfoAndPieces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/*
 * Wrapper of InfoAndPieces class for DownloadListAdapter, that override Object::equals method
 * Necessary for other behavior in case if item was selected (see SelectionTracker).
 */

public class DownloadItem extends InfoAndPieces
{
    public DownloadItem(@NonNull InfoAndPieces infoAndPieces)
    {
        this.info = infoAndPieces.info;
        this.pieces = infoAndPieces.pieces;
    }

    /*
     * Compare objects by their content (info, pieces)
     */

    public boolean equalsContent(DownloadItem item)
    {
        return super.equals(item);
    }

    /*
     * Compare objects by info id
     */

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (!(o instanceof DownloadItem))
            return false;

        if (o == this)
            return true;

        return info.id.equals(((DownloadItem)o).info.id);
    }
}
