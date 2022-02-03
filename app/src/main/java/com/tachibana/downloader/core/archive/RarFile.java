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

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.zip.ZipEncoding;
import org.apache.commons.compress.archivers.zip.ZipEncodingHelper;
import org.apache.commons.compress.utils.IOUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;

import io.reactivex.Completable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

@RequiresApi(api = Build.VERSION_CODES.O)
class RarFile implements Closeable {
    private static final String TAG = RarFile.class.getSimpleName();

    public static final String RAR = "rar";

    private static final byte[] SIGNATURE_OLD = new byte[]{0x52, 0x45, 0x7e, 0x5e};
    private static final byte[] SIGNATURE_V4 = new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00};
    private static final byte[] SIGNATURE_V5 = new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01};

    private final File source;
    private final String encoding;
    private final Archive archive;
    private final ZipEncoding zipEncoding;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public RarFile(@NonNull File source) throws ArchiveException, IOException {
        this(source, null);
    }

    public RarFile(@NonNull File source, String encoding) throws ArchiveException, IOException {
        this.source = source;
        this.encoding = encoding;
        try {
            // InputStream variant isn't support big files due to memory limitations
            archive = new Archive(source);
        } catch (RarException e) {
            throw new ArchiveException(e.getMessage(), e);
        }
        zipEncoding = ZipEncodingHelper.getZipEncoding(encoding);
    }

    public RarArchiveEntry getNextEntry() throws IOException {
        var header = archive.nextFileHeader();
        return header == null ? null : new RarArchiveEntry(header, zipEncoding);
    }

    public Iterable<RarArchiveEntry> getEntries() throws IOException {
        var list = new ArrayList<RarArchiveEntry>();
        for (var header : archive.getFileHeaders()) {
            list.add(new RarArchiveEntry(header, zipEncoding));
        }
        return list;
    }

    public InputStream getInputStream(RarArchiveEntry entry) {
        var is = new PipedInputStream();
        try (var os = new PipedOutputStream(is)) {
            disposables.add(Completable.fromRunnable(() -> {
                try {
                    archive.extractFile(entry.getHeader(), os);
                } catch (RarException e) {
                    Log.e(TAG, "Unable to extract file", e);
                }
            }).subscribeOn(Schedulers.io()).subscribe());
        } catch (IOException e) {
            Log.e(TAG, "Unable to extract file", e);
        }

        return is;
    }

    @Override
    public void close() throws IOException {
        disposables.clear();
        archive.close();
    }

    public static String detect(@NonNull InputStream source) throws IOException {
        if (!source.markSupported()) {
            throw new IllegalArgumentException("InputStream.markSupported() returned false");
        }
        var signature = new byte[SIGNATURE_V4.length];
        source.mark(signature.length);
        try {
            var signatureLength = IOUtils.readFully(source, signature);
            return matches(signature, signatureLength) ? RAR : null;
        } finally {
            source.reset();
        }
    }

    private static boolean matches(byte[] signature, int length) {
        return matches(signature, length, SIGNATURE_OLD)
                || matches(signature, length, SIGNATURE_V4)
                || matches(signature, length, SIGNATURE_V5);
    }

    private static boolean matches(byte[] actual, int actualLength, byte[] expected) {
        if (actualLength < expected.length) {
            return false;
        }
        for (var i = 0; i < expected.length; i++) {
            if (actual[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
