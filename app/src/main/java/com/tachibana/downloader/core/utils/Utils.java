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
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.webkit.WebSettings;
import android.widget.ProgressBar;

import com.tachibana.downloader.R;
import com.tachibana.downloader.adapter.DownloadItem;
import com.tachibana.downloader.adapter.drawer.DrawerGroup;
import com.tachibana.downloader.adapter.drawer.DrawerGroupItem;
import com.tachibana.downloader.core.RealSystemFacade;
import com.tachibana.downloader.core.SystemFacade;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.filter.DownloadFilter;
import com.tachibana.downloader.core.filter.DownloadFilterCollection;
import com.tachibana.downloader.core.sorting.DownloadSorting;
import com.tachibana.downloader.core.sorting.DownloadSortingComparator;
import com.tachibana.downloader.receiver.BootReceiver;
import com.tachibana.downloader.settings.SettingsManager;

import org.acra.ACRA;
import org.acra.ReportField;

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
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import static com.tachibana.downloader.core.utils.MimeTypeUtils.DEFAULT_MIME_TYPE;
import static com.tachibana.downloader.core.utils.MimeTypeUtils.MIME_TYPE_DELIMITER;

public class Utils
{
    public static final String INFINITY_SYMBOL = "\u221e";
    public static final String HTTP_PREFIX = "http";
    public static final String DEFAULT_DOWNLOAD_FILENAME = "downloadfile";
    private static final String CONTENT_DISPOSITION_PATTERN = "attachment;\\s*filename\\s*=\\s*\"([^\"]*)\"";

    public static final String DEFAULT_NOTIFY_CHAN_ID = "com.tachibana.downloader.DEFAULT_NOTIFY_CHAN";
    public static final String FOREGROUND_NOTIFY_CHAN_ID = "com.tachibana.downloader.FOREGROUND_NOTIFY_CHAN";
    public static final String ACTIVE_DOWNLOADS_NOTIFY_CHAN_ID = "com.tachibana.downloader.CTIVE_DOWNLOADS_NOTIFY_CHAN";
    public static final String PENDING_DOWNLOADS_NOTIFY_CHAN_ID = "com.tachibana.downloader.PENDING_DOWNLOADS_NOTIFY_CHAN";
    public static final String COMPLETED_DOWNLOADS_NOTIFY_CHAN_ID = "com.tachibana.downloader.COMPLETED_DOWNLOADS_NOTIFY_CHAN";

    private static SystemFacade systemFacade;

    public synchronized static SystemFacade getSystemFacade(@NonNull Context context)
    {
        if (systemFacade == null)
            systemFacade = new RealSystemFacade(context);

        return systemFacade;
    }

    @VisibleForTesting
    public synchronized static void setSystemFacade(@NonNull SystemFacade systemFacade)
    {
        Utils.systemFacade = systemFacade;
    }

    public static void makeNotifyChans(@NonNull Context context,
                                       @NonNull NotificationManager notifyManager)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;

        ArrayList<NotificationChannel> channels = new ArrayList<>();

        channels.add(new NotificationChannel(DEFAULT_NOTIFY_CHAN_ID,
                context.getText(R.string.Default),
                NotificationManager.IMPORTANCE_DEFAULT));
        NotificationChannel foregroundChan = new NotificationChannel(FOREGROUND_NOTIFY_CHAN_ID,
                context.getString(R.string.foreground_notification),
                NotificationManager.IMPORTANCE_LOW);
        foregroundChan.setShowBadge(false);
        channels.add(foregroundChan);
        channels.add(new NotificationChannel(ACTIVE_DOWNLOADS_NOTIFY_CHAN_ID,
                context.getText(R.string.download_running),
                NotificationManager.IMPORTANCE_MIN));
        channels.add(new NotificationChannel(PENDING_DOWNLOADS_NOTIFY_CHAN_ID,
                context.getText(R.string.pending),
                NotificationManager.IMPORTANCE_LOW));
        channels.add(new NotificationChannel(COMPLETED_DOWNLOADS_NOTIFY_CHAN_ID,
                context.getText(R.string.finished),
                NotificationManager.IMPORTANCE_DEFAULT));

