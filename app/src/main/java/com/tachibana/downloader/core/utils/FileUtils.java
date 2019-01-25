/*
 * Copyright (C) 2018, 2019 Tachibana General Laboratories, LLC
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>ru>
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

package com.tachibana.downloader.core.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStatVfs;
import android.text.TextUtils;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import androidx.annotation.NonNull;

public class FileUtils
{
    @SuppressWarnings("unused")
    private static final String TAG = FileUtils.class.getSimpleName();

    public static final char EXTENSION_SEPARATOR = '.';

    /*
     * Release read and write permissions for SAF files
     */

    public static void releaseUriPermission(@NonNull Context context, @NonNull Uri path)
    {
        if (!isSAFPath(path))
            return;

        int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        context.getContentResolver().releasePersistableUriPermission(path, takeFlags);
    }

    /*
     * Take read and write permissions for SAF files
     */

    public static void takeUriPermission(@NonNull Context context, @NonNull Uri path)
    {
        if (!isSAFPath(path))
            return;

        int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        context.getContentResolver().takePersistableUriPermission(path, takeFlags);
    }

    public static boolean deleteFile(@NonNull Context context,
                                     @NonNull Uri path) throws FileNotFoundException
    {
        if (isFileSystemPath(path)) {
            String fileSystemPath = path.getPath();
            if (fileSystemPath == null)
                return false;
            return new File(fileSystemPath).delete();

        } else {
            return DocumentsContract.deleteDocument(context.getContentResolver(), path);
        }
    }

    /*
     * Return true if the uri is a simple filesystem path
     */

    public static boolean isFileSystemPath(@NonNull Uri uri)
    {
        String scheme = uri.getScheme();
        if (scheme == null)
            throw new IllegalArgumentException("Scheme of " + uri.getPath() + " is null");

        return scheme.equals("file");
    }

    /*
     * Return true if the uri is a SAF path
     */

    public static boolean isSAFPath(@NonNull Uri uri)
    {
        String scheme = uri.getScheme();
        if (scheme == null)
            throw new IllegalArgumentException("Scheme of " + uri.getPath() + " is null");

        return scheme.equals("content");
    }

    public static String getExtension(String fileName)
    {
        if (fileName == null)
            return null;

        int extensionPos = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        int lastSeparator = fileName.lastIndexOf(File.separator);
        int index = (lastSeparator > extensionPos ? -1 : extensionPos);

        if (index == -1)
            return "";
        else
            return fileName.substring(index + 1);
    }

    /*
     * Return path to the standard Download directory.
     * If the directory doesn't exist, the function creates it automatically.
     */

    public static String getDefaultDownloadPath()
    {
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .getAbsolutePath();

        File dir = new File(path);
        if (dir.exists() && dir.isDirectory())
            return path;
        else
            return dir.mkdirs() ? path : "";
    }

    /*
     * Returns all shared/external storage devices where the application can place files it owns.
     * For Android below 4.4 returns only primary storage (standard download path).
     */

    public static ArrayList<String> getStorageList(Context context)
    {
        ArrayList<String> storages = new ArrayList<>();
        storages.add(Environment.getExternalStorageDirectory().getAbsolutePath());

        String altPath = altExtStoragePath();
        if (!TextUtils.isEmpty(altPath))
            storages.add(altPath);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            /*
             * First volume returned by getExternalFilesDirs is always primary storage,
             * or emulated. Further entries, if they exist, will be secondary or external SD,
             * see http://www.doubleencore.com/2014/03/android-external-storage/
             */
            File[] filesDirs = context.getExternalFilesDirs(null);

            if (filesDirs != null) {
                /* Skip primary storage */
                for (int i = 1; i < filesDirs.length; i++) {
                    if (filesDirs[i] != null) {
                        if (filesDirs[i].exists())
                            storages.add(filesDirs[i].getAbsolutePath());
                        else
                            Log.w(TAG, "Unexpected external storage: " + filesDirs[i].getAbsolutePath());
                    }
                }
            }
        }

        return storages;
    }

    private  static String altExtStoragePath()
    {
        File extdir = new File("/storage/sdcard1");
        String path = "";
        if (extdir.exists() && extdir.isDirectory()) {
            File[] contents = extdir.listFiles();
            if (contents != null && contents.length > 0)
                path = extdir.toString();
        }

        return path;
    }

    /*
     * Return the primary shared/external storage directory.
     */

    public static String getUserDirPath()
    {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath();

        File dir = new File(path);
        if (dir.exists() && dir.isDirectory())
            return path;
        else
            return dir.mkdirs() ? path : "";
    }

    /*
     * See http://man7.org/linux/man-pages/man2/lseek.2.html
     */

    public static void lseek(@NonNull FileOutputStream fout, long offset) throws IOException
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

    public static void ftruncate(@NonNull FileOutputStream fout, long length) throws IOException
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


    public static void fallocate(@NonNull Context context, @NonNull FileDescriptor fd, long length) throws IOException
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
    public static long getAvailableBytes(@NonNull FileDescriptor fd) throws IOException
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
