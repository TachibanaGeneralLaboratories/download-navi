/*
 * Copyright (C) 2021 Tachibana General Laboratories, LLC
 * Copyright (C) 2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.ui.filemanager.FileManagerConfig;
import com.tachibana.downloader.ui.filemanager.FileManagerDialog;

public final class FileSystemContracts {
    private FileSystemContracts() {}

    /**
     * An {@link ActivityResultContract} to prompt the user to select a directory, returning the
     * user selection as a {@link Uri}. Apps can fully manage documents within the returned
     * directory.
     * <p>
     * The input is an optional {@link Uri} of the initial starting location.
     */
    public static class OpenDirectory extends ActivityResultContracts.OpenDocumentTree {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @Nullable Uri input) {
            Intent i;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                i = new Intent(context, FileManagerDialog.class);
                String dirPath = null;
                if (input != null && Utils.isFileSystemPath(input)) {
                    dirPath = input.getPath();
                }
                FileManagerConfig config = new FileManagerConfig(
                        dirPath,
                        context.getString(R.string.select_folder_to_save),
                        FileManagerConfig.DIR_CHOOSER_MODE
                );
                i.putExtra(FileManagerDialog.TAG_CONFIG, config);
            } else {
                i = super.createIntent(context, input);
                i.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

            }
            return i;
        }
    }

    /**
     * An {@link ActivityResultContract} to prompt the user to open a document, receiving its
     * contents as a {@code file:/http:/content:} {@link Uri}.
     * <p>
     * The input is the mime types to filter by, e.g. {@code image/*}.
     */
    public static class OpenFile extends ActivityResultContracts.OpenDocument {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull String[] input) {
            Intent i;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                i = new Intent(context, FileManagerDialog.class);
                FileManagerConfig config = new FileManagerConfig(
                        null,
                        null,
                        FileManagerConfig.FILE_CHOOSER_MODE
                );
                config.mimeType = input.length > 0 ? input[0] : null;
                i.putExtra(FileManagerDialog.TAG_CONFIG, config);
            } else {
                i = super.createIntent(context, input);
                i.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

            }
            return i;
        }
    }
}