        notifyManager.createNotificationChannels(channels);
    }

    /*
     * Workaround for start service in Android 8+ if app no started.
     * We have a window of time to get around to calling startForeground() before we get ANR,
     * if work is longer than a millisecond but less than a few seconds.
     */

    public static void startServiceBackground(@NonNull Context context, @NonNull Intent i)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(i);
        else
            context.startService(i);
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

    public static int getThemePreference(@NonNull Context appContext)
    {
        return SettingsManager.getInstance(appContext)
                .getPreferences().getInt(appContext.getString(R.string.pref_key_theme),
                                         SettingsManager.Default.theme(appContext));
    }

    public static int getAppTheme(@NonNull Context appContext)
    {
        int theme = getThemePreference(appContext);

        if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_light_value)))
            return R.style.AppTheme;
        else if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_dark_value)))
            return R.style.AppTheme_Dark;
        else if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_black_value)))
            return R.style.AppTheme_Black;

        return R.style.AppTheme;
    }

    public static int getTranslucentAppTheme(@NonNull Context appContext)
    {
        int theme = getThemePreference(appContext);

        if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_light_value)))
            return R.style.AppTheme_Translucent;
        else if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_dark_value)))
            return R.style.AppTheme_Translucent_Dark;
        else if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_black_value)))
            return R.style.AppTheme_Translucent_Black;

        return R.style.AppTheme_Translucent;
    }

    public static int getSettingsTheme(@NonNull Context appContext)
    {
        int theme = getThemePreference(appContext);

        if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_light_value)))
            return R.style.AppTheme_Settings;
        else if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_dark_value)))
            return R.style.AppTheme_Settings_Dark;
        else if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_black_value)))
            return R.style.AppTheme_Settings_Black;

        return R.style.AppTheme_Settings;
    }

    /*
     * Colorize the progress bar in the accent color (for pre-Lollipop).
     */

    public static void colorizeProgressBar(@NonNull Context context,
                                           @NonNull ProgressBar progress)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            progress.getProgressDrawable().setColorFilter(ContextCompat.getColor(context, R.color.accent),
                    android.graphics.PorterDuff.Mode.SRC_IN);
    }

    /*
     * Returns the first item from clipboard.
     */

    @Nullable
    public static String getClipboard(@NonNull Context context)
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

    public static String getHttpFileName(@NonNull String decodedUrl, String contentDisposition, String contentLocation)
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
            if (!decodedUrl.endsWith("/") && decodedUrl.indexOf('?') < 0) {
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

    private static String parseContentDisposition(@NonNull String contentDisposition)
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

    public static boolean checkConnectivity(@NonNull Context context)
    {
        SystemFacade systemFacade = getSystemFacade(context);
        NetworkInfo netInfo = systemFacade.getActiveNetworkInfo();

        return netInfo != null && netInfo.isConnected() && isNetworkTypeAllowed(context);
    }

    public static boolean isNetworkTypeAllowed(@NonNull Context context)
    {
        SystemFacade systemFacade = getSystemFacade(context);

        SharedPreferences pref = SettingsManager.getInstance(context).getPreferences();
        boolean enableRoaming = pref.getBoolean(context.getString(R.string.pref_key_enable_roaming),
                                                SettingsManager.Default.enableRoaming);
        boolean unmeteredOnly = pref.getBoolean(context.getString(R.string.pref_key_umnetered_connections_only),
                                                SettingsManager.Default.unmeteredConnectionsOnly);

        boolean noUnmeteredOnly;
        boolean noRoaming;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities caps = systemFacade.getNetworkCapabilities();
            /*
             * Use ConnectivityManager#isActiveNetworkMetered() instead of NetworkCapabilities#NET_CAPABILITY_NOT_METERED,
             * since Android detection VPN as metered, including on Android 9, oddly enough.
             * I think this is due to what VPN services doesn't use setUnderlyingNetworks() method.
             *
             * See for details: https://developer.android.com/about/versions/pie/android-9.0-changes-all#network-capabilities-vpn
             */
            boolean unmetered = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                    !systemFacade.isActiveNetworkMetered();
            noUnmeteredOnly = !unmeteredOnly || unmetered;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                noRoaming = !enableRoaming || caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
            } else {
                NetworkInfo netInfo = systemFacade.getActiveNetworkInfo();
                noRoaming = netInfo != null && !(enableRoaming && netInfo.isRoaming());
            }

        } else {
            NetworkInfo netInfo = systemFacade.getActiveNetworkInfo();
            if (netInfo == null) {
                noUnmeteredOnly = false;
                noRoaming = false;
            } else {
                noUnmeteredOnly = !unmeteredOnly || !systemFacade.isActiveNetworkMetered();
                noRoaming = !(enableRoaming && netInfo.isRoaming());
            }
        }

        return noUnmeteredOnly && noRoaming;
    }

    public static boolean isMetered(@NonNull Context context)
    {
        SystemFacade systemFacade = getSystemFacade(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities caps = systemFacade.getNetworkCapabilities();
            return caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                    systemFacade.isActiveNetworkMetered();
        } else {
            return systemFacade.isActiveNetworkMetered();
        }
    }

    public static boolean isRoaming(@NonNull Context context)
    {
        SystemFacade systemFacade = getSystemFacade(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            NetworkCapabilities caps = systemFacade.getNetworkCapabilities();
            return caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        } else {
            NetworkInfo netInfo = systemFacade.getActiveNetworkInfo();
            return netInfo != null && netInfo.isRoaming();
        }
    }

    /*
     * Don't use app context (its doesn't reload after configuration changes)
     */

    public static boolean isTwoPane(@NonNull Context context)
    {
        return context.getResources().getBoolean(R.bool.isTwoPane);
    }

    /*
     * Tablets (from 7"), notebooks, TVs
     *
     * Don't use app context (its doesn't reload after configuration changes)
     */

    public static boolean isLargeScreenDevice(Context context)
    {
        return context.getResources().getBoolean(R.bool.isLargeScreenDevice);
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

    static public String getHostFromUrl(@NonNull String url)
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

    public static int getAttributeColor(@NonNull Context context, int attributeId)
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

    public static Intent makeFileShareIntent(@NonNull Context context,
                                             @NonNull List<DownloadItem> items)
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

        if (itemsUri.size() == 1)
            i.putExtra(Intent.EXTRA_STREAM, itemsUri.get(0));
        else if (itemsUri.size() > 1)
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, itemsUri);

        /* If there is exactly one item shared, set the mail title */
        if (items.size() == 1)
            i.putExtra(Intent.EXTRA_SUBJECT, items.get(0).info.fileName);

        i.setAction(intentAction);
        i.setType(intentMimeType);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return i;
    }

    public static Intent createOpenFileIntent(@NonNull Context context, @NonNull DownloadInfo info)
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
     */

    public static String getSystemUserAgent(@NonNull Context context)
    {
        return WebSettings.getDefaultUserAgent(context);
    }

    public static boolean checkStoragePermission(@NonNull Context context)
    {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static Intent makeShareUrlIntent(@NonNull List<String> urlList)
    {
        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "url");

        if (!urlList.isEmpty()) {
            if (urlList.size() == 1)
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, urlList.get(0));
            else
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT,
                        TextUtils.join(Utils.getLineSeparator(), urlList));
        }

        return sharingIntent;
    }

    public static Intent makeShareUrlIntent(@NonNull String url)
    {
        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "url");
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, url);

        return sharingIntent;
    }

    /*
     * Return system text line separator (in android it '\n').
     */

    public static String getLineSeparator()
    {
        return System.getProperty("line.separator");
    }

    /*
     * Starting with the version of Android 8.0,
     * setting notifications from the app preferences isn't working,
     * you can change them only in the settings of Android 8.0
     */

    public static void applyLegacyNotifySettings(@NonNull Context appContext,
                                                 @NonNull NotificationCompat.Builder builder)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            return;

        SharedPreferences pref = SettingsManager.getInstance(appContext).getPreferences();
        
        if (pref.getBoolean(appContext.getString(R.string.pref_key_play_sound_notify),
                SettingsManager.Default.playSoundNotify)) {
            Uri sound = Uri.parse(pref.getString(appContext.getString(R.string.pref_key_notify_sound),
                    SettingsManager.Default.notifySound));
            builder.setSound(sound);
        }

        if (pref.getBoolean(appContext.getString(R.string.pref_key_vibration_notify),
                SettingsManager.Default.vibrationNotify))
            builder.setVibrate(new long[] {1000}); /* ms */

        if (pref.getBoolean(appContext.getString(R.string.pref_key_led_indicator_notify),
                SettingsManager.Default.ledIndicatorNotify)) {
            int color = pref.getInt(appContext.getString(R.string.pref_key_led_indicator_color_notify),
                    SettingsManager.Default.ledIndicatorColorNotify(appContext));
            builder.setLights(color, 1000, 1000); /* ms */
        }
    }

    public static int getDefaultBatteryLowLevel()
    {
        return Resources.getSystem().getInteger(
                Resources.getSystem().getIdentifier("config_lowBatteryWarningLevel", "integer", "android"));
    }

    public static float getBatteryLevel(@NonNull Context context)
    {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        /* Error checking that probably isn't needed but I added just in case */
        if (level == -1 || scale == -1)
            return 50.0f;

        return ((float) level / (float) scale) * 100.0f;
    }

    public static boolean isBatteryCharging(@NonNull Context context)
    {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
    }

    public static boolean isBatteryLow(@NonNull Context context)
    {
        return Utils.getBatteryLevel(context) <= Utils.getDefaultBatteryLowLevel();
    }

    public static boolean isBatteryBelowThreshold(@NonNull Context context, int threshold)
    {
        return Utils.getBatteryLevel(context) <= threshold;
    }

    public static void enableBootReceiver(@NonNull Context appContext, boolean enable)
    {
        int flag = (enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        ComponentName bootReceiver = new ComponentName(appContext, BootReceiver.class);
        appContext.getPackageManager()
                .setComponentEnabledSetting(bootReceiver, flag, PackageManager.DONT_KILL_APP);
    }

    public static void reportError(@NonNull Throwable error,
                                   String comment)
    {
        if (comment != null)
            ACRA.getErrorReporter().putCustomData(ReportField.USER_COMMENT.toString(), comment);

        ACRA.getErrorReporter().handleSilentException(error);
    }

    public static String getAppVersionName(@NonNull Context context)
    {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            /* Ignore */
        }

        return null;
    }

    public static String getScheme(@NonNull String url)
    {
        int indexColon = url.indexOf(':');
        if (indexColon <= 0)
            return null;

        return url.substring(0, indexColon).toLowerCase();
    }

    public static List<DrawerGroup> getNavigationDrawerItems(@NonNull Context context,
                                                             @NonNull SharedPreferences localPref)
    {
        Resources res = context.getResources();

        ArrayList<DrawerGroup> groups = new ArrayList<>();

        DrawerGroup category = new DrawerGroup(res.getInteger(R.integer.drawer_category_id),
                res.getString(R.string.drawer_category),
                localPref.getBoolean(res.getString(R.string.drawer_category_is_expanded), true));
        category.selectItem(localPref.getLong(res.getString(R.string.drawer_category_selected_item),
                                              DrawerGroup.DEFAULT_SELECTED_ID));

        DrawerGroup status = new DrawerGroup(res.getInteger(R.integer.drawer_status_id),
                res.getString(R.string.drawer_status),
                localPref.getBoolean(res.getString(R.string.drawer_status_is_expanded), false));
        status.selectItem(localPref.getLong(res.getString(R.string.drawer_status_selected_item),
                                            DrawerGroup.DEFAULT_SELECTED_ID));

        DrawerGroup dateAdded = new DrawerGroup(res.getInteger(R.integer.drawer_date_added_id),
                res.getString(R.string.drawer_date_added),
                localPref.getBoolean(res.getString(R.string.drawer_time_is_expanded), false));
        dateAdded.selectItem(localPref.getLong(res.getString(R.string.drawer_time_selected_item),
                                               DrawerGroup.DEFAULT_SELECTED_ID));

        DrawerGroup sorting = new DrawerGroup(res.getInteger(R.integer.drawer_sorting_id),
                res.getString(R.string.drawer_sorting),
                localPref.getBoolean(res.getString(R.string.drawer_sorting_is_expanded), false));
        sorting.selectItem(localPref.getLong(res.getString(R.string.drawer_sorting_selected_item),
                                             DrawerGroup.DEFAULT_SELECTED_ID));

        category.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_category_all_id),
                R.drawable.ic_all_inclusive_grey600_24dp, res.getString(R.string.all)));
        category.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_category_others_id),
                R.drawable.ic_file_grey600_24dp, res.getString(R.string.drawer_category_others)));
        category.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_category_documents_id),
                R.drawable.ic_file_document_grey600_24dp, res.getString(R.string.drawer_category_documents)));
        category.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_category_images_id),
                R.drawable.ic_image_grey600_24dp, res.getString(R.string.drawer_category_images)));
        category.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_category_video_id),
                R.drawable.ic_video_grey600_24dp, res.getString(R.string.drawer_category_video)));
        category.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_category_apk_id),
                R.drawable.ic_android_grey600_24dp, res.getString(R.string.drawer_category_apk)));
        category.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_category_audio_id),
                R.drawable.ic_music_note_grey600_24dp, res.getString(R.string.drawer_category_audio)));
        category.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_category_archives_id),
                R.drawable.ic_zip_box_grey600_24dp, res.getString(R.string.drawer_category_archives)));

        status.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_status_all_id),
                R.drawable.ic_all_inclusive_grey600_24dp, res.getString(R.string.all)));
        status.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_status_running_id),
                R.drawable.ic_play_circle_outline_grey600_24dp, res.getString(R.string.drawer_status_running)));
        status.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_status_stopped_id),
                R.drawable.ic_stop_circle_outline_grey600_24dp, res.getString(R.string.drawer_status_stopped)));

        dateAdded.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_date_added_all_id),
                R.drawable.ic_all_inclusive_grey600_24dp, res.getString(R.string.all)));
        dateAdded.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_date_added_today_id),
                R.drawable.ic_calendar_today_grey600_24dp, res.getString(R.string.drawer_date_added_today)));
        dateAdded.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_date_added_yesterday_id),
                R.drawable.ic_calendar_yesterday_grey600_24dp, res.getString(R.string.drawer_date_added_yesterday)));
        dateAdded.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_date_added_week_id),
                R.drawable.ic_calendar_week_grey600_24dp, res.getString(R.string.drawer_date_added_week)));
        dateAdded.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_date_added_month_id),
                R.drawable.ic_calendar_month_grey600_24dp, res.getString(R.string.drawer_date_added_month)));
        dateAdded.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_date_added_year_id),
                R.drawable.ic_calendar_year_grey600_24dp, res.getString(R.string.drawer_date_added_year)));

        sorting.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_sorting_date_added_asc_id),
                R.drawable.ic_sort_ascending_grey600_24dp, res.getString(R.string.drawer_sorting_date_added)));
        sorting.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_sorting_date_added_desc_id),
                R.drawable.ic_sort_descending_grey600_24dp, res.getString(R.string.drawer_sorting_date_added)));
        sorting.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_sorting_name_asc_id),
                R.drawable.ic_sort_ascending_grey600_24dp, res.getString(R.string.drawer_sorting_name)));
        sorting.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_sorting_name_desc_id),
                R.drawable.ic_sort_descending_grey600_24dp, res.getString(R.string.drawer_sorting_name)));
        sorting.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_sorting_size_asc_id),
                R.drawable.ic_sort_ascending_grey600_24dp, res.getString(R.string.drawer_sorting_size)));
        sorting.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_sorting_size_desc_id),
                R.drawable.ic_sort_descending_grey600_24dp, res.getString(R.string.drawer_sorting_size)));
        sorting.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_sorting_category_asc_id),
                R.drawable.ic_sort_ascending_grey600_24dp, res.getString(R.string.drawer_sorting_category)));
        sorting.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_sorting_category_desc_id),
                R.drawable.ic_sort_descending_grey600_24dp, res.getString(R.string.drawer_sorting_category)));
        sorting.items.add(new DrawerGroupItem(res.getInteger(R.integer.drawer_sorting_no_sorting_id),
                R.drawable.ic_sort_off_grey600_24dp, res.getString(R.string.drawer_sorting_no_sorting)));

        groups.add(category);
        groups.add(status);
        groups.add(dateAdded);
        groups.add(sorting);

        return groups;
    }

    public static DownloadSortingComparator getDrawerGroupItemSorting(@NonNull Context context,
                                                                      long itemId)
    {
        Resources res = context.getResources();
        if (itemId == res.getInteger(R.integer.drawer_sorting_no_sorting_id))
            return new DownloadSortingComparator(new DownloadSorting(DownloadSorting.SortingColumns.none, DownloadSorting.Direction.ASC));
        else if (itemId == res.getInteger(R.integer.drawer_sorting_name_asc_id))
            return new DownloadSortingComparator(new DownloadSorting(DownloadSorting.SortingColumns.name, DownloadSorting.Direction.ASC));
        else if (itemId == res.getInteger(R.integer.drawer_sorting_name_desc_id))
            return new DownloadSortingComparator(new DownloadSorting(DownloadSorting.SortingColumns.name, DownloadSorting.Direction.DESC));
        else if (itemId == res.getInteger(R.integer.drawer_sorting_size_asc_id))
            return new DownloadSortingComparator(new DownloadSorting(DownloadSorting.SortingColumns.size, DownloadSorting.Direction.ASC));
        else if (itemId == res.getInteger(R.integer.drawer_sorting_size_desc_id))
            return new DownloadSortingComparator(new DownloadSorting(DownloadSorting.SortingColumns.size, DownloadSorting.Direction.DESC));
        else if (itemId == res.getInteger(R.integer.drawer_sorting_date_added_asc_id))
            return new DownloadSortingComparator(new DownloadSorting(DownloadSorting.SortingColumns.dateAdded, DownloadSorting.Direction.ASC));
        else if (itemId == res.getInteger(R.integer.drawer_sorting_date_added_desc_id))
            return new DownloadSortingComparator(new DownloadSorting(DownloadSorting.SortingColumns.dateAdded, DownloadSorting.Direction.DESC));
        else if (itemId == res.getInteger(R.integer.drawer_sorting_category_asc_id))
            return new DownloadSortingComparator(new DownloadSorting(DownloadSorting.SortingColumns.category, DownloadSorting.Direction.ASC));
        else if (itemId == res.getInteger(R.integer.drawer_sorting_category_desc_id))
            return new DownloadSortingComparator(new DownloadSorting(DownloadSorting.SortingColumns.category, DownloadSorting.Direction.DESC));
        else
            return new DownloadSortingComparator(new DownloadSorting(DownloadSorting.SortingColumns.none, DownloadSorting.Direction.ASC));
    }

    public static DownloadFilter getDrawerGroupCategoryFilter(@NonNull Context context,
                                                              long itemId)
    {
        Resources res = context.getResources();
        if (itemId == res.getInteger(R.integer.drawer_category_all_id))
            return DownloadFilterCollection.all();
        else if (itemId == res.getInteger(R.integer.drawer_category_others_id))
            return DownloadFilterCollection.category(MimeTypeUtils.Category.OTHER);
        else if (itemId == res.getInteger(R.integer.drawer_category_documents_id))
            return DownloadFilterCollection.category(MimeTypeUtils.Category.DOCUMENT);
        else if (itemId == res.getInteger(R.integer.drawer_category_images_id))
            return DownloadFilterCollection.category(MimeTypeUtils.Category.IMAGE);
        else if (itemId == res.getInteger(R.integer.drawer_category_video_id))
            return DownloadFilterCollection.category(MimeTypeUtils.Category.VIDEO);
        else if (itemId == res.getInteger(R.integer.drawer_category_apk_id))
            return DownloadFilterCollection.category(MimeTypeUtils.Category.APK);
        else if (itemId == res.getInteger(R.integer.drawer_category_audio_id))
            return DownloadFilterCollection.category(MimeTypeUtils.Category.AUDIO);
        else if (itemId == res.getInteger(R.integer.drawer_category_archives_id))
            return DownloadFilterCollection.category(MimeTypeUtils.Category.ARCHIVE);
        else
            return DownloadFilterCollection.all();
    }

    public static DownloadFilter getDrawerGroupStatusFilter(@NonNull Context context,
                                                            long itemId)
    {
        Resources res = context.getResources();
        if (itemId == res.getInteger(R.integer.drawer_status_all_id))
            return DownloadFilterCollection.all();
        else if (itemId == res.getInteger(R.integer.drawer_status_running_id))
            return DownloadFilterCollection.statusRunning();
        else if (itemId == res.getInteger(R.integer.drawer_status_stopped_id))
            return DownloadFilterCollection.statusStopped();
        else
            return DownloadFilterCollection.all();
    }

    public static DownloadFilter getDrawerGroupDateAddedFilter(@NonNull Context context,
                                                               long itemId)
    {
        Resources res = context.getResources();
        if (itemId == res.getInteger(R.integer.drawer_date_added_all_id))
            return DownloadFilterCollection.all();
        else if (itemId == res.getInteger(R.integer.drawer_date_added_today_id))
            return DownloadFilterCollection.dateAddedToday();
        else if (itemId == res.getInteger(R.integer.drawer_date_added_yesterday_id))
            return DownloadFilterCollection.dateAddedYesterday();
        else if (itemId == res.getInteger(R.integer.drawer_date_added_week_id))
            return DownloadFilterCollection.dateAddedWeek();
        else if (itemId == res.getInteger(R.integer.drawer_date_added_month_id))
            return DownloadFilterCollection.dateAddedMonth();
        else if (itemId == res.getInteger(R.integer.drawer_date_added_year_id))
            return DownloadFilterCollection.dateAddedYear();
        else
            return DownloadFilterCollection.all();
    }
}
