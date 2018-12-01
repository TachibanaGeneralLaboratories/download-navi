/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of DownloadNavi.
 *
 * DownloadNavi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DownloadNavi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DownloadNavi.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tachibana.downloader.core.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.storage.StorageManager;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStatVfs;
import android.text.TextUtils;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FileUtils
{
    @SuppressWarnings("unused")
    private static final String TAG = FileUtils.class.getSimpleName();

    /*
     * See http://man7.org/linux/man-pages/man2/lseek.2.html
     */

    public static void lseek(FileOutputStream fout, long offset) throws IOException
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Os.lseek(fout.getFD(), offset, OsConstants.SEEK_SET);

            } catch (Exception e) {
                throw new IOException(e);
            }
        } else {
            fout.getChannel().position(offset);
        }
    }

    /*
     * See http://man7.org/linux/man-pages/man2/ftruncate.2.html
     */

    public static void ftruncate(FileOutputStream fout, long length) throws IOException
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Os.ftruncate(fout.getFD(), length);

            } catch (Exception e) {
                throw new IOException(e);
            }
        } else {
            fout.getChannel().truncate(length);
        }
    }

    /*
     * See http://man7.org/linux/man-pages/man3/posix_fallocate.3.html
     *
     * Only for API 21 and above
     */


    public static void fallocate(Context context, FileDescriptor fd, long length) throws IOException
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            StorageManager storageManager = (StorageManager)context.getSystemService(Context.STORAGE_SERVICE);
            storageManager.allocateBytes(fd, length);

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                long curSize = Os.fstat(fd).st_size;
                long newBytes = length - curSize;
                long availBytes = getAvailableBytes(fd);
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
    }

    public static void closeQuietly(Closeable closeable)
    {
        try {
            if (closeable != null)
                closeable.close();
        } catch (final IOException e) {
            /* Ignore */
        }
    }

    /*
     * Return the number of bytes that are free on the file system
     * backing the given FileDescriptor
     *
     * TODO: maybe there is analog for KitKat?
     */

    @TargetApi(21)
    public static long getAvailableBytes(FileDescriptor fd) throws IOException
    {
        try {
            StructStatVfs stat = Os.fstatvfs(fd);

            return stat.f_bavail * stat.f_bsize;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /*
     * Check if given filename is valid for a FAT filesystem
     */

    public static boolean isValidFatFilename(String name)
    {
        return name != null && name.equals(buildValidFatFilename(name));
    }

    /*
     * Mutate the given filename to make it valid for a FAT filesystem,
     * replacing any invalid characters with "_"
     */

    public static String buildValidFatFilename(String name)
    {
        if (TextUtils.isEmpty(name) || ".".equals(name) || "..".equals(name))
            return "(invalid)";

        final StringBuilder res = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (isValidFatFilenameChar(c))
                res.append(c);
            else
                res.append('_');
        }
        /*
         * Even though vfat allows 255 UCS-2 chars, we might eventually write to
         * ext4 through a FUSE layer, so use that limit
         */
        trimFilename(res, 255);

        return res.toString();
    }

    private static boolean isValidFatFilenameChar(char c)
    {
        if ((0x00 <= c && c <= 0x1f))
            return false;
        switch (c) {
            case '"':
            case '*':
            case '/':
            case ':':
            case '<':
            case '>':
            case '?':
            case '\\':
            case '|':
            case 0x7F:
                return false;
            default:
                return true;
        }
    }

    private static void trimFilename(StringBuilder res, int maxBytes)
    {
        byte[] raw = res.toString().getBytes(StandardCharsets.UTF_8);
        if (raw.length > maxBytes) {
            maxBytes -= 3;
            while (raw.length > maxBytes) {
                res.deleteCharAt(res.length() / 2);
                raw = res.toString().getBytes(StandardCharsets.UTF_8);
            }
            res.insert(res.length() / 2, "...");
        }
    }
}
