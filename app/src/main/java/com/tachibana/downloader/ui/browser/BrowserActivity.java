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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tachibana.downloader.R;
import com.tachibana.downloader.core.model.data.entity.BrowserBookmark;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.databinding.ActivityBrowserBottomAppBarBinding;
import com.tachibana.downloader.databinding.ActivityBrowserTopAppBarBinding;
import com.tachibana.downloader.ui.FragmentCallback;
import com.tachibana.downloader.ui.SendTextToClipboard;
import com.tachibana.downloader.ui.adddownload.AddDownloadActivity;
import com.tachibana.downloader.ui.adddownload.AddInitParams;
import com.tachibana.downloader.ui.browser.bookmarks.BrowserBookmarksActivity;
import com.tachibana.downloader.ui.browser.bookmarks.EditBookmarkActivity;
import com.tachibana.downloader.ui.settings.SettingsActivity;

import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/*
 * A simple, WebView-based browser
 */

public class BrowserActivity extends AppCompatActivity
    implements FragmentCallback
{
    @SuppressWarnings("unused")
    private static final String TAG = BrowserActivity.class.getSimpleName();

    private static final String TAG_DOUBLE_BACK_PRESSED = "double_back_pressed";
    private static final String TAG_IS_CURRENT_PAGE_BOOKMARKED = "is_current_page_bookmarked";
    private static final String TAG_CONTEXT_MENU_DIALOG = "context_menu_dialog";
    private static final int REQUEST_CODE_SETTINGS = 1;
    private static final int REQUEST_CODE_BOOKMARKS = 2;
    private static final int REQUEST_CODE_EDIT_BOOKMARK = 3;
    private static final int REQUEST_CODE_ADD_DOWNLOAD = 4;
    private static final int REQUEST_CODE_COPY_TO_CLIPBOARD = 5;

    private BrowserViewModel viewModel;
    private WebView webView;
    private TextInputLayout addressLayout;
    private TextInputEditText addressInput;
    private CoordinatorLayout coordinatorLayout;
    private boolean doubleBackPressed = false;
    private boolean hideMenuButtons = false;
    private boolean isCurrentPageBookmarked = false;
    private CompositeDisposable disposables = new CompositeDisposable();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        if (!Utils.isWebViewAvailable(this)) {
            Toast.makeText(getApplicationContext(),
                    R.string.webview_is_required,
                    Toast.LENGTH_SHORT)
                    .show();
            finish();
        }

        setTheme(Utils.getAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(BrowserViewModel.class);
        viewModel.observeUrlFetchState().observe(this, this::handleUrlFetchState);

        initView();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayShowTitleEnabled(false);
        initAddressBar();
        initWebView();

        if (savedInstanceState != null) {
            doubleBackPressed = savedInstanceState.getBoolean(TAG_DOUBLE_BACK_PRESSED);
            isCurrentPageBookmarked = savedInstanceState.getBoolean(TAG_IS_CURRENT_PAGE_BOOKMARKED);
            webView.restoreState(savedInstanceState);
        } else {
            String url = getUrlFromIntent();
            if (url != null) {
                viewModel.url.set(url);
                viewModel.loadUrl(webView);
            } else {
                viewModel.loadStartPage(webView);
            }
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        disposables.clear();
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        observeDownloadRequests();
    }

    private void observeDownloadRequests()
    {
        disposables.add(viewModel.observeDownloadRequests()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((request) -> {
                    viewModel.stopLoading(webView);
                    showAddDownloadDialog(request.getUrl());
                }));
    }

    private void showAddDownloadDialog(String url)
    {
        if (url == null)
            return;

        AddInitParams initParams = new AddInitParams();
        initParams.url = url;

        Intent i = new Intent(this, AddDownloadActivity.class);
        i.putExtra(AddDownloadActivity.TAG_INIT_PARAMS, initParams);
        startActivityForResult(i, REQUEST_CODE_ADD_DOWNLOAD);
    }

    private void handleUrlFetchState(BrowserViewModel.UrlFetchState fetchState)
    {
        isCurrentPageBookmarked = false;
        invalidateOptionsMenu();
        if (fetchState == BrowserViewModel.UrlFetchState.PAGE_FINISHED) {
            checkIsCurrentPageBookmarked();
        }
    }

    private void checkIsCurrentPageBookmarked()
    {
        disposables.add(viewModel.isCurrentPageBookmarked()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((isBookmarked) -> {
                            isCurrentPageBookmarked = isBookmarked;
                            invalidateOptionsMenu();
                        },
                        (e) -> {
                            isCurrentPageBookmarked = false;
                            invalidateOptionsMenu();
                        })
        );
    }

    private String getUrlFromIntent()
    {
        Intent i = getIntent();
        if (i != null) {
            if (i.getData() != null)
                return i.getData().toString();
            else
                return i.getStringExtra(Intent.EXTRA_TEXT);
        }

        return null;
    }

    private void initView()
    {
        if (viewModel.pref.browserBottomAddressBar()) {
            ActivityBrowserBottomAppBarBinding binding =
                    DataBindingUtil.setContentView(this, R.layout.activity_browser_bottom_app_bar);
            binding.setLifecycleOwner(this);
            binding.setViewModel(viewModel);
            setSupportActionBar(binding.bottomBar);
            webView = binding.webView;
            addressLayout = binding.addressBar.addressLayout;
            addressInput = binding.addressBar.addressInput;
            coordinatorLayout = binding.coordinatorLayout;

        } else {
            ActivityBrowserTopAppBarBinding binding =
                    DataBindingUtil.setContentView(this, R.layout.activity_browser_top_app_bar);
            binding.setLifecycleOwner(this);
            binding.setViewModel(viewModel);
            setSupportActionBar(binding.toolbar);
            webView = binding.webView;
            addressLayout = binding.addressBar.addressLayout;
            addressInput = binding.addressBar.addressInput;
            coordinatorLayout = binding.coordinatorLayout;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState)
    {
        outState.putBoolean(TAG_DOUBLE_BACK_PRESSED, doubleBackPressed);
        outState.putBoolean(TAG_IS_CURRENT_PAGE_BOOKMARKED, isCurrentPageBookmarked);
        webView.saveState(outState);

        super.onSaveInstanceState(outState);
    }

    private void initWebView()
    {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            webView.setAnimationCacheEnabled(false);
            webView.setAlwaysDrawnWithCacheEnabled(false);
        }
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLongClickable(true);
        webView.setOnLongClickListener((v) -> {
            WebView.HitTestResult result = webView.getHitTestResult();
            switch (result.getType()) {
                case WebView.HitTestResult.SRC_ANCHOR_TYPE:
                case WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE:
                case WebView.HitTestResult.IMAGE_TYPE:
                    String url = result.getExtra();
                    if (url != null)
                        showContextMenu(url);
                    return true;
                default:
                    return false;
            }
        });
        viewModel.initWebView(webView);
    }

    private void initAddressBar()
    {
        KeyboardVisibilityEvent.setEventListener(this, this, (isOpen) -> {
            if (!isOpen)
                addressLayout.clearFocus();
        });

        addressLayout.setEndIconOnClickListener((v) -> viewModel.url.set(""));
        toggleClearButton(false);

        addressInput.setOnFocusChangeListener((v, hasFocus) -> {
            /* Move to the beginning of the address bar after keyboard hiding */
            if (!hasFocus)
                addressInput.setSelection(0);
            toggleMenuButtons(hasFocus);
            toggleClearButton(hasFocus && !TextUtils.isEmpty(viewModel.url.get()));
        });

        addressInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s)
            {
                toggleClearButton(s.length() > 0 && addressInput.hasFocus());
            }
        });

        addressInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                addressLayout.clearFocus();
                hideKeyboard();
                viewModel.loadUrl(webView);

                return true;
            }
            return false;
        });
    }

    private void toggleClearButton(boolean show)
    {
        addressLayout.setEndIconVisible(show);
    }

    private void toggleMenuButtons(boolean hide)
    {
        hideMenuButtons = hide;
        invalidateOptionsMenu();
    }

    private void hideKeyboard()
    {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(addressInput.getWindowToken(), 0);
    }

    private void showContextMenu(String url)
    {
        BrowserContextMenuDialog dialog = BrowserContextMenuDialog.newInstance(url);
        dialog.show(getSupportFragmentManager(), TAG_CONTEXT_MENU_DIALOG);
    }

    @Override
    public void fragmentFinished(Intent intent, ResultCode code)
    {
        if (code != ResultCode.OK)
            return;

        String action = intent.getAction();
        if (action == null)
            return;

        String url = intent.getStringExtra(BrowserContextMenuDialog.TAG_URL);
        switch (action) {
            case BrowserContextMenuDialog.TAG_ACTION_SHARE:
                makeShareDialog(url);
                break;
            case BrowserContextMenuDialog.TAG_ACTION_DOWNLOAD:
                showAddDownloadDialog(url);
                break;
            case BrowserContextMenuDialog.TAG_ACTION_COPY:
                showCopyToClipboardDialog(url);
                break;
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        if (hideMenuButtons)
            return false;

        getMenuInflater().inflate(R.menu.browser, menu);

        if (menu instanceof MenuBuilder){
            MenuBuilder menuBuilder = (MenuBuilder)menu;
            menuBuilder.setOptionalIconsVisible(true);
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        BrowserViewModel.UrlFetchState state = viewModel.observeUrlFetchState().getValue();
        MenuItem refresh = menu.findItem(R.id.refresh_menu);
        MenuItem stop = menu.findItem(R.id.stop_menu);
        boolean fetching = state == BrowserViewModel.UrlFetchState.FETCHING ||
                state == BrowserViewModel.UrlFetchState.PAGE_STARTED;
        refresh.setVisible(!fetching);
        stop.setVisible(fetching);

        MenuItem forward = menu.findItem(R.id.forward_menu);
        forward.setVisible(webView.canGoForward());

        MenuItem desktopVersion = menu.findItem(R.id.desktop_version_menu);
        desktopVersion.setChecked(viewModel.isDesktopMode());

        MenuItem addBookmark = menu.findItem(R.id.add_bookmark_menu);
        MenuItem editBookmark = menu.findItem(R.id.edit_bookmark_menu);
        addBookmark.setVisible(!isCurrentPageBookmarked);
        editBookmark.setVisible(isCurrentPageBookmarked);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int itemId = item.getItemId();
        if (itemId == R.id.forward_menu) {
            if (webView.canGoForward())
                webView.goForward();
        } else if (itemId == R.id.stop_menu) {
            viewModel.stopLoading(webView);
        } else if (itemId == R.id.refresh_menu) {
            webView.reload();
        } else if (itemId == R.id.share_menu) {
            makeShareDialog(viewModel.url.get());
        } else if (itemId == R.id.settings_menu) {
            showSettings();
        } else if (itemId == R.id.desktop_version_menu) {
            item.setChecked(!item.isChecked());
            viewModel.enableDesktopMode(webView, item.isChecked());
            webView.reload();
        } else if (itemId == R.id.bookmarks_menu) {
            showBookmarks();
        } else if (itemId == R.id.add_bookmark_menu) {
            addBookmark();
        } else if (itemId == R.id.edit_bookmark_menu) {
            disposables.add(viewModel.getBookmarkForCurrentPage()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::showEditBookmarkDialog,
                            (e) -> Log.e(TAG, Log.getStackTraceString(e)))
            );
        } else if (itemId == R.id.close_menu) {
            finish();
        }

        return true;
    }

    private void makeShareDialog(String url)
    {
        if (url == null)
            return;

        startActivity(Intent.createChooser(
                Utils.makeShareUrlIntent(url),
                getString(R.string.share_via)));
    }

    private void showCopyToClipboardDialog(String url)
    {
        if (url == null)
            return;

        Intent i = new Intent(this, SendTextToClipboard.class);
        i.putExtra(Intent.EXTRA_TEXT, url);
        startActivityForResult(i, REQUEST_CODE_COPY_TO_CLIPBOARD);
    }

    private void showSettings()
    {
        Intent i = new Intent(this, SettingsActivity.class);
        i.putExtra(SettingsActivity.TAG_OPEN_PREFERENCE, SettingsActivity.BrowserSettings);
        startActivityForResult(i, REQUEST_CODE_SETTINGS);
    }

    private void showBookmarks()
    {
        startActivityForResult(
                new Intent(this, BrowserBookmarksActivity.class),
                REQUEST_CODE_BOOKMARKS);
    }

    private void addBookmark()
    {
        disposables.add(viewModel.addBookmark()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((bookmark) -> {
                            showBookmarkAddedSnackbar(bookmark);
                            isCurrentPageBookmarked = bookmark.url.equals(viewModel.url.get());
                        },
                        this::showAddBookmarkFailedSnackbar)
        );
    }

    private void showEditBookmarkDialog(BrowserBookmark bookmark)
    {
        if (bookmark == null)
            return;

        Intent i = new Intent(this, EditBookmarkActivity.class);
        i.putExtra(EditBookmarkActivity.TAG_BOOKMARK, bookmark);
        startActivityForResult(i, REQUEST_CODE_EDIT_BOOKMARK);
    }

    private void showBookmarkAddedSnackbar(BrowserBookmark bookmark)
    {
        Snackbar.make(coordinatorLayout,
                R.string.browser_bookmark_added,
                Snackbar.LENGTH_SHORT)
                .setAction(R.string.browser_bookmark_edit_menu,
                        (v) -> showEditBookmarkDialog(bookmark))
                .show();
    }

    private void showAddBookmarkFailedSnackbar(Throwable e)
    {
        Log.e(TAG, Log.getStackTraceString(e));

        Snackbar.make(coordinatorLayout,
                R.string.browser_bookmark_add_failed,
                Snackbar.LENGTH_SHORT)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_CODE_SETTINGS:
                /*
                 * Lazy applying settings
                 * TODO: consider other options for applying settings
                 */
                recreate();
                break;
            case REQUEST_CODE_EDIT_BOOKMARK:
                handleEditBookmarkRequest(resultCode, data);
                break;
            case REQUEST_CODE_BOOKMARKS:
                handleBookmarksRequest(data);
                break;
        }
    }

    private void handleEditBookmarkRequest(int resultCode, @Nullable Intent data)
    {
        if (resultCode != RESULT_OK || data == null)
            return;

        String action = data.getAction();
        if (action == null)
            return;

        BrowserBookmark bookmark = data.getParcelableExtra(EditBookmarkActivity.TAG_BOOKMARK);

        String message = null;
        switch (action) {
            case EditBookmarkActivity.TAG_RESULT_ACTION_DELETE_BOOKMARK:
                message = getResources().getQuantityString(R.plurals.browser_bookmark_deleted, 1);
                if (bookmark != null && bookmark.url.equals(viewModel.url.get()))
                    isCurrentPageBookmarked = false;
                break;
            case EditBookmarkActivity.TAG_RESULT_ACTION_DELETE_BOOKMARK_FAILED:
                message = getResources().getQuantityString(R.plurals.browser_bookmark_delete_failed, 1);
                break;
            case EditBookmarkActivity.TAG_RESULT_ACTION_APPLY_CHANGES_FAILED:
                message = getString(R.string.browser_bookmark_change_failed);
                break;
            case EditBookmarkActivity.TAG_RESULT_ACTION_APPLY_CHANGES:
                isCurrentPageBookmarked = bookmark != null &&
                        bookmark.url.equals(viewModel.url.get());
                break;
        }
        if (message != null)
            Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_SHORT).show();
    }

    private void handleBookmarksRequest(@Nullable Intent data)
    {
        String action = (data == null ? null : data.getAction());
        if (BrowserBookmarksActivity.TAG_ACTION_OPEN_BOOKMARK.equals(action)) {
            BrowserBookmark bookmark = data.getParcelableExtra(BrowserBookmarksActivity.TAG_BOOKMARK);
            if (bookmark == null)
                return;
            viewModel.url.set(bookmark.url);
            viewModel.loadUrl(webView);
        } else {
            checkIsCurrentPageBookmarked();
        }
    }

    @Override
    public void onBackPressed()
    {
        if (webView.canGoBack()) {
            doubleBackPressed = false;
            webView.goBack();
        } else {
            if (doubleBackPressed) {
                doubleBackPressed = false;
                super.onBackPressed();
            } else {
                doubleBackPressed = true;
                Toast.makeText(this,
                        R.string.browser_back_pressed,
                        Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }
}
