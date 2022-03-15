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

import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.tachibana.downloader.core.exception.UnknownArchiveFormatException;
import com.tachibana.downloader.core.system.FileSystemFacade;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.StreamingNotSupportedException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZFileOptions;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@RequiresApi(api = Build.VERSION_CODES.O)
public class ArchiveExtractor {
    private static final int SEVEN_Z_MAX_MEMORY_LIMIT_KB = 10240; /* 10 MB */
    /* The file copy buffer size (30 MB) */
    private static final int FILE_COPY_BUFFER_SIZE = 1024 * 1024 * 30;

    private static final String TAG = ArchiveExtractor.class.getSimpleName();

    private final FileInputStream source;
    private final FileSystemFacade fs;
    String fileName;
    String mimeType;

    private final SevenZFileOptions sevenZOptions = new SevenZFileOptions.Builder()
            .withMaxMemoryLimitInKb(SEVEN_Z_MAX_MEMORY_LIMIT_KB)
            .withUseDefaultNameForUnnamedEntries(true)
            .withTryToRecoverBrokenArchives(true)
            .build();

    public ArchiveExtractor(
            FileSystemFacade fs,
            FileInputStream source,
            String fileName,
            String mimeType
    ) {
        this.fs = fs;
        this.source = source;
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    public void uncompress(Uri outputDir, boolean extractToSubDir) throws IOException, UnknownArchiveFormatException {
        var subDirName = extractToSubDir ? getExtractSubDirName(outputDir, fileName) : "";
        subDirName += File.separator;
        switch (ArchiveType.getByMimeType(mimeType)) {
            case sevenZ:
                uncompress7z(source, outputDir, subDirName);
                break;
            case other:
                try (var bufIs = new BufferedInputStream(source)) {
                    defaultUncompress(bufIs, outputDir, subDirName);
                }
                break;
        }
    }

    private void defaultUncompress(BufferedInputStream bufIs, Uri outputDir, String subDirName) throws IOException, UnknownArchiveFormatException {
        ArchiveInputStream archiveIs = null;
        BufferedInputStream compressorIs = null;
        try {
            try {
                // InputStream must be buffered for markSupported()
                compressorIs = new BufferedInputStream(
                        new CompressorStreamFactory().createCompressorInputStream(bufIs)
                );
            } catch (CompressorException e) {
                // Ignore
            }

            try {
                // InputStream must be buffered for markSupported()
                archiveIs = new ArchiveStreamFactory().createArchiveInputStream(
                        compressorIs == null ? bufIs : compressorIs
                );
            } catch (StreamingNotSupportedException e) {
                Log.e(TAG, "Unable to read format from stream", e);
            } catch (ArchiveException e) {
                Log.e(TAG, "Unknown archive format", e);
            }

            if (archiveIs == null) {
                throw new UnknownArchiveFormatException();
            }
            final var is = archiveIs;
            ArchiveEntry entry;
            while ((entry = archiveIs.getNextEntry()) != null) {
                if (!archiveIs.canReadEntryData(entry)) {
                    Log.e(TAG, "Unable to read archive entry: " + entry.getName());
                    continue;
                }
                uncompressEntry(entry, outputDir, subDirName, (path) -> saveStream(is, path));
            }
        } finally {
            if (archiveIs != null) {
                archiveIs.close();
            }
            if (compressorIs != null) {
                compressorIs.close();
            }
        }
    }

    private void uncompress7z(FileInputStream is, Uri outputDir, String subDirName) throws IOException, UnknownArchiveFormatException {
        SevenZFile file = null;
        try (var channel = is.getChannel()) {
            try {
                file = new SevenZFile(channel, sevenZOptions);
            } catch (IOException e) {
                Log.e(TAG, "Unable to read 7z archive", e);
                throw new UnknownArchiveFormatException();
            }
            SevenZArchiveEntry entry;
            while ((entry = file.getNextEntry()) != null) {
                try (var fileIs = file.getInputStream(entry)) {
                    uncompressEntry(entry, outputDir, subDirName, (path) -> saveStream(fileIs, path));
                }
            }
        } finally {
            if (file != null) {
                file.close();
            }
        }
    }

    private void uncompressEntry(
            ArchiveEntry entry,
            Uri outputDir,
            String subDirName,
            OnSaveFileEntryListener listener
    ) throws IOException {
        var name = entry.getName();
        if (name == null) {
            name = File.separator + fs.buildValidFatFilename(null);
        }
        var path = subDirName + name;
        if (entry.isDirectory() || path.endsWith(File.separator)) {
            fs.mkdirs(outputDir, path);
        } else {
            var filePath = fs.createFile(path, outputDir, true);
            if (filePath == null) {
                throw new FileNotFoundException(path);
            }
            listener.save(filePath);
        }
    }

    private void saveStream(InputStream is, Uri filePath) throws IOException {
        try (var w = fs.getFD(filePath)) {
            var inFd = w.open("rw");
            try (var os = new FileOutputStream(inFd)) {
                fs.copy(is, os, FILE_COPY_BUFFER_SIZE);
            }
        }
    }

    private void copyToTmpFile(InputStream is, File tmpFile) throws IOException {
        try (var os = new FileOutputStream(tmpFile)) {
            fs.copy(is, os, FILE_COPY_BUFFER_SIZE);
        }
    }

    private String getExtractSubDirName(Uri dirPath, String fileName) {
        var extension = fs.getNameWithoutExtension(fileName);
        if (extension.equals(fileName)) {
            return fs.makeFilename(dirPath, fileName);
        } else {
            return extension;
        }
    }

    private interface OnSaveFileEntryListener {
        void save(@NonNull Uri filePath) throws IOException;
    }

    private enum ArchiveType {
        other,
        sevenZ;

        static ArchiveType getByMimeType(String mimeType) {
            switch (mimeType) {
                case "application/x-7z-compressed":
                    return ArchiveType.sevenZ;
            }
            return ArchiveType.other;
        }
    }
}