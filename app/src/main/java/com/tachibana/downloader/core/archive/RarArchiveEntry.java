/*
 * Copyright (C) 2022 Tachibana General Laboratories, LLC
 * Copyright (C) 2022 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.core.archive;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.junrar.rarfile.FileHeader;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipEncoding;

import java.io.IOException;
import java.util.Date;

class RarArchiveEntry implements ArchiveEntry {
    private final FileHeader header;
    private final ZipEncoding zipEncoding;
    private final String name;

    public RarArchiveEntry(@NonNull FileHeader header, @NonNull ZipEncoding zipEncoding) throws IOException {
        this.header = header;
        this.zipEncoding = zipEncoding;

        var name = header.getFileNameW();
        if (TextUtils.isEmpty(name)) {
            name = zipEncoding.decode(header.getFileNameByteArray());
        }
        name = name.replace('\\', '/');
        this.name = name;
    }

    public FileHeader getHeader() {
        return header;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getSize() {
        return header.getFullUnpackSize();
    }

    @Override
    public boolean isDirectory() {
        return header.isDirectory();
    }

    @Override
    public Date getLastModifiedDate() {
        return header.getMTime();
    }
}
