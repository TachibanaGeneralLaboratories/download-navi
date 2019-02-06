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

package com.tachibana.downloader.core.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

import androidx.annotation.NonNull;

/*
 * Hash utils.
 */

public class DigestUtils
{
    private static final int STREAM_BUFFER_LENGTH = 1024;

    public static String makeSha256Hash(@NonNull FileInputStream is)
    {
        try (BufferedInputStream bufIs = new BufferedInputStream(is)) {
            return makeHash("SHA-256", bufIs);
        } catch (IOException e) {
            return null;
        }
    }

    public static String makeMd5Hash(@NonNull FileInputStream is)
    {
        try (BufferedInputStream bufIs = new BufferedInputStream(is)) {
            return makeHash("MD5", bufIs);
        } catch (IOException e) {
            return null;
        }
    }

    public static String makeSha256Hash(@NonNull byte[] bytes)
    {
        try (ByteArrayInputStream is = new ByteArrayInputStream(bytes)) {
            return makeHash("SHA-256", is);
        } catch (IOException e) {
            return null;
        }
    }

    public static String makeMd5Hash(@NonNull byte[] bytes)
    {
        try (ByteArrayInputStream is = new ByteArrayInputStream(bytes)) {
            return makeHash("MD5", is);
        } catch (IOException e) {
            return null;
        }
    }

    private static String makeHash(String algorithm, InputStream is)
    {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(algorithm);
            updateDigest(messageDigest, is);

        } catch (Exception e) {
            return null;
        }

        return digestToString(messageDigest.digest());
    }

    private static void updateDigest(MessageDigest messageDigest, InputStream is) throws IOException
    {
        byte[] buffer = new byte[STREAM_BUFFER_LENGTH];
        int read = is.read(buffer, 0, STREAM_BUFFER_LENGTH);

        while (read > -1) {
            messageDigest.update(buffer, 0, read);
            read = is.read(buffer, 0, STREAM_BUFFER_LENGTH);
        }
    }

    private static String digestToString(byte[] digest)
    {
        StringBuilder sha1 = new StringBuilder();
        for (byte b : digest) {
            if ((0xff & b) < 0x10)
                sha1.append("0");
            sha1.append(Integer.toHexString(0xff & b));
        }

        return sha1.toString();
    }
}
