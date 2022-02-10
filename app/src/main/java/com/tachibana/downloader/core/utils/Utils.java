/*
 * Copyright (C) 2018-2020 Tachibana General Laboratories, LLC
 * Copyright (C) 2018-2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import static android.content.Context.POWER_SERVICE;
import static com.tachibana.downloader.core.model.data.StatusCode.STATUS_FILE_ERROR;
import static com.tachibana.downloader.core.model.data.StatusCode.STATUS_HTTP_DATA_ERROR;
import static com.tachibana.downloader.core.utils.MimeTypeUtils.DEFAULT_MIME_TYPE;
import static com.tachibana.downloader.core.utils.MimeTypeUtils.MIME_TYPE_DELIMITER;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

import android.Manifest;
import android.app.Activity;
import android.app.ForegroundServiceStartNotAllowedException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
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
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.util.TypedValue;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.RepositoryHelper;
import com.tachibana.downloader.core.filter.DownloadFilter;
import com.tachibana.downloader.core.filter.DownloadFilterCollection;
import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.settings.SettingsRepository;
import com.tachibana.downloader.core.sorting.DownloadSorting;
import com.tachibana.downloader.core.sorting.DownloadSortingComparator;
import com.tachibana.downloader.core.system.FileSystemFacade;
import com.tachibana.downloader.core.system.SafFileSystem;
import com.tachibana.downloader.core.system.SystemFacade;
import com.tachibana.downloader.core.system.SystemFacadeHelper;
import com.tachibana.downloader.receiver.BootReceiver;
import com.tachibana.downloader.ui.main.DownloadItem;
import com.tachibana.downloader.ui.main.drawer.DrawerGroup;
import com.tachibana.downloader.ui.main.drawer.DrawerGroupItem;

import org.acra.ACRA;
import org.acra.ReportField;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    public static final String INFINITY_SYMBOL = "\u221e";
    public static final String HTTP_PREFIX = "http";

    private static final Pattern ACCEPTED_URI_SCHEMA = Pattern.compile(
            "(?i)" +  /* Switch on case insensitive matching */
                    "(" +  /* Begin group for schema */
                    "(?:http|https|file|chrome)://" +
                    "|(?:inline|data|about|javascript):" +
                    ")" +
                    "(.*)"
    );

    /*
     * Workaround for start service in Android 8+ if app no started.
     * We have a window of time to get around to calling startForeground() before we get ANR,
     * if work is longer than a millisecond but less than a few seconds.
     */

    public static void startServiceBackground(@NonNull Context context, @NonNull Intent i) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(i);
        else
            context.startService(i);
    }

    public static int getThemePreference(@NonNull Context appContext) {
        return RepositoryHelper.getSettingsRepository(appContext).theme();
    }

    public static int getAppTheme(@NonNull Context appContext) {
        int theme = getThemePreference(appContext);

        if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_light_value)))
            return R.style.AppTheme;
        else if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_dark_value)))
            return R.style.AppTheme_Dark;
        else if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_black_value)))
            return R.style.AppTheme_Black;

        return R.style.AppTheme;
    }

    public static int getTranslucentAppTheme(@NonNull Context appContext) {
        int theme = getThemePreference(appContext);

        if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_light_value)))
            return R.style.AppTheme_Translucent;
        else if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_dark_value)))
            return R.style.AppTheme_Translucent_Dark;
        else if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_black_value)))
            return R.style.AppTheme_Translucent_Black;

        return R.style.AppTheme_Translucent;
    }

    public static int getSettingsTheme(@NonNull Context appContext) {
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
                                           @NonNull ProgressBar progress) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            progress.getProgressDrawable().setColorFilter(ContextCompat.getColor(context, R.color.accent),
                    android.graphics.PorterDuff.Mode.SRC_IN);
    }

    @Nullable
    public static ClipData getClipData(@NonNull Context context) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Activity.CLIPBOARD_SERVICE);
        if (!clipboard.hasPrimaryClip())
            return null;

        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0)
            return null;

        return clip;
    }

    public static List<CharSequence> getClipboardText(@NonNull Context context) {
        ArrayList<CharSequence> clipboardText = new ArrayList<>();

        ClipData clip = Utils.getClipData(context);
        if (clip == null)
            return clipboardText;

        for (int i = 0; i < clip.getItemCount(); i++) {
            CharSequence item = clip.getItemAt(i).getText();
            if (item == null)
                continue;
            clipboardText.add(item);
        }

        return clipboardText;
    }

    public static boolean checkConnectivity(@NonNull SettingsRepository pref,
                                            @NonNull SystemFacade systemFacade) {
        NetworkInfo netInfo = systemFacade.getActiveNetworkInfo();

        return netInfo != null && netInfo.isConnected() && isNetworkTypeAllowed(pref, systemFacade);
    }

    public static boolean isNetworkTypeAllowed(@NonNull SettingsRepository pref,
                                               @NonNull SystemFacade systemFacade) {
        boolean enableRoaming = pref.enableRoaming();
        boolean unmeteredOnly = pref.unmeteredConnectionsOnly();

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

    public static boolean isMetered(@NonNull SystemFacade systemFacade) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities caps = systemFacade.getNetworkCapabilities();
            return caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                    systemFacade.isActiveNetworkMetered();
        } else {
            return systemFacade.isActiveNetworkMetered();
        }
    }

    public static boolean isRoaming(@NonNull SystemFacade systemFacade) {
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

    public static boolean isTwoPane(@NonNull Context context) {
        return context.getResources().getBoolean(R.bool.isTwoPane);
    }

    /*
     * Tablets (from 7"), notebooks, TVs
     *
     * Don't use app context (its doesn't reload after configuration changes)
     */

    public static boolean isLargeScreenDevice(Context context) {
        return context.getResources().getBoolean(R.bool.isLargeScreenDevice);
    }

    public static long calcETA(long totalBytes, long curBytes, long speed) {
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

    static public String getHostFromUrl(@NonNull String url) {
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

    public static int getAttributeColor(@NonNull Context context, int attributeId) {
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

    public static boolean isSafPath(@NonNull Context appContext, @NonNull Uri path) {
        return SafFileSystem.getInstance(appContext).isSafPath(path);
    }

    public static boolean isFileSystemPath(@NonNull Uri path) {
        String scheme = path.getScheme();
        if (scheme == null)
            throw new IllegalArgumentException("Scheme of " + path.getPath() + " is null");

        return scheme.equals(ContentResolver.SCHEME_FILE);
    }

    public static Intent makeFileShareIntent(@NonNull Context context,
                                             @NonNull List<DownloadItem> items) {
        FileSystemFacade fs = SystemFacadeHelper.getFileSystemFacade(context);

        Intent i = new Intent();
        String intentAction;
        ArrayList<Uri> itemsUri = new ArrayList<>();
        String intentMimeType = "";
        String[] intentMimeParts = {"", ""};

        for (DownloadItem item : items) {
            if (item == null)
                continue;

            DownloadInfo info = item.info;
            Uri filePath = fs.getFileUri(info.dirPath, info.fileName);
            if (filePath != null && fs.exists(filePath)) {
                if (Utils.isFileSystemPath(filePath))
                    itemsUri.add(FileProvider.getUriForFile(context,
                            context.getPackageName() + ".provider",
                            new File(filePath.getPath())));
                else
                    itemsUri.add(filePath);
            }

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

        if (itemsUri.isEmpty()) {
            return null;
        } else {
            if (itemsUri.size() == 1) {
                intentAction = Intent.ACTION_SEND;
                i.putExtra(Intent.EXTRA_STREAM, itemsUri.get(0));
                /* If there is exactly one item shared, set the mail title */
                i.putExtra(Intent.EXTRA_SUBJECT, items.get(0).info.fileName);
            } else {
                intentAction = Intent.ACTION_SEND_MULTIPLE;
                i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, itemsUri);
            }

            i.setAction(intentAction);
            i.setType(intentMimeType);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            return i;
        }
    }

    public static Intent createOpenFileIntent(@NonNull Context context, @NonNull DownloadInfo info) {
        FileSystemFacade fs = SystemFacadeHelper.getFileSystemFacade(context);
        Uri filePath = fs.getFileUri(info.dirPath, info.fileName);
        if (filePath != null && fs.exists(filePath)) {
            return createOpenFileIntent(context, filePath, info.mimeType);
        } else {
            return null;
        }
    }

    public static Intent createOpenFileIntent(@NonNull Context context, Uri filePath, String mimeType) {
        Intent i = new Intent();
        i.setAction(Intent.ACTION_VIEW);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (filePath == null || mimeType == null)
            return i;

        if (Utils.isFileSystemPath(filePath))
            i.setDataAndType(FileProvider.getUriForFile(context,
                    context.getPackageName() + ".provider",
                    new File(filePath.getPath())),
                    mimeType);
        else
            i.setDataAndType(filePath, mimeType);

        return i;
    }

    public static boolean checkStoragePermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean shouldRequestStoragePermission(@NonNull Activity activity) {
        return ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        );
    }

    public static Intent makeShareUrlIntent(@NonNull List<String> urlList) {
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

    public static Intent makeShareUrlIntent(@NonNull String url) {
        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "url");
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, url);

        return sharingIntent;
    }

    /*
     * Return system text line separator (in android it '\n').
     */

    public static String getLineSeparator() {
        return System.getProperty("line.separator");
    }

    public static int getDefaultBatteryLowLevel() {
        return Resources.getSystem().getInteger(
                Resources.getSystem().getIdentifier("config_lowBatteryWarningLevel", "integer", "android"));
    }

    public static float getBatteryLevel(@NonNull Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        /* Error checking that probably isn't needed but I added just in case */
        if (level == -1 || scale == -1)
            return 50.0f;

        return ((float) level / (float) scale) * 100.0f;
    }

    public static boolean isBatteryCharging(@NonNull Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
    }

    public static boolean isBatteryLow(@NonNull Context context) {
        return Utils.getBatteryLevel(context) <= Utils.getDefaultBatteryLowLevel();
    }

    public static boolean isBatteryBelowThreshold(@NonNull Context context, int threshold) {
        return Utils.getBatteryLevel(context) <= threshold;
    }

    public static void enableBootReceiver(@NonNull Context appContext, boolean enable) {
        int flag = (enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        ComponentName bootReceiver = new ComponentName(appContext, BootReceiver.class);
        appContext.getPackageManager()
                .setComponentEnabledSetting(bootReceiver, flag, PackageManager.DONT_KILL_APP);
    }

    public static void reportError(@NonNull Throwable error,
                                   String comment) {
        if (comment != null)
            ACRA.getErrorReporter().putCustomData(ReportField.USER_COMMENT.toString(), comment);

        ACRA.getErrorReporter().handleSilentException(error);
    }

    public static String getAppVersionName(@NonNull Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            /* Ignore */
        }

        return null;
    }

    public static String getScheme(@NonNull String url) {
        int indexColon = url.indexOf(':');
        if (indexColon <= 0)
            return null;

        return url.substring(0, indexColon).toLowerCase();
    }

    public static boolean isStatusRetryable(int statusCode) {
        switch (statusCode) {
            case STATUS_HTTP_DATA_ERROR:
            case HTTP_UNAVAILABLE:
            case HTTP_INTERNAL_ERROR:
            case STATUS_FILE_ERROR:
                return true;
            default:
                return false;
        }
    }

    public static boolean isWebViewAvailable(@NonNull Context context) {
        return context.getPackageManager().hasSystemFeature("android.software.webview");
    }

    public static void enableBrowserLauncherIcon(@NonNull Context context, boolean enable) {
        PackageManager pm = context.getPackageManager();
        int flag = (enable ?
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        pm.setComponentEnabledSetting(
                new ComponentName(context, "com.tachibana.downloader.ui.browser.BrowserIcon"),
                flag, PackageManager.DONT_KILL_APP);
    }

    public static void disableBrowserFromSystem(@NonNull Context context, boolean disable) {
        PackageManager pm = context.getPackageManager();
        int flag = (disable ?
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED :
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        pm.setComponentEnabledSetting(
                new ComponentName(context, "com.tachibana.downloader.ui.browser.Browser"),
                flag, PackageManager.DONT_KILL_APP);
    }

    public static void deleteCookies() {
        CookieManager cookieManager = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        } else {
            cookieManager.removeAllCookie();
        }
    }

    /*
     * Formats a launch-able uri out of the template uri by
     * replacing the template parameters with actual values
     */
    public static String getFormattedSearchUrl(String templateUrl, String query) {
        return URLUtil.composeSearchUrl(query, templateUrl, "{searchTerms}");
    }

    /*
     * Attempts to determine whether user input is a URL or search
     * terms. Anything with a space is passed to search if canBeSearch is true.
     * Converts to lowercase any mistakenly uppercased schema (i.e.,
     * "Http://" converts to "http://"
     */
    public static String smartUrlFilter(@NonNull String url) {
        String inUrl = url.trim();
        boolean hasSpace = inUrl.indexOf(' ') != -1;
        Matcher matcher = ACCEPTED_URI_SCHEMA.matcher(inUrl);
        if (matcher.matches()) {
            /* Force scheme to lowercase */
            String scheme = matcher.group(1);
            String lcScheme = scheme.toLowerCase(Locale.getDefault());
            if (!lcScheme.equals(scheme))
                inUrl = lcScheme + matcher.group(2);
            if (hasSpace && Patterns.WEB_URL.matcher(inUrl).matches())
                inUrl = inUrl.replace(" ", "%20");

            return inUrl;
        }

        return (!hasSpace && Patterns.WEB_URL.matcher(inUrl).matches() ?
                URLUtil.guessUrl(inUrl) :
                null);
    }

    /*
     * If an HTML document is returned, then need a Referer from the site URL to download a required file
     */
    public static boolean needsReferer(@Nullable String mimeType, @Nullable String extension) {
        return "text/html".equals(mimeType) || "html".equals(extension) || "htm".equals(extension);
    }

    public static boolean shouldShowBatteryOptimizationDialog(@NonNull Context appContext) {
        var pref = RepositoryHelper.getSettingsRepository(appContext);
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && pref.askDisableBatteryOptimization()
                && !isBatteryOptimizationEnabled(appContext);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean isBatteryOptimizationEnabled(@NonNull Context context) {
        var packageName = context.getPackageName();
        var pm = (PowerManager) context.getSystemService(POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(packageName);
    }

    public static void requestDisableBatteryOptimization(@NonNull Context context) {
        var uri = Uri.fromParts("package", context.getPackageName(), null);
        var i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
        context.startActivity(i);
    }

    public static List<DrawerGroup> getNavigationDrawerItems(@NonNull Context context,
                                                             @NonNull SharedPreferences localPref) {
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
        final long DEFAULT_SORTING_ITEM = 1;
        sorting.selectItem(localPref.getLong(res.getString(R.string.drawer_sorting_selected_item),
                DEFAULT_SORTING_ITEM));

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
                                                                      long itemId) {
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
                                                              long itemId) {
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
                                                            long itemId) {
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
                                                               long itemId) {
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
