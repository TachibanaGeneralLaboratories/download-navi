/*
 * Copyright (C) 2019-2022 Tachibana General Laboratories, LLC
 * Copyright (C) 2019-2022 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.webkit.MimeTypeMap;

import java.util.HashMap;

public class MimeTypeUtils
{
    public static final String DEFAULT_MIME_TYPE = "*/*";
    public static final String MIME_TYPE_DELIMITER = "/";

    public enum Category
    {
        OTHER,
        ARCHIVE,
        VIDEO,
        AUDIO,
        IMAGE,
        DOCUMENT,
        APK,
    }

    public static Category getCategory(String mime)
    {
        if (mime == null)
            return Category.OTHER;

        mime = mime.toLowerCase();

        if (mime.startsWith("video/"))
            return Category.VIDEO;
        else if (mime.startsWith("audio/"))
            return Category.AUDIO;
        else if (mime.startsWith("image/"))
            return Category.IMAGE;
        else if (mime.startsWith("text/"))
            return Category.DOCUMENT;

        Category category;
        return ((category = mimeToCategory.get(mime)) == null ? Category.OTHER : category);
    }

    private static final HashMap<String, Category> mimeToCategory = new HashMap<>();
    static {
        mimeToCategory.put("application/atom+xml", Category.DOCUMENT);
        mimeToCategory.put("application/ecmascript", Category.DOCUMENT);
        mimeToCategory.put("application/epub+zip",Category.DOCUMENT);
        mimeToCategory.put("application/gpx+xml", Category.DOCUMENT);
        mimeToCategory.put("application/gzip", Category.ARCHIVE);
        mimeToCategory.put("application/hta", Category.DOCUMENT);
        mimeToCategory.put("application/java-archive", Category.ARCHIVE);
        mimeToCategory.put("application/javascript", Category.DOCUMENT);
        mimeToCategory.put("application/x-javascript", Category.DOCUMENT);
        mimeToCategory.put("application/json", Category.DOCUMENT);
        mimeToCategory.put("application/mpegurl", Category.VIDEO);
        mimeToCategory.put("application/msword", Category.DOCUMENT);
        mimeToCategory.put("application/ogg", Category.VIDEO);
        mimeToCategory.put("application/olescript", Category.DOCUMENT);
        mimeToCategory.put("application/onenote", Category.DOCUMENT);
        mimeToCategory.put("application/opensearchdescription+xml", Category.DOCUMENT);
        mimeToCategory.put("application/pdf", Category.DOCUMENT);
        mimeToCategory.put("application/postscript", Category.DOCUMENT);
        mimeToCategory.put("application/rtf", Category.DOCUMENT);
        mimeToCategory.put("application/typescript", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.adobe.air-application-installer-package+zip", Category.ARCHIVE);
        mimeToCategory.put("application/vnd.amazon.ebook", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.android.package-archive", Category.APK);
        mimeToCategory.put("application/vnd.apple.mpegurl", Category.VIDEO);
        mimeToCategory.put("application/vnd.apple.mpegurl.audio", Category.AUDIO);
        mimeToCategory.put("application/vnd.fdf", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.mozilla.xul+xml", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-cab-compressed", Category.ARCHIVE);
        mimeToCategory.put("application/vnd.ms-excel", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-excel.addin.macroEnabled.12", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-excel.sheet.binary.macroEnabled.12", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-excel.sheet.macroEnabled.12", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-excel.template.macroEnabled.12", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-mediapackage", Category.IMAGE);
        mimeToCategory.put("application/vnd.ms-powerpoint", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-powerpoint.addin.macroEnabled.12", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-powerpoint.presentation.macroEnabled.12", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-powerpoint.slide.macroEnabled.12", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-powerpoint.slideshow.macroEnabled.12", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-powerpoint.template.macroEnabled.12", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-project", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-visio.viewer", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-word.document.macroEnabled.12", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-word.template.macroEnabled.12", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-wpl", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.ms-xpsdocument", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.oasis.opendocument.chart", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.oasis.opendocument.database", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.oasis.opendocument.formula", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.oasis.opendocument.graphics", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.oasis.opendocument.graphics-template", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.oasis.opendocument.image", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.oasis.opendocument.presentation", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.oasis.opendocument.presentation-template", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.oasis.opendocument.spreadsheet", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.oasis.opendocument.spreadsheet-template", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.oasis.opendocument.text", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.oasis.opendocument.text-master", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.oasis.opendocument.text-template", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.oasis.opendocument.text-web", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.openofficeorg.extension", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.openxmlformats-officedocument.presentationml.slide", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.openxmlformats-officedocument.presentationml.slideshow", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.openxmlformats-officedocument.presentationml.template", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.openxmlformats-officedocument.spreadsheetml.template", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.openxmlformats-officedocument.wordprocessingml.template", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.rn-realmedia", Category.VIDEO);
        mimeToCategory.put("application/vnd.symbian.install", Category.ARCHIVE);
        mimeToCategory.put("application/vnd.visio", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.wap.wmlc", Category.DOCUMENT);
        mimeToCategory.put("application/vnd.wap.wmlscriptc", Category.DOCUMENT);
        mimeToCategory.put("application/vsix", Category.ARCHIVE);
        mimeToCategory.put("application/windows-library+xml", Category.DOCUMENT);
        mimeToCategory.put("application/windows-search-connector+xml", Category.DOCUMENT);
        mimeToCategory.put("application/x-7z-compressed", Category.ARCHIVE);
        mimeToCategory.put("application/x-abiword", Category.DOCUMENT);
        mimeToCategory.put("application/x-ace-compressed", Category.ARCHIVE);
        mimeToCategory.put("application/x-astrotite-afa", Category.ARCHIVE);
        mimeToCategory.put("application/x-alz-compressed", Category.ARCHIVE);
        mimeToCategory.put("application/x-apple-diskimage", Category.ARCHIVE);
        mimeToCategory.put("application/x-arj", Category.ARCHIVE);
        mimeToCategory.put("application/x-b1", Category.ARCHIVE);
        mimeToCategory.put("application/x-bzip", Category.ARCHIVE);
        mimeToCategory.put("application/x-bzip2", Category.ARCHIVE);
        mimeToCategory.put("application/x-cfs-compressed", Category.ARCHIVE);
        mimeToCategory.put("application/x-compress", Category.ARCHIVE);
        mimeToCategory.put("application/x-compressed", Category.ARCHIVE);
        mimeToCategory.put("application/x-cpio", Category.ARCHIVE);
        mimeToCategory.put("application/x-csh", Category.ARCHIVE);
        mimeToCategory.put("application/x-dar", Category.ARCHIVE);
        mimeToCategory.put("application/x-dgc-compressed", Category.ARCHIVE);
        mimeToCategory.put("application/x-director", Category.VIDEO);
        mimeToCategory.put("application/x-dvi", Category.DOCUMENT);
        mimeToCategory.put("application/x-gtar", Category.ARCHIVE);
        mimeToCategory.put("application/x-gzip", Category.ARCHIVE);
        mimeToCategory.put("application/x-itunes-itlp", Category.DOCUMENT);
        mimeToCategory.put("application/x-gca-compressed", Category.ARCHIVE);
        mimeToCategory.put("application/x-latex", Category.DOCUMENT);
        mimeToCategory.put("application/x-lzip", Category.ARCHIVE);
        mimeToCategory.put("application/x-lzh", Category.ARCHIVE);
        mimeToCategory.put("application/x-lzx", Category.ARCHIVE);
        mimeToCategory.put("application/x-lzma", Category.ARCHIVE);
        mimeToCategory.put("application/x-lzop", Category.ARCHIVE);
        mimeToCategory.put("application/x-mpegurl", Category.VIDEO);
        mimeToCategory.put("application/x-quicktimeplayer", Category.VIDEO);
        mimeToCategory.put("application/x-rar-compressed", Category.ARCHIVE);
        mimeToCategory.put("application/x-rar", Category.ARCHIVE);
        mimeToCategory.put("application/rar", Category.ARCHIVE);
        mimeToCategory.put("application/vnd.rar", Category.ARCHIVE);
        mimeToCategory.put("application/x-sbx", Category.ARCHIVE);
        mimeToCategory.put("application/x-sh", Category.DOCUMENT);
        mimeToCategory.put("application/x-shar", Category.ARCHIVE);
        mimeToCategory.put("application/x-shockwave-flash", Category.VIDEO);
        mimeToCategory.put("application/x-silverlight-app", Category.ARCHIVE);
        mimeToCategory.put("application/x-smaf", Category.AUDIO);
        mimeToCategory.put("application/x-snappy-framed", Category.ARCHIVE);
        mimeToCategory.put("application/x-stuffit", Category.ARCHIVE);
        mimeToCategory.put("application/x-stuffitx", Category.ARCHIVE);
        mimeToCategory.put("application/x-sv4cpio", Category.ARCHIVE);
        mimeToCategory.put("application/x-tar", Category.ARCHIVE);
        mimeToCategory.put("application/x-tcl", Category.DOCUMENT);
        mimeToCategory.put("application/x-tex", Category.DOCUMENT);
        mimeToCategory.put("application/x-texinfo", Category.DOCUMENT);
        mimeToCategory.put("application/x-troff", Category.DOCUMENT);
        mimeToCategory.put("application/x-troff-man", Category.DOCUMENT);
        mimeToCategory.put("application/x-troff-me", Category.DOCUMENT);
        mimeToCategory.put("application/x-troff-ms", Category.DOCUMENT);
        mimeToCategory.put("application/x-ustar", Category.ARCHIVE);
        mimeToCategory.put("application/xaml+xml", Category.DOCUMENT);
        mimeToCategory.put("application/xapk-package-archive", Category.APK);
        mimeToCategory.put("application/xhtml+xml", Category.DOCUMENT);
        mimeToCategory.put("application/xml", Category.DOCUMENT);
        mimeToCategory.put("application/xml-dtd", Category.DOCUMENT);
        mimeToCategory.put("application/xspf+xml", Category.DOCUMENT);
        mimeToCategory.put("application/x-xz", Category.ARCHIVE);
        mimeToCategory.put("application/zip", Category.ARCHIVE);
        mimeToCategory.put("application/x-zoo", Category.ARCHIVE);
        mimeToCategory.put("application/php", Category.DOCUMENT);
        mimeToCategory.put("application/x-php", Category.DOCUMENT);
        mimeToCategory.put("application/x-httpd-php", Category.DOCUMENT);
        mimeToCategory.put("application/x-httpd-php-source", Category.DOCUMENT);
    }

    private static final HashMap<String, String> extensionToMime = new HashMap<>();
    static {
        extensionToMime.put("php", "application/php");
        extensionToMime.put("json", "application/json");
    }

    private static final HashMap<String, String> mimeToExtension = new HashMap<>();
    static {
        mimeToExtension.put("text/php", "php");
        mimeToExtension.put("text/x-php", "php");
        mimeToExtension.put("application/php", "php");
        mimeToExtension.put("application/x-php", "php");
        mimeToExtension.put("application/x-httpd-php", "php");
        mimeToExtension.put("application/x-httpd-php-source", "php");
        mimeToExtension.put("application/json", "json");
    }

    public static String getExtensionFromMimeType(String mimeType)
    {
        var extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        return extension == null || "bin".equals(mimeType)
                ? mimeToExtension.get(mimeType)
                : extension;
    }

    public static String getMimeTypeFromExtension(String extension)
    {
        var mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mimeType == null || "application/octet-stream".equals(mimeType)
                ? extensionToMime.get(extension)
                : mimeType;
    }
}
