/*
 * Copyright (C) 2018, 2019 Tachibana General Laboratories, LLC
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.core.system.filesystem;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStatVfs;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tachibana.downloader.core.exception.FileAlreadyExistsException;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

public class FileSystemFacadeImpl implements FileSystemFacade
{
    @SuppressWarnings("unused")
    private static final String TAG = FileSystemFacadeImpl.class.getSimpleName();

    private static final String EXTENSION_SEPARATOR = ".";
    /* The file copy buffer size (30 MB) */
    private static final long FILE_COPY_BUFFER_SIZE = 1024 * 1024 * 30;

    private Context appContext;

    public FileSystemFacadeImpl(@NonNull Context appContext)
    {
        this.appContext = appContext;
    }

    /*
     * See http://man7.org/linux/man-pages/man2/lseek.2.html
     */

    @Override
    public void lseek(@NonNull FileOutputStream fout, long offset) throws IOException
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
     * See http://man7.org/linux/man-pages/man3/posix_fallocate.3.html
     */

    @Override
    @TargetApi(21)
    public void fallocate(@NonNull FileDescriptor fd, long length) throws IOException
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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

    @Override
    public void closeQuietly(Closeable closeable)
    {
        try {
            if (closeable != null)
                closeable.close();

        } catch (final IOException e) {
            /* Ignore */
        }
    }

    /*
     * If file with required name exists returns new filename in the following format:
     *
     *     base_name (count_number).extension
     *
     * otherwise returns original filename
     */

    public String makeFilename(@NonNull Uri dir,
                               @NonNull String desiredFileName)
    {
        while (true) {
            /* File doesn't exists, return */
            Uri filePath = getFileUri(dir, desiredFileName);
            if (filePath == null)
                return desiredFileName;

            String fileName;
            if (isFileSystemPath(filePath)) {
                fileName = new File(filePath.getPath()).getName();

            } else {
                SafFileSystem fs = SafFileSystem.getInstance(appContext);
                SafFileSystem.Stat stat = fs.stat(filePath);

                fileName = (stat == null || stat.name == null ? desiredFileName : stat.name);
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
                        .append(getExtension(fileName));

            desiredFileName = sb.toString();
        }
    }

    /*
     * Returns Uri and name of moved file.
     */

    @Override
    public void moveFile(@NonNull Uri srcDir,
                         @NonNull String srcFileName,
                         @NonNull Uri destDir,
                         @NonNull String destFileName,
                         boolean replace) throws IOException, FileAlreadyExistsException
    {
        SafFileSystem fs = SafFileSystem.getInstance(appContext);
        Uri srcFileUri, destFileUri;

        if (isFileSystemPath(srcDir)) {
            File srcFile = new File(srcDir.getPath(), srcFileName);
            if (!srcFile.exists())
                throw new FileNotFoundException(srcFile.getAbsolutePath());

            srcFileUri = Uri.fromFile(srcFile);

        } else {
            srcFileUri = fs.getFileUri(srcDir, srcFileName, false);
            if (srcFileUri == null)
                throw new FileNotFoundException("Source '" + srcFileName + "' from " + srcDir + " does not exists");
        }

        if (isFileSystemPath(destDir)) {
            File destFile = new File(destDir.getPath(), destFileName);
            if (destFile.exists() && !replace)
                throw new FileAlreadyExistsException("Destination '" + destFile + "' already exists");

        } else {
            destFileUri = fs.getFileUri(destDir, destFileName, replace);
            if (!replace && destFileUri != null)
                throw new FileAlreadyExistsException("Destination '" + destFileUri + "' already exists");
        }

        destFileUri = createFile(destDir, destFileName, false);
        if (destFileUri == null)
            throw new IOException("Cannot create destination file '" + destFileName + "'");

        copyFile(srcFileUri, destFileUri, replace);
        deleteFile(srcFileUri);
    }

    /*
     * This caches the original file length, and throws an IOException
     * if the output file length is different from the current input file length.
     * So it may fail if the file changes size.
     * It may also fail with "IllegalArgumentException: Negative size" if the input file is truncated part way
     * through copying the data and the new file size is less than the current position.
     */

    public void copyFile(@NonNull Uri srcFile,
                         @NonNull Uri destFile,
                         boolean truncateDestFile) throws IOException
    {

        if (srcFile.equals(destFile))
            throw new IllegalArgumentException("Uri points to the same file");

        try (FileDescriptorWrapper wSrc = getFD(srcFile);
             FileDescriptorWrapper wDest = getFD(destFile)) {

            try (FileInputStream fin = new FileInputStream(wSrc.open("r"));
                 FileOutputStream fout = new FileOutputStream(wDest.open((truncateDestFile ? "rwt" : "rw")));
                 FileChannel input = fin.getChannel();
                 FileChannel output = fout.getChannel())
            {
                long size = input.size();
                long pos = 0;
                long count;
                while (pos < size) {
                    long remain = size - pos;
                    count = (remain > FILE_COPY_BUFFER_SIZE ? FILE_COPY_BUFFER_SIZE : remain);
                    long bytesCopied = output.transferFrom(input, pos, count);
                    if (bytesCopied == 0)
                        break;
                    pos += bytesCopied;
                }

                long srcLen = input.size();
                long dstLen = output.size();
                if (srcLen != dstLen)
                    throw new IOException("Failed to copy full contents from '" +
                            srcFile + "' to '" + destFile + "' Expected length: " + srcLen + " Actual: " + dstLen);
            }
        }
    }

    @Override
    public FileDescriptorWrapper getFD(@NonNull Uri path)
    {
        return new FileDescriptorWrapperImpl(appContext, path);
    }

    @Override
    public String getExtensionSeparator()
    {
        return EXTENSION_SEPARATOR;
    }

    @Override
    public String appendExtension(@NonNull String fileName, @NonNull String mimeType)
    {
        String extension = null;
        if (TextUtils.isEmpty(getExtension(fileName)))
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);

        if (extension != null && !fileName.endsWith(extension))
            fileName += getExtensionSeparator() + extension;

        return fileName;
    }

    /*
     * Return path to the standard Download directory.
     * If the directory doesn't exist, the function creates it automatically.
     */

    @Override
    @Nullable
    public String getDefaultDownloadPath()
    {
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .getAbsolutePath();

        File dir = new File(path);
        if (dir.exists() && dir.isDirectory())
            return path;
        else
            return dir.mkdirs() ? path : null;
    }

    @Override
    public String altExtStoragePath()
    {
        File extdir = new File("/storage/sdcard1");
        String path = "";
        if (extdir.exists() && extdir.isDirectory()) {
            File[] contents = extdir.listFiles();
            if (contents != null && contents.length > 0) {
                path = extdir.toString();
            }
        }
        return path;
    }

    /*
     * Return the primary shared/external storage directory.
     */

    @Override
    @Nullable
    public String getUserDirPath()
    {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath();

        File dir = new File(path);
        if (dir.exists() && dir.isDirectory())
            return path;
        else
            return dir.mkdirs() ? path : null;
    }

    /*
     * Return true if the uri is a SAF path
     */

    @Override
    public boolean isSafPath(@NonNull Uri path)
    {
        return SafFileSystem.getInstance(appContext).isSafPath(path);
    }

    /*
     * Return true if the uri is a simple filesystem path
     */

    @Override
    public boolean isFileSystemPath(@NonNull Uri path)
    {
        String scheme = path.getScheme();
        if (scheme == null)
            throw new IllegalArgumentException("Scheme of " + path.getPath() + " is null");

        return scheme.equals("file");
    }

    @Override
    public boolean deleteFile(@NonNull Uri path) throws FileNotFoundException
    {
        if (isFileSystemPath(path)) {
            String fileSystemPath = path.getPath();
            if (fileSystemPath == null)
                return false;
            return new File(fileSystemPath).delete();

        } else {
            SafFileSystem fs = SafFileSystem.getInstance(appContext);
            return fs.delete(path);
        }
    }

    /*
     * Returns a file (if exists) Uri by name from the pointed directory
     */

    @Override
    public Uri getFileUri(@NonNull Uri dir,
                          @NonNull String fileName)
    {
        if (isFileSystemPath(dir)) {
            File f = new File(dir.getPath(), fileName);

            return (f.exists() ? Uri.fromFile(f) : null);

        } else {
            return SafFileSystem.getInstance(appContext)
                    .getFileUri(dir, fileName, false);
        }
    }

    /*
     * Returns a file (if exists) Uri by relative path (e.g foo/bar.txt)
     * from the pointed directory
     */

    @Override
    public Uri getFileUri(@NonNull String relativePath,
                          @NonNull Uri dir)
    {
        if (isFileSystemPath(dir)) {
            File f = new File(dir.getPath() + File.separator + relativePath);

            return (f.exists() ? Uri.fromFile(f) : null);

        } else {
            return SafFileSystem.getInstance(appContext)
                    .getFileUri(new SafFileSystem.FakePath(dir, relativePath), false);
        }
    }

    /*
     * Returns Uri of created file.
     * Note: if replace == false, doesn't replace file if it exists and returns its Uri.
     */

    @Override
    public Uri createFile(@NonNull Uri dir,
                          @NonNull String fileName,
                          boolean replace) throws IOException
    {
        if (isFileSystemPath(dir)) {
            File f = new File(dir.getPath(), fileName);
            try {
                if (f.exists()) {
                    if (!replace)
                        return Uri.fromFile(f);
                    else if (!f.delete())
                        return null;
                }
                if (!f.createNewFile())
                    return null;

            } catch (IOException | SecurityException e) {
                throw new IOException(e);
            }

            return Uri.fromFile(f);

        } else {
            SafFileSystem fs = SafFileSystem.getInstance(appContext);
            Uri path = fs.getFileUri(dir, fileName, false);
            if (replace && path != null) {
                if (!fs.delete(path))
                    return null;
                path = fs.getFileUri(dir, fileName, true);
            }

            if (path == null)
                throw new IOException("Unable to create file {name=" + fileName + ", dir=" + dir + "}");

            return path;
        }
    }

    @Override
    public String makeFileSystemPath(@NonNull Uri uri,
                                     String relativePath)
    {
        if (isSafPath(uri))
            return new SafFileSystem.FakePath(uri, (relativePath == null ? "" : relativePath))
                    .toString();
        else
            return uri.getPath();
    }

    /*
     * Return the number of bytes that are free on the file system
     * backing the given FileDescriptor
     *
     * TODO: maybe there is analog for KitKat?
     */

    @Override
    @TargetApi(21)
    public long getAvailableBytes(@NonNull FileDescriptor fd) throws IOException
    {
        try {
            StructStatVfs stat = Os.fstatvfs(fd);

            return stat.f_bavail * stat.f_bsize;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public long getDirAvailableBytes(@NonNull Uri dir)
    {
        long availableBytes = -1;

        if (isSafPath(dir)) {
            SafFileSystem fs = SafFileSystem.getInstance(appContext);
            Uri dirPath = fs.makeSafRootDir(dir);
            try (FileDescriptorWrapper w = getFD(dirPath)) {
                availableBytes = getAvailableBytes(w.open("r"));

            } catch (IllegalArgumentException | IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));
                return availableBytes;
            }

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
        }

        return availableBytes;
    }

    @Override
    public String getExtension(String fileName)
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
     * Check if given filename is valid for a FAT filesystem
     */

    @Override
    public boolean isValidFatFilename(String name)
    {
        return name != null && name.equals(buildValidFatFilename(name));
    }

    /*
     * Mutate the given filename to make it valid for a FAT filesystem,
     * replacing any invalid characters with "_"
     */

    @Override
    public String buildValidFatFilename(String name)
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

    private boolean isValidFatFilenameChar(char c)
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

    private void trimFilename(StringBuilder res, int maxBytes)
    {
        byte[] raw = res.toString().getBytes(Charset.forName("UTF-8"));
        if (raw.length > maxBytes) {
            maxBytes -= 3;
            while (raw.length > maxBytes) {
                res.deleteCharAt(res.length() / 2);
                raw = res.toString().getBytes(Charset.forName("UTF-8"));
            }
            res.insert(res.length() / 2, "...");
        }
    }

    /*
     * Returns path if the directory belongs to the filesystem,
     * otherwise returns SAF name
     */

    @Override
    public String getDirName(@NonNull Uri dir)
    {
        if (isFileSystemPath(dir))
            return dir.getPath();

        SafFileSystem.Stat stat = SafFileSystem.getInstance(appContext).statSafRoot(dir);

        return (stat == null || stat.name == null ? dir.getPath() : stat.name);
    }
}
