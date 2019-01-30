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

package com.tachibana.downloader.core.utils;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.tachibana.downloader.R;
import com.tachibana.downloader.adapter.DownloadItem;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.settings.SettingsManager;

import java.io.File;
import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import static com.tachibana.downloader.core.utils.MimeTypeUtils.DEFAULT_MIME_TYPE;
import static com.tachibana.downloader.core.utils.MimeTypeUtils.MIME_TYPE_DELIMITER;

public class Utils
{
    public static final String INFINITY_SYMBOL = "\u221e";
    public static final String HTTP_PREFIX = "http://";
    public static final String HTTPS_PREFIX = "https://";
    public static final String FTP_PREFIX = "ftp://";
    public static final String DEFAULT_DOWNLOAD_FILENAME = "downloadfile";
    private static final String CONTENT_DISPOSITION_PATTERN = "attachment;\\s*filename\\s*=\\s*\"([^\"]*)\"";

    /*
     * Workaround for start service in Android 8+ if app no started.
     * We have a window of time to get around to calling startForeground() before we get ANR,
     * if work is longer than a millisecond but less than a few seconds.
     */

    public static void startServiceBackground(Context context, Intent i)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(i);
        else
            context.startService(i);
    }

    public static boolean checkNetworkConnection(Context context)
    {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        return activeNetwork.isConnectedOrConnecting();
    }

    public static NetworkInfo getActiveNetworkInfo(Context context)
    {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        return cm.getActiveNetworkInfo();
    }

    @TargetApi(23)
    public static NetworkCapabilities getNetworkCapabilities(Context context)
    {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null)
            return null;

        return cm.getNetworkCapabilities(network);
    }

    public static SSLContext getSSLContext() throws GeneralSecurityException
    {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore)null);

        TrustManager[] trustManagers = tmf.getTrustManagers();
        final X509TrustManager origTrustManager = (X509TrustManager)trustManagers[0];

        TrustManager[] wrappedTrustManagers = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers()
                    {
                        return origTrustManager.getAcceptedIssuers();
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException
                    {
                        origTrustManager.checkClientTrusted(certs, authType);
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException
                    {
                            origTrustManager.checkServerTrusted(certs, authType);
                    }
                }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, wrappedTrustManagers, null);

        return sslContext;
    }

    public static int getThemePreference(Context context)
    {
        return SettingsManager.getPreferences(context).getInt(context.getString(R.string.pref_key_theme),
                SettingsManager.Default.theme(context));
    }

    public static int getAppTheme(Context context)
    {
        int theme = getThemePreference(context);

        if (theme == Integer.parseInt(context.getString(R.string.pref_theme_light_value)))
            return R.style.AppTheme;
        else if (theme == Integer.parseInt(context.getString(R.string.pref_theme_dark_value)))
            return R.style.AppTheme_Dark;
        else if (theme == Integer.parseInt(context.getString(R.string.pref_theme_black_value)))
            return R.style.AppTheme_Black;

        return R.style.AppTheme;
    }

    public static int getTranslucentAppTheme(Context context)
    {
        int theme = getThemePreference(context);

        if (theme == Integer.parseInt(context.getString(R.string.pref_theme_light_value)))
            return R.style.Theme_AppCompat_Translucent;
        else if (theme == Integer.parseInt(context.getString(R.string.pref_theme_dark_value)))
            return R.style.Theme_AppCompat_Translucent_Dark;
        else if (theme == Integer.parseInt(context.getString(R.string.pref_theme_black_value)))
            return R.style.Theme_AppCompat_Translucent_Black;

        return R.style.Theme_AppCompat_Translucent;
    }

    /*
     * Colorize the progress bar in the accent color (for pre-Lollipop).
     */

    public static void colorizeProgressBar(Context context, ProgressBar progress)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            progress.getProgressDrawable().setColorFilter(ContextCompat.getColor(context, R.color.accent),
                    android.graphics.PorterDuff.Mode.SRC_IN);
    }

    /*
     * Returns the first item from clipboard.
     */

    @Nullable
    public static String getClipboard(Context context)
    {
        ClipboardManager clipboard = (ClipboardManager)context.getSystemService(Activity.CLIPBOARD_SERVICE);
        if (clipboard == null)
            return null;

        if (!clipboard.hasPrimaryClip())
            return null;

        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0)
            return null;

        CharSequence text = clip.getItemAt(0).getText();
        if (text == null)
            return null;

        return text.toString();
    }

    public static String getHttpFileName(String url, String contentDisposition, String contentLocation)
    {
        String filename = null;

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
            if (decodedContentLocation != null &&
                !decodedContentLocation.endsWith("/") &&
                decodedContentLocation.indexOf('?') < 0)
            {
                int index = decodedContentLocation.lastIndexOf('/') + 1;
                if (index > 0)
                    filename = decodedContentLocation.substring(index);
                else
                    filename = decodedContentLocation;
            }
        }

        /* If all the other http-related approaches failed, use the plain uri */
        if (filename == null) {
            String decodedUrl = Uri.decode(url);
            if (decodedUrl != null && !decodedUrl.endsWith("/") && decodedUrl.indexOf('?') < 0) {
                int index = decodedUrl.lastIndexOf('/') + 1;
                if (index > 0)
                    filename = decodedUrl.substring(index);
            }
        }

        /* Finally, if couldn't get filename from URI, get a generic filename */
        if (filename == null)
            filename = DEFAULT_DOWNLOAD_FILENAME;

        /*
         * The VFAT file system is assumed as target for downloads.
         * Replace invalid characters according to the specifications of VFAT
         */
        filename = FileUtils.buildValidFatFilename(filename);

        return filename;
    }

    /*
     * Parse the Content-Disposition HTTP Header. The format of the header
     * is defined here: http://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html
     * This header provides a filename for content that is going to be
     * downloaded to the file system. We only support the attachment type
     */

    private static String parseContentDisposition(String contentDisposition)
    {
        try {
            Matcher m = Pattern.compile(CONTENT_DISPOSITION_PATTERN).matcher(contentDisposition);
            if (m.find())
                return m.group(1);

        } catch (IllegalStateException e) {
            /* Ignore */
        }
        return null;
    }

    /*
     * Returns the link as "(http[s]|ftp)://[www.]name.domain/...".
     */

    public static String normalizeURL(String url)
    {
        url = IDN.toUnicode(url);

        if (!url.startsWith(HTTP_PREFIX) && !url.startsWith(HTTPS_PREFIX) && !url.startsWith(FTP_PREFIX))
            return HTTP_PREFIX + url;
        else
            return url;
    }

    public static boolean checkConnectivity(Context context)
    {
        NetworkInfo netInfo = Utils.getActiveNetworkInfo(context);

        return netInfo != null && netInfo.isConnected() && isNetworkTypeAllowed(context);
    }

    public static boolean isNetworkTypeAllowed(Context context)
    {
        SharedPreferences pref = SettingsManager.getPreferences(context);
        boolean enableRoaming = pref.getBoolean(context.getString(R.string.pref_key_wifi_only),
                SettingsManager.Default.enableRoaming);
        boolean wifiOnly = pref.getBoolean(context.getString(R.string.pref_key_wifi_only),
                SettingsManager.Default.wifiOnly);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities caps = Utils.getNetworkCapabilities(context);
            if (caps == null)
                return true;
            if (wifiOnly && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                enableRoaming && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING))
                return false;

        } else {
            NetworkInfo netInfo = Utils.getActiveNetworkInfo(context);
            if (netInfo == null)
                return false;
            if (wifiOnly && netInfo.getType() != ConnectivityManager.TYPE_WIFI ||
                enableRoaming && netInfo.isRoaming())
            return false;
        }

        return true;
    }

    /*
     * Colored status bar in KitKat.
     */

    public static void showColoredStatusBar_KitKat(Activity activity)
    {
        RelativeLayout statusBar = activity.findViewById(R.id.statusBarKitKat);

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT)
            statusBar.setVisibility(View.VISIBLE);
    }

    /*
     * Don't use app context (its doesn't reload after configuration changes)
     */

    public static boolean isTwoPane(Context context)
    {
        return context.getResources().getBoolean(R.bool.isTwoPane);
    }

    public static long calcETA(long totalBytes, long curBytes, long speed)
    {
        long left = totalBytes - curBytes;
        if (left <= 0)
            return 0;
        if (speed <= 0)
            return -1;

        return left / speed;
    }

    /*
     * For example, for https://docs.oracle.com/javase/8/docs/api/java/net/URL.html
     * returns docs.oracle.com
     */

    static public String getHostFromUrl(String url)
    {
        URL uri;
        try {
            uri = new URL(url);

        } catch (MalformedURLException e) {
            return null;
        }

        String host = uri.getHost();
        if (host == null)
            return null;

        return host.replaceAll("^www\\.", "");
    }

    public static int getAttributeColor(Context context, int attributeId)
    {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attributeId, typedValue, true);
        int colorRes = typedValue.resourceId;
        int color = -1;
        try {
            color = context.getResources().getColor(colorRes);

        } catch (Resources.NotFoundException e) {
            return color;
        }

        return color;
    }

    public static Intent makeFileShareIntent(@NonNull Context context, List<DownloadItem> items)
    {
        Intent i = new Intent();
        String intentAction;
        ArrayList<Uri> itemsUri = new ArrayList<>();
        String intentMimeType = "";
        String[] intentMimeParts = {"", ""};

        for (DownloadItem item : items) {
            if (item == null)
                continue;

            DownloadInfo info = item.info;
            Uri filePath = FileUtils.getFileUri(context, info.dirPath, info.fileName);
            if (filePath != null) {
                if (FileUtils.isFileSystemPath(filePath))
                    filePath = FileProvider.getUriForFile(context,
                            context.getPackageName() + ".provider",
                            new File(filePath.getPath()));

                itemsUri.add(filePath);
            };

            String mimeType = item.info.mimeType;
            if (TextUtils.isEmpty(mimeType)) {
                intentMimeType = DEFAULT_MIME_TYPE;
                continue;
            }

            /*
             * If the intent mime type hasn't been set yet,
             * set it to the mime type for this item
             */
            if (TextUtils.isEmpty(intentMimeType)) {
                intentMimeType = mimeType;
                if (!TextUtils.isEmpty(intentMimeType)) {
                    intentMimeParts = intentMimeType.split(MIME_TYPE_DELIMITER);
                    /* Guard against invalid mime types */
                    if (intentMimeParts.length != 2)
                        intentMimeType = DEFAULT_MIME_TYPE;
                }
                continue;
            }

            /*
             * Either the mime type is already the default or it matches the current item's mime type.
             * In either case, intentMimeType is already the correct value
             */
            if (TextUtils.equals(intentMimeType, DEFAULT_MIME_TYPE) ||
                TextUtils.equals(intentMimeType, mimeType))
                continue;

            String[] mimeParts = mimeType.split(MIME_TYPE_DELIMITER);
            if (!TextUtils.equals(intentMimeParts[0], mimeParts[0])) {
                /* The top-level types don't match; fallback to the default mime type */
                intentMimeType = DEFAULT_MIME_TYPE;
            } else {
                /* The mime type should be "{top-level type}/*" */
                intentMimeType = intentMimeParts[0] + MIME_TYPE_DELIMITER + "*";
            }
        }

        if (itemsUri.size() == 0 || itemsUri.size() == 1)
            intentAction = Intent.ACTION_SEND;
        else
            intentAction = Intent.ACTION_SEND_MULTIPLE;

        if (itemsUri.size() > 1)
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, itemsUri);
        else
            i.putExtra(Intent.EXTRA_STREAM, itemsUri.get(0));

        /* If there is exactly one item shared, set the mail title */
        if (items.size() == 1)
            i.putExtra(Intent.EXTRA_SUBJECT, items.get(0).info.fileName);

        i.setAction(intentAction);
        i.setType(intentMimeType);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return i;
    }

    public static Intent createOpenFileIntent(Context context, DownloadInfo info)
    {
        Intent i = new Intent();
        i.setAction(Intent.ACTION_VIEW);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Uri filePath = FileUtils.getFileUri(context, info.dirPath, info.fileName);
        if (filePath == null)
            return i;

        if (FileUtils.isFileSystemPath(filePath))
            i.setDataAndType(FileProvider.getUriForFile(context,
                    context.getPackageName() + ".provider",
                    new File(filePath.getPath())),
                    info.mimeType);
        else
            i.setDataAndType(filePath, info.mimeType);

        return i;
    }

    /*
     * Get system user agent (from WebView).
     * Requires UI thread for WebView
     */

    @UiThread
    public static String getSystemUserAgent(Context context)
    {
        return new WebView(context).getSettings().getUserAgentString();
    }

    public static boolean checkStoragePermission(Context context)
    {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /*
     * Retain download dir path and its permissions (if SAF path).
     */

    public static void retainDownloadDir(@NonNull Context context, @NonNull Uri dirPath)
    {
        SharedPreferences pref = SettingsManager.getPreferences(context);
        String key = context.getString(R.string.pref_key_last_download_dir_uri);

        try {
            FileUtils.takeUriPermission(context, dirPath);

        } catch (SecurityException e) {
            /* Save default value */
            pref.edit().putString(key, SettingsManager.Default.lastDownloadDirUri).apply();
            return;
        }

        pref.edit().putString(key, dirPath.toString()).apply();
    }
}
