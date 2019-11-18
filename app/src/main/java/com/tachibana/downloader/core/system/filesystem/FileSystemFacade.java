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
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tachibana.downloader.core.exception.FileAlreadyExistsException;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public interface FileSystemFacade
{
    void lseek(@NonNull FileOutputStream fout, long offset) throws IOException;

    @TargetApi(21)
    void fallocate(@NonNull FileDescriptor fd, long length) throws IOException;

    void closeQuietly(Closeable closeable);

    String makeFilename(@NonNull Uri dir,
                        @NonNull String desiredFileName);

    void moveFile(@NonNull Uri srcDir,
                  @NonNull String srcFileName,
                  @NonNull Uri destDir,
                  @NonNull String destFileName,
                  boolean replace) throws IOException, FileAlreadyExistsException;

    void copyFile(@NonNull Uri srcFile,
                  @NonNull Uri destFile,
                  boolean truncateDestFile) throws IOException;

    FileDescriptorWrapper getFD(@NonNull Uri path);

    String getExtensionSeparator();

    String appendExtension(@NonNull String fileName, @NonNull String mimeType);

    @Nullable
    String getDefaultDownloadPath();

    String altExtStoragePath();

    @Nullable
    String getUserDirPath();

    boolean isSafPath(@NonNull Uri path);

    boolean isFileSystemPath(@NonNull Uri path);

    boolean deleteFile(@NonNull Uri path) throws FileNotFoundException;

    Uri getFileUri(@NonNull Uri dir,
                   @NonNull String fileName);

    Uri getFileUri(@NonNull String relativePath,
                   @NonNull Uri dir);

    Uri createFile(@NonNull Uri dir,
                   @NonNull String fileName,
                   boolean replace) throws IOException;

    String makeFileSystemPath(@NonNull Uri uri,
                              String relativePath);

    @TargetApi(21)
    long getAvailableBytes(@NonNull FileDescriptor fd) throws IOException;

    long getDirAvailableBytes(@NonNull Uri dir);

    String getExtension(String fileName);

    boolean isValidFatFilename(String name);

    String buildValidFatFilename(String name);

    String getDirName(@NonNull Uri dir);
}
