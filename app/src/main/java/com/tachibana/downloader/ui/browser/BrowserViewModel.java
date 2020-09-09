/*
 * Copyright (C) 2020 Tachibana General Laboratories, LLC
 * Copyright (C) 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.ui.browser;
import android.content.Intent;
import android.annotation.TargetApi;
import android.app.Application;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableField;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tachibana.downloader.core.RepositoryHelper;
import com.tachibana.downloader.core.model.data.entity.BrowserBookmark;
import com.tachibana.downloader.core.settings.SettingsRepository;
import com.tachibana.downloader.core.storage.BrowserRepository;
import com.tachibana.downloader.core.utils.Utils;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.PublishSubject;

public class BrowserViewModel extends AndroidViewModel
{
    @SuppressWarnings("unused")
    private static final String TAG = BrowserViewModel.class.getSimpleName();

    private static final String DESKTOP_DEVICE = "X11; Linux x86_64";
    private static final String DESKTOP_USER_AGENT_FALLBACK = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2049.0 Safari/537.36";
    private static final String PATTERN_USER_AGENT = "([^)]+ \\()([^)]+)(\\) .*)";
    private static final String HEADER_DNT = "DNT";

    public SettingsRepository pref;
    public BrowserRepository repo;
    private MutableLiveData<UrlFetchState> urlFetchState =
            new MutableLiveData<>(UrlFetchState.UNKNOWN);
    public ObservableField<String> url = new ObservableField<>();
    public ObservableField<String> title = new ObservableField<>();
    public ObservableBoolean isSecureConnection = new ObservableBoolean(false);
    public PublishSubject<DownloadRequest> downloadRequestEvent = PublishSubject.create();
    private boolean requestStop = false;
    private String mobileUserAgent;
    private String desktopUserAgent;
    private boolean isDesktopMode = false;
    private HashMap<String, String> requestHeaders;

    public enum UrlFetchState
    {
        UNKNOWN,
        PAGE_STARTED,
        FETCHING,
        PAGE_FINISHED;

        private int progress = 0;

        public UrlFetchState progress(int progress)
        {
            this.progress = progress;

            return this;
        }

        public int progress()
        {
            return progress;
        }
    }

    public BrowserViewModel(@NonNull Application application)
    {
        super(application);

        pref = RepositoryHelper.getSettingsRepository(application);
        repo = RepositoryHelper.getBrowserRepository(application);
        requestHeaders = new HashMap<>();
    }

    void initWebView(@NonNull WebView webView)
    {
        webView.setWebViewClient(webViewClient);
        webView.setWebChromeClient(webChromeClient);
        webView.setDownloadListener(downloadListener);

        WebSettings settings = webView.getSettings();

        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAppCachePath(getApplication().getCacheDir().getAbsolutePath());
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setGeolocationEnabled(false);

        initUserAgent(settings);

        applyPrefs(webView);
    }

    private void initUserAgent(WebSettings settings)
    {
        /*
         * Mobile: Remove "wv" from the WebView's user agent. Some websites don't work
         * properly if the browser reports itself as a simple WebView.
         * Desktop: Generate the desktop user agent starting from the mobile one so that
         * we always report the current engine version.
         */
        String userAgent = settings.getUserAgentString();
        Pattern pattern = Pattern.compile(PATTERN_USER_AGENT);
        Matcher matcher = pattern.matcher(userAgent);
        if (matcher.matches()) {
            String mobileDevice = matcher.group(2).replace("; wv", "");
            mobileUserAgent = matcher.group(1) + mobileDevice + matcher.group(3);
            desktopUserAgent = matcher.group(1) + DESKTOP_DEVICE + matcher.group(3)
                    .replace(" Mobile ", " ");
            settings.setUserAgentString(mobileDevice);
        } else {
            Log.e(TAG, "Couldn't parse the user agent: " + userAgent);
            mobileUserAgent = settings.getUserAgentString();
            desktopUserAgent = DESKTOP_USER_AGENT_FALLBACK;
        }
    }

    void applyPrefs(@NonNull WebView webView)
    {
        if (pref.browserDoNotTrack())
            requestHeaders.put(HEADER_DNT, "1");
        else
            requestHeaders.remove(HEADER_DNT);

        allowJavaScript(webView, pref.browserAllowJavaScript());
        enableCookies(pref.browserEnableCookies());
        enableCaching(webView, pref.browserEnableCaching());
        allowPopupWindows(webView, pref.browserAllowPopupWindows());
        enableDesktopMode(webView, isDesktopMode);
    }

    private void allowJavaScript(@NonNull WebView webView, boolean enable)
    {
        webView.getSettings().setJavaScriptEnabled(enable);
    }

    private void enableCaching(@NonNull WebView webView, boolean enable)
    {
        WebSettings settings = webView.getSettings();

        if (enable) {
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setAppCacheEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setDomStorageEnabled(true);
        } else {
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            settings.setAppCacheEnabled(false);
            settings.setDatabaseEnabled(false);
            settings.setDomStorageEnabled(false);
            WebStorage.getInstance().deleteAllData();
            webView.clearCache(true);
        }
    }

    private void allowPopupWindows(@NonNull WebView webView, boolean enable)
    {
        WebSettings settings = webView.getSettings();

        if (enable) {
            settings.setSupportMultipleWindows(true);
            settings.setJavaScriptCanOpenWindowsAutomatically(true);
        } else {
            settings.setSupportMultipleWindows(false);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
        }
    }

    private void enableCookies(boolean enable)
    {
        CookieManager.getInstance().setAcceptCookie(enable);
        if (!enable)
            Utils.deleteCookies();
    }

    void enableDesktopMode(@NonNull WebView webView, boolean enable)
    {
        isDesktopMode = enable;
        WebSettings settings = webView.getSettings();

        settings.setUserAgentString((enable ? desktopUserAgent : mobileUserAgent));
        settings.setUseWideViewPort(enable);
        settings.setLoadWithOverviewMode(enable);
    }

    boolean isDesktopMode()
    {
        return isDesktopMode;
    }

    public LiveData<UrlFetchState> observeUrlFetchState()
    {
        return urlFetchState;
    }

    void loadUrl(@NonNull WebView webView)
    {
        requestStop = false;

        String urlStr = url.get();
        if (TextUtils.isEmpty(urlStr))
            return;

        String fixedUrl = Utils.smartUrlFilter(urlStr);
        if (fixedUrl != null)
            urlStr = fixedUrl;
        else
            /* This is a search query */
            urlStr = Utils.getFormattedSearchUrl(pref.browserSearchEngine(), urlStr);

        url.set(urlStr);
        webView.loadUrl(urlStr, requestHeaders);
    }

    void stopLoading(@NonNull WebView webView)
    {
        webView.stopLoading();

        requestStop = true;
        urlFetchState.postValue(UrlFetchState.UNKNOWN);
    }

    void loadStartPage(@NonNull WebView webView)
    {
        url.set(pref.browserStartPage());
        loadUrl(webView);
    }

    Single<BrowserBookmark> addBookmark()
    {
        String urlStr = url.get();
        if (TextUtils.isEmpty(urlStr))
            return Single.error(new NullPointerException("Url is empty or null"));
        String titleStr = title.get();
        if (TextUtils.isEmpty(titleStr))
            titleStr = urlStr;

        BrowserBookmark bookmark = new BrowserBookmark(urlStr, titleStr, System.currentTimeMillis());

        return repo.addBookmark(bookmark).map((__) -> bookmark);
    }

    Maybe<BrowserBookmark> getBookmarkForCurrentPage()
    {
        String urlStr = url.get();
        if (TextUtils.isEmpty(urlStr))
            return Maybe.error(new NullPointerException("url is null"));

        return repo.getBookmarkByUrlSingle(urlStr).toMaybe();
    }

    Maybe<Boolean> isCurrentPageBookmarked()
    {
        return getBookmarkForCurrentPage()
                .map((bookmark) -> bookmark != null);
    }

    Observable<DownloadRequest> observeDownloadRequests()
    {
        return downloadRequestEvent;
    }

    private final WebViewClient webViewClient = new WebViewClient() {
        @TargetApi(Build.VERSION_CODES.N)
        @Override
        public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request)
        {
            
               if (request.getUrl().toString().startsWith("http")) {


                view.loadUrl(request.getUrl().toString());

            } else if (request.getUrl().toString().startsWith("mailto:")) {  // Load the email address in an external email program.
                // Use `ACTION_SENDTO` instead of `ACTION_SEND` so that only email programs are launched.
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);

                // Parse the url and set it as the data for the intent.
                emailIntent.setData(Uri.parse(request.getUrl().toString()));

                // Open the email program in a new task instead of as part of Browser.
                emailIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Make it so.
                view.getContext().startActivity(emailIntent);

                // Returning true indicates Browser is handling the URL by creating an intent.
                return true;
            } else if (request.getUrl().toString().startsWith("tel:")) {  // Load the phone number in the dialer.
                // Open the dialer and load the phone number, but wait for the user to place the call.
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);

                // Add the phone number to the intent.
                dialIntent.setData(Uri.parse(request.getUrl().toString()));

                // Open the dialer in a new task instead of as part of Browser.
                dialIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Make it so.
                view.getContext().startActivity(dialIntent);

                // Returning true indicates Browser is handling the URL by creating an intent.
                return true;
            } else {  // Load a system chooser to select an app that can handle the URL.
                // Open an app that can handle the URL.
                Intent genericIntent = new Intent(Intent.ACTION_VIEW);

                // Add the URL to the intent.
                genericIntent.setData(Uri.parse(request.getUrl().toString()));

                // List all apps that can handle the URL instead of just opening the first one.
                genericIntent.addCategory(Intent.CATEGORY_BROWSABLE);

                // Open the app in a new task instead of as part of Browser.
                genericIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Start the app or display a toast if no app is available to handle the URL.
                try {
                    view.getContext().startActivity(genericIntent);
                } catch (ActivityNotFoundException exception) {
                    // Toast.makeText(getActivity(),"unrecognized url",Toast.LENGTH_LONG).show();
                    Toast.makeText(view.getContext(),"unrecognized url",Toast.LENGTH_LONG).show();
                }

                // Returning true indicates Browser is handling the URL by creating an intent.
                return true;
            }

            return true;
            
        }

        @Override
        public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull String url)
        {
            
  if (url.startsWith("http")) {


                view.loadUrl(url);

            } else if (url.startsWith("mailto:")) {  // Load the email address in an external email program.
                // Use `ACTION_SENDTO` instead of `ACTION_SEND` so that only email programs are launched.
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);

                // Parse the url and set it as the data for the intent.
                emailIntent.setData(Uri.parse(url));

                // Open the email program in a new task instead of as part of Browser.
                emailIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Make it so.
                view.getContext().startActivity(emailIntent);

                // Returning true indicates Browser is handling the URL by creating an intent.
                return true;
            } else if (url.startsWith("tel:")) {  // Load the phone number in the dialer.
                // Open the dialer and load the phone number, but wait for the user to place the call.
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);

                // Add the phone number to the intent.
                dialIntent.setData(Uri.parse(url));

                // Open the dialer in a new task instead of as part of Browser.
                dialIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Make it so.
                view.getContext().startActivity(dialIntent);

                // Returning true indicates Browser is handling the URL by creating an intent.
                return true;
            } else {  // Load a system chooser to select an app that can handle the URL.
                // Open an app that can handle the URL.
                Intent genericIntent = new Intent(Intent.ACTION_VIEW);

                // Add the URL to the intent.
                genericIntent.setData(Uri.parse(url));

                // List all apps that can handle the URL instead of just opening the first one.
                genericIntent.addCategory(Intent.CATEGORY_BROWSABLE);

                // Open the app in a new task instead of as part of Browser.
                genericIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Start the app or display a toast if no app is available to handle the URL.
                try {
                    view.getContext().startActivity(genericIntent);
                } catch (ActivityNotFoundException exception) {
                   // Toast.makeText(getActivity(),"unrecognized url",Toast.LENGTH_LONG).show();
                    Toast.makeText(view.getContext(),"unrecognized url",Toast.LENGTH_LONG).show();
                }

                // Returning true indicates Browser is handling the URL by creating an intent.
                return true;
            }
            return true;

        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon)
        {
            super.onPageStarted(view, url, favicon);

            BrowserViewModel.this.url.set(url);

            isSecureConnection.set(url != null && url.startsWith("https"));
            if (!requestStop)
                urlFetchState.postValue(UrlFetchState.PAGE_STARTED);
        }

        @Override
        public void onPageFinished(WebView view, String url)
        {
            super.onPageFinished(view, url);

            String titleStr = view.getTitle();
            if (TextUtils.isEmpty(titleStr))
                titleStr = url;
            title.set(titleStr);

            if (!requestStop)
                urlFetchState.postValue(UrlFetchState.PAGE_FINISHED);
        }

          @Override
        public void onLoadResource(WebView view, String url) {
            super.onLoadResource(view, url);
            view.evaluateJavascript("document.querySelector('meta[name=\"viewport\"]').setAttribute('content', 'width=1024px, initial-scale=' + (window.screen.width / 1024));", null);
        }

    };

    private final WebChromeClient webChromeClient = new WebChromeClient() {
        @Override
        public void onProgressChanged(WebView view, int newProgress)
        {
            super.onProgressChanged(view, newProgress);

            if (!requestStop)
                urlFetchState.postValue(UrlFetchState.FETCHING.progress(newProgress));
        }
    };

    private final DownloadListener downloadListener = new DownloadListener() {
        @Override
        public void onDownloadStart(String url,
                                    String userAgent,
                                    String contentDisposition,
                                    String mimeType,
                                    long contentLength)
        {
            downloadRequestEvent.onNext(new DownloadRequest(url));
        }
    };

    public static class DownloadRequest
    {
        private String url;

        private DownloadRequest(@NonNull String url)
        {
            this.url = url;
        }

        @NonNull
        public String getUrl()
        {
            return url;
        }
    }
}
