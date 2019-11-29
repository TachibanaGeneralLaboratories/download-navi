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

package com.tachibana.downloader.core.system;

import android.annotation.TargetApi;
import android.os.Build;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStatVfs;

import androidx.annotation.NonNull;

import java.io.FileDescriptor;
import java.io.IOException;

class SysCallImpl implements SysCall
{
    @Override
    public void lseek(@NonNull FileDescriptor fd, long offset) throws IOException, UnsupportedOperationException
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Os.lseek(fd, offset, OsConstants.SEEK_SET);

            } catch (Exception e) {
                throw new IOException(e);
            }

        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    @TargetApi(21)
    public void fallocate(@NonNull FileDescriptor fd, long length) throws IOException
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return;

        try {
            long curSize = Os.fstat(fd).st_size;
            long newBytes = length - curSize;
            long availBytes = availableBytes(fd);
            if (availBytes < newBytes)
                throw new IOException("Not enough free space; " + newBytes + " requested, " +
                        availBytes + " available");

            Os.posix_fallocate(fd, 0, length);

        } catch (Exception e) {
            try {
                Os.ftruncate(fd, length);

            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
    }

    /*
     * Return the number of bytes that are free on the file system
     * backing the given FileDescriptor
     *
     * TODO: maybe there is analog for KitKat?
     */

    @Override
    @TargetApi(21)
    public long availableBytes(@NonNull FileDescriptor fd) throws IOException
    {
        try {
            StructStatVfs stat = Os.fstatvfs(fd);

            return stat.f_bavail * stat.f_bsize;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
