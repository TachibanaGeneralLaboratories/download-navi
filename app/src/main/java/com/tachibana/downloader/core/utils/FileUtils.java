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
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStatVfs;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

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
     * Returns a file (if exists) Uri by name from the pointed directory
     */

    public static Uri getFileUri(@NonNull Context context,
                                 @NonNull Uri dir,
                                 @NonNull String fileName)
    {
        if (isFileSystemPath(dir)) {
            File f = new File(dir.getPath(), fileName);

            return (f.exists() ? Uri.fromFile(f) : null);

        } else {
            DocumentFile tree = DocumentFile.fromTreeUri(context, dir);
            DocumentFile f;
            try {
                f = tree.findFile(fileName);

            } catch (UnsupportedOperationException e) {
                return null;
            }

            return (f != null ? f.getUri() : null);
        }
    }

    /*
     * Returns Uri and name of created file.
     * Note: if replace == false, doesn't replace file if it exists and returns its Uri.
     *       Storage Access Framework can change the name after creating the file
     *       (e.g. extension), please check it after returning.
     */

    public static Pair<Uri, String> createFile(@NonNull Context context,
                                               @NonNull Uri dir,
                                               @NonNull String desiredFileName,
                                               @NonNull String mimeType,
                                               boolean replace) throws IOException
    {
        if (isFileSystemPath(dir)) {
            File f = new File(dir.getPath(), desiredFileName);
            try {
                if (f.exists()) {
                    if (!replace)
                        return Pair.create(Uri.fromFile(f), desiredFileName);
                    else if (!f.delete())
                        return null;
                }
                if (!f.createNewFile())
                    return null;

            } catch (IOException | SecurityException e) {
                throw new IOException(e);
            }

            return Pair.create(Uri.fromFile(f), desiredFileName);

        } else {
            DocumentFile tree = DocumentFile.fromTreeUri(context, dir);
            DocumentFile f;
            try {
                f = tree.findFile(desiredFileName);
                if (f != null) {
                    if (!replace)
                        return Pair.create(f.getUri(), desiredFileName);
                    else if (!DocumentsContract.deleteDocument(context.getContentResolver(), f.getUri()))
                        return null;
                }
                f = tree.createFile(mimeType, desiredFileName);

            } catch (UnsupportedOperationException e) {
                throw new IOException(e);
            }
            if (f == null)
                throw new IOException("Unable to create file {name=" + desiredFileName + ", dir=" + dir + "}");

            /* Maybe an extension was added to the file name */
            String newName = f.getName();

            return Pair.create(f.getUri(), (newName == null ? desiredFileName : newName));
        }
    }

    /*
     * If file with required name exists returns new filename in the following format:
     *
     *     base_name (count_number).extension
     *
     * otherwise returns original filename
     */

    public static String makeFilename(@NonNull Context context,
                                      @NonNull Uri dir,
                                      @NonNull String desiredFileName)
    {
        while (true) {
            /* File doesn't exists, return */
            Uri filePath = getFileUri(context, dir, desiredFileName);
            if (filePath == null)
                return desiredFileName;

            String fileName;
            if (isFileSystemPath(filePath)) {
                fileName = new File(filePath.getPath()).getName();
            } else {
                DocumentFile f = DocumentFile.fromSingleUri(context, filePath);
                String name = f.getName();
                fileName = (name == null ? desiredFileName : name);
            }

            int openBracketPos = fileName.lastIndexOf("(");
            int closeBracketPos = fileName.lastIndexOf(")");

            /* Try to parse the counter number and increment it for a new filename */
            int countNumber;
            if (openBracketPos > 0 && closeBracketPos > 0) {
                try {
                    countNumber = Integer.parseInt(fileName.substring(openBracketPos + 1, closeBracketPos));

                    desiredFileName = fileName.substring(0, openBracketPos + 1) +
                            ++countNumber + fileName.substring(closeBracketPos);
                    continue;

                } catch (NumberFormatException e) {
                    /* Ignore */
                }
            }

            /* Otherwise create a name with the initial value of the counter */
            countNumber = 1;
            int extensionPos = fileName.lastIndexOf(EXTENSION_SEPARATOR);
            String baseName = (extensionPos < 0 ? fileName : fileName.substring(0, extensionPos));

            StringBuilder sb = new StringBuilder(baseName + " (" + countNumber + ")");
            if (extensionPos > 0)
                sb.append(EXTENSION_SEPARATOR)
                  .append(FileUtils.getExtension(fileName));

            desiredFileName = sb.toString();
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
            throw new IllegalArgumentException("Scheme of " + uri + " is null");

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

    public static long getDirAvailableBytes(@NonNull Context context,
                                            @NonNull Uri dir)
    {
        long availableBytes = -1;

        if (isFileSystemPath(dir)) {
            try {
                File file = new File(dir.getPath());
                availableBytes = file.getUsableSpace();

            } catch (Exception e) {
                /* This provides invalid space on some devices */
                try {
                    StatFs stat = new StatFs(dir.getPath());

                    availableBytes = stat.getAvailableBytes();
                } catch (Exception ee) {
                    Log.e(TAG, Log.getStackTraceString(e));
                    return availableBytes;
                }
            }

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Uri pseudoDirPath = DocumentsContract.buildDocumentUriUsingTree(dir,
                    DocumentsContract.getTreeDocumentId(dir));
            try {
                ParcelFileDescriptor pfd = context.getContentResolver()
                        .openFileDescriptor(pseudoDirPath, "r");
                availableBytes = getAvailableBytes(pfd.getFileDescriptor());

            } catch (IllegalArgumentException | IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));
                return availableBytes;
            }
        }

        return availableBytes;
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
