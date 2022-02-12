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

package com.tachibana.downloader.core.utils;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.tachibana.downloader.core.system.FileSystemFacade;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DownloadUtils {
    public static final String DEFAULT_DOWNLOAD_FILENAME = "downloadfile";

    /**
     * This is the regular expression to match the content disposition type segment.
     *
     * A content disposition header can start either with inline or attachment followed by comma;
     *  For example: attachment; filename="filename.jpg" or inline; filename="filename.jpg"
     * (inline|attachment)\\s*; -> Match either inline or attachment, followed by zero o more
     * optional whitespaces characters followed by a comma.
     *
     */
    private static final String contentDispositionType = "(inline|attachment)\\s*;";

    /**
     * This is the regular expression to match filename* parameter segment.
     *
     * A content disposition header could have an optional filename* parameter,
     * the difference between this parameter and the filename is that this uses
     * the encoding defined in RFC 5987.
     *
     * Some examples:
     *  filename*=utf-8''success.html
     *  filename*=iso-8859-1'en'file%27%20%27name.jpg
     *  filename*=utf-8'en'filename.jpg
     *
     * For matching this section we use:
     * \\s*filename\\s*=\\s*= -> Zero or more optional whitespaces characters
     * followed by filename followed by any zero or more whitespaces characters and the equal sign;
     *
     * (utf-8|iso-8859-1)-> Either utf-8 or iso-8859-1 encoding types.
     *
     * '[^']*'-> Zero or more characters that are inside of single quotes '' that are not single
     * quote.
     *
     * (\S*) -> Zero or more characters that are not whitespaces. In this group,
     * it's where we are going to have the filename.
     *
     */
    private static final String contentDispositionFileNameAsterisk =
            "\\s*filename\\*\\s*=\\s*(utf-8|iso-8859-1|windows-1251)'[^']*'([^;\\s]*)";

    /**
     * Format as defined in RFC 2616 and RFC 5987
     * Both inline and attachment types are supported.
     * More details can be found
     * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Disposition
     *
     * The first segment is the [contentDispositionType], there you can find the documentation,
     * Next, it's the filename segment, where we have a filename="filename.ext"
     * For example, all of these could be possible in this section:
     * filename="filename.jpg"
     * filename="file\"name.jpg"
     * filename="file\\name.jpg"
     * filename="file\\\"name.jpg"
     * filename=filename.jpg
     *
     * For matching this section we use:
     * \\s*filename\\s*=\\s*= -> Zero or more whitespaces followed by filename followed
     *  by zero or more whitespaces and the equal sign.
     *
     * As we want to extract the the content of filename="THIS", we use:
     *
     * \\s* -> Zero or more whitespaces
     *
     *  (\"((?:\\\\.|[^|"\\\\])*)\" -> A quotation mark, optional : or \\ or any character,
     *  and any non quotation mark or \\\\ zero or more times.
     *
     *  For example: filename="file\\name.jpg", filename="file\"name.jpg" and filename="file\\\"name.jpg"
     *
     * We don't want to match after ; appears, For example filename="filename.jpg"; foo
     * we only want to match before the semicolon, so we use. |[^;]*)
     *
     * \\s* ->  Zero or more whitespaces.
     *
     *  For supporting cases, where we have both filename and filename*, we use:
     * "(?:;contentDispositionFileNameAsterisk)?"
     *
     * Some examples:
     *
     * attachment; filename="_.jpg"; filename*=iso-8859-1'en'file%27%20%27name.jpg
     * attachment; filename="_.jpg"; filename*=iso-8859-1'en'file%27%20%27name.jpg
     */
    private static final Pattern contentDispositionPattern = Pattern.compile(
            contentDispositionType +
                    "\\s*filename\\s*=\\s*(\"((?:\\\\.|[^\"\\\\])*)\"|[^;]*)\\s*" +
                    "(?:;" + contentDispositionFileNameAsterisk + ")?",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * This is an alternative content disposition pattern where only filename* is available
     */
    private static final Pattern fileNameAsteriskContentDispositionPattern = Pattern.compile(
            contentDispositionType + contentDispositionFileNameAsterisk,
            Pattern.CASE_INSENSITIVE
    );

    /*
     * Keys for the capture groups inside contentDispositionPattern
     */
    private static final int ENCODED_FILE_NAME_GROUP = 5;
    private static final int ENCODING_GROUP = 4;
    private static final int QUOTED_FILE_NAME_GROUP = 3;
    private static final int UNQUOTED_FILE_NAME = 2;

    /*
     * Belongs to the [fileNameAsteriskContentDispositionPattern]
     */
    private static final int ALTERNATIVE_FILE_NAME_GROUP = 3;
    private static final int ALTERNATIVE_ENCODING_GROUP = 2;

    private static final Pattern encodedSymbolPattern = Pattern.compile(
            "%[0-9a-f]{2}|[\\S|\\s*]",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern extensionWithDot =
            Pattern.compile("(\\.\\w+)+", Pattern.CASE_INSENSITIVE);

    public static final String[] CONTENT_DISPOSITION_TYPES = new String[]{"attachment", "inline"};

    public static String getHttpFileName(@NonNull FileSystemFacade fs,
                                         @NonNull String decodedUrl,
                                         String contentDisposition,
                                         String contentLocation,
                                         String mimeType) {
        String filename = null;
        String extension = null;

        /* First, try to use the content disposition */
        if (filename == null && contentDisposition != null) {
            filename = parseContentDisposition(contentDisposition);
            if (filename != null) {
                int index = filename.lastIndexOf('/') + 1;
                if (index > 0)
                    filename = filename.substring(index);
            }
        }

        /* If we still have nothing at this point, try the content location */
        if (filename == null && contentLocation != null) {
            String decodedContentLocation = Uri.decode(contentLocation);
            if (decodedContentLocation != null) {
                int queryIndex = decodedContentLocation.indexOf('?');
                /* If there is a query string strip it, same as desktop browsers */
                if (queryIndex > 0)
                    decodedUrl = decodedContentLocation.substring(0, queryIndex);

                if (!decodedContentLocation.endsWith("/")) {
                    int index = decodedContentLocation.lastIndexOf('/') + 1;
                    if (index > 0)
                        filename = decodedContentLocation.substring(index);
                    else
                        filename = decodedContentLocation;
                }
            }
        }

        /* If all the other http-related approaches failed, use the plain uri */
        if (filename == null) {
            int queryIndex = decodedUrl.indexOf('?');
            /* If there is a query string strip it, same as desktop browsers */
            if (queryIndex > 0)
                decodedUrl = decodedUrl.substring(0, queryIndex);

            if (!decodedUrl.endsWith("/")) {
                int index = decodedUrl.lastIndexOf('/') + 1;
                if (index > 0) {
                    String rawFilename = decodedUrl.substring(index);
                    filename = autoDecodePercentEncoding(rawFilename);
                    if (filename == null) {
                        filename = rawFilename;
                    }
                }
            }
        }

        /* Finally, if couldn't get filename from URI, get a generic filename */
        if (filename == null)
            filename = DEFAULT_DOWNLOAD_FILENAME;

        /*
         * Split filename between base and extension.
         * Add an extension if filename does not have one
         */
        var m = extensionWithDot.matcher(filename);
        String originExtension = null;
        int extensionStart = -1;
        while (m.find()) {
            originExtension = m.group();
            extensionStart = m.start();
        }

        if (originExtension == null) {
            if (mimeType != null) {
                extension = MimeTypeUtils.getExtensionFromMimeType(mimeType);
                if (extension != null)
                    extension = "." + extension;
            }
            if (extension == null) {
                if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("text/")) {
                    if (mimeType.equalsIgnoreCase("text/html"))
                        extension = ".html";
                    else
                        extension = ".txt";
                } else {
                    extension = ".bin";
                }
            }
        } else {
            originExtension = originExtension.substring(1);
            if (mimeType != null) {
                /*
                 * Compare the last segment of the extension against the mime type.
                 * If there's a mismatch, discard the entire extension.
                 */
                String typeFromExt = MimeTypeUtils.getMimeTypeFromExtension(originExtension);
                if (typeFromExt != null && !typeFromExt.equalsIgnoreCase(mimeType)) {
                    extension = MimeTypeUtils.getExtensionFromMimeType(mimeType);
                    if (extension != null)
                        extension = "." + extension;
                }
            }
            if (extension == null)
                extension = originExtension;

            filename = filename.substring(0, extensionStart + 1);
        }

        /*
         * The VFAT file system is assumed as target for downloads.
         * Replace invalid characters according to the specifications of VFAT
         */
        filename = fs.buildValidFatFilename(filename + extension);

        return filename;
    }

    /*
     * Parse the Content-Disposition HTTP Header. The format of the header
     * is defined here: http://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html
     * This header provides a filename for content that is going to be
     * downloaded to the file system. We only support the attachment type
     */

    private static String parseContentDisposition(@NonNull String contentDisposition) {
        try {
            String name = parseContentDispositionWithFileName(contentDisposition);
            if (name == null) {
                name = parseContentDispositionWithFileNameAsterisk(contentDisposition);
            }
            return name;
        } catch (IllegalStateException | NumberFormatException e) {
            // This function is defined as returning null when it can't parse the header
        } catch (UnsupportedEncodingException e) {
            // Nothing
        }

        return null;
    }

    private static String parseContentDispositionWithFileName(String contentDisposition)
            throws UnsupportedEncodingException {
        Matcher m = contentDispositionPattern.matcher(contentDisposition);
        if (m.find()) {
            // If escaped string is found, decode it using the given encoding
            String encodedFileName = m.group(ENCODED_FILE_NAME_GROUP);
            String encoding = m.group(ENCODING_GROUP);

            if (encodedFileName != null && encoding != null) {
                return decodePercentEncoding(encodedFileName, encoding);
            } else {
                // Return quoted string if available and replace escaped characters.
                String quotedFileName = m.group(QUOTED_FILE_NAME_GROUP);
                String rawFileName = quotedFileName == null ?
                        m.group(UNQUOTED_FILE_NAME) :
                        quotedFileName.replaceAll("\\\\(.)", "$1");
                String fileName = autoDecodePercentEncoding(rawFileName);
                if (fileName == null) {
                    fileName = rawFileName;
                }

                return fileName;
            }
        }

        return null;
    }

    private static String parseContentDispositionWithFileNameAsterisk(String contentDisposition)
            throws UnsupportedEncodingException {
        Matcher alternative = fileNameAsteriskContentDispositionPattern.matcher(contentDisposition);

        if (alternative.find()) {
            String encoding = alternative.group(ALTERNATIVE_ENCODING_GROUP);
            if (encoding == null) {
                return null;
            }
            String fileName = alternative.group(ALTERNATIVE_FILE_NAME_GROUP);
            if (fileName == null) {
                return null;
            }
            return decodePercentEncoding(fileName, encoding);
        }

        return null;
    }

    private static String decodePercentEncoding(String field, String encoding)
            throws UnsupportedEncodingException, NumberFormatException {
        byte[] bytes = percentEncodingBytes(field);
        return new String(bytes, 0, bytes.length, encoding);
    }

    private static byte[] percentEncodingBytes(String field)
            throws NumberFormatException {
        Matcher m = encodedSymbolPattern.matcher(field);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        while (m.find()) {
            String symbol = m.group();
            if (symbol.startsWith("%")) {
                stream.write(Integer.parseInt(symbol.substring(1), 16));
            } else {
                try {
                    stream.write(symbol.getBytes());
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        return stream.toByteArray();
    }

    private static String autoDecodePercentEncoding(String field) throws NumberFormatException {
        String encoding;
        UniversalDetector detector = new UniversalDetector();
        byte[] bytes = percentEncodingBytes(field);

        detector.handleData(bytes);
        detector.dataEnd();
        encoding = detector.getDetectedCharset();
        detector.reset();

        try {
            return encoding == null ?
                    null :
                    new String(bytes, 0, bytes.length, encoding);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    /*
     * Returns -1 if the content length is not known,
     * or if the content length is greater than Integer.MAX_VALUE.
     */
    public static long parseContentRangeFullSize(String contentRange) {
        if (contentRange == null || contentRange.isEmpty()) {
            return -1;
        }
        var bytesRange = contentRange.substring("bytes ".length());
        var sizeSplit = bytesRange.indexOf('/');
        var rangeSplit = bytesRange.indexOf('-');
        if (sizeSplit > 0) {
            var size = bytesRange.substring(sizeSplit + 1);
            if (!"*".equals(size)) {
                return parseContentSizeLong(size);
            }
        }
        if (!"*".startsWith(bytesRange) && rangeSplit > 0) {
            var start = bytesRange.substring(0, rangeSplit);
            var end = sizeSplit > 0 ?
                    bytesRange.substring(rangeSplit + 1, sizeSplit) :
                    bytesRange.substring(rangeSplit + 1);
            var startLong = parseContentSizeLong(start);
            var endLong = parseContentSizeLong(end);
            var size = endLong - startLong + 1;
            return size < 0 ? -1 : size;
        }

        return -1;
    }

    private static long parseContentSizeLong(String size) {
        try {
            return Long.parseLong(size);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
