/*
 * Copyright (C) 2020-2021 Tachibana General Laboratories, LLC
 * Copyright (C) 2020-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.ui.browser.bookmarks;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.tachibana.downloader.R;
import com.tachibana.downloader.core.model.data.entity.BrowserBookmark;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.databinding.ActivityBrowserBookmarksBinding;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class BrowserBookmarksActivity extends AppCompatActivity
    implements BrowserBookmarksAdapter.ClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = BrowserBookmarksActivity.class.getSimpleName();

    public static final String TAG_ACTION_OPEN_BOOKMARK = "action_open_bookmark";
    public static final String TAG_BOOKMARK = "bookmark";

    private static final String TAG_BOOKMARKS_LIST_STATE = "bookmarks_list_state";
    private static final String SELECTION_TRACKER_ID = "selection_tracker_0";

    private ActivityBrowserBookmarksBinding binding;
    private BrowserBookmarksViewModel viewModel;
    private LinearLayoutManager layoutManager;
    private BrowserBookmarksAdapter adapter;
    /* Save state scrolling */
    private Parcelable bookmarksListState;
    private ActionMode actionMode;
    private SelectionTracker<BrowserBookmarkItem> selectionTracker;
    private CompositeDisposable disposables = new CompositeDisposable();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        setTheme(Utils.getAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(BrowserBookmarksViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_browser_bookmarks);

        setSupportActionBar(binding.toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);

        adapter = new BrowserBookmarksAdapter(this);

        layoutManager = new LinearLayoutManager(this);
        binding.bookmarksList.setLayoutManager(layoutManager);
        binding.bookmarksList.setEmptyView(binding.emptyViewBookmarksList);
        binding.bookmarksList.setAdapter(adapter);

        selectionTracker = new SelectionTracker.Builder<>(
                SELECTION_TRACKER_ID,
                binding.bookmarksList,
                new BrowserBookmarksAdapter.KeyProvider(adapter),
                new BrowserBookmarksAdapter.ItemLookup(binding.bookmarksList),
                StorageStrategy.createParcelableStorage(BrowserBookmarkItem.class))
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
                .build();

        selectionTracker.addObserver(new SelectionTracker.SelectionObserver<BrowserBookmarkItem>() {
            @Override
            public void onSelectionChanged()
            {
                super.onSelectionChanged();

                if (selectionTracker.hasSelection() && actionMode == null) {
                    actionMode = startSupportActionMode(actionModeCallback);
                    setActionModeTitle(selectionTracker.getSelection().size());

                } else if (!selectionTracker.hasSelection()) {
                    if (actionMode != null)
                        actionMode.finish();
                    actionMode = null;

                } else {
                    setActionModeTitle(selectionTracker.getSelection().size());

                    /* Show/hide menu items after change selection */
                    int size = selectionTracker.getSelection().size();
                    if (size == 1 || size == 2)
                        actionMode.invalidate();
                }
            }

            @Override
            public void onSelectionRestored()
            {
                super.onSelectionRestored();

                actionMode = startSupportActionMode(actionModeCallback);
                setActionModeTitle(selectionTracker.getSelection().size());
            }
        });

        if (savedInstanceState != null)
            selectionTracker.onRestoreInstanceState(savedInstanceState);
        adapter.setSelectionTracker(selectionTracker);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);

        if (savedInstanceState != null)
            bookmarksListState = savedInstanceState.getParcelable(TAG_BOOKMARKS_LIST_STATE);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (bookmarksListState != null)
            layoutManager.onRestoreInstanceState(bookmarksListState);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        bookmarksListState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_BOOKMARKS_LIST_STATE, bookmarksListState);
        selectionTracker.onSaveInstanceState(outState);

        super.onSaveInstanceState(outState);
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

        observeBookmarks();
    }

    private void observeBookmarks()
    {
        disposables.add(viewModel.observeBookmarks()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((bookmarks) ->
                        Flowable.fromIterable(bookmarks)
                                .map(BrowserBookmarkItem::new)
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::submitList)
        );
    }

    @Override
    public void onItemClicked(@NonNull BrowserBookmarkItem item)
    {
        Intent i = new Intent(TAG_ACTION_OPEN_BOOKMARK);
        i.putExtra(TAG_BOOKMARK, item);
        setResult(RESULT_OK, i);

        finish();
    }

    @Override
    public void onItemMenuClicked(int menuId, @NonNull BrowserBookmarkItem item)
    {
        switch (menuId) {
            case R.id.edit_bookmark_menu:
                showEditDialog(item);
                break;
            case R.id.delete_bookmark_menu:
                delete(Collections.singletonList(item));
                break;
            case R.id.share_bookmark_menu:
                shareBookmark(item);
                break;
        }
    }

    private void setActionModeTitle(int itemCount)
    {
        actionMode.setTitle(String.valueOf(itemCount));
    }

    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback()
    {
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu)
        {
            MenuItem edit = menu.findItem(R.id.edit_bookmark_menu);
            boolean show = selectionTracker.getSelection().size() <= 1;
            edit.setVisible(show);

            return true;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu)
        {
            mode.getMenuInflater().inflate(R.menu.browser_bookmarks_action_mode, menu);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item)
        {
            switch (item.getItemId()) {
                case R.id.edit_bookmark_menu:
                    editSelectedBookmark();
                    mode.finish();
                    break;
                case R.id.delete_bookmark_menu:
                    deleteSelectedBookmarks();
                    mode.finish();
                    break;
                case R.id.share_bookmark_menu:
                    shareSelectedBookmarks();
                    mode.finish();
                    break;
                case R.id.select_all_menu:
                    selectAllBookmarks();
                    break;
            }

            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode)
        {
            selectionTracker.clearSelection();
        }
    };

    private void editSelectedBookmark()
    {
        MutableSelection<BrowserBookmarkItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        Iterator<BrowserBookmarkItem> it = selections.iterator();
        if (!it.hasNext())
            return;

        showEditDialog(it.next());
    }

    private void showEditDialog(BrowserBookmark bookmark)
    {
        Intent i = new Intent(this, EditBookmarkActivity.class);
        i.putExtra(EditBookmarkActivity.TAG_BOOKMARK, bookmark);
        editBookmark.launch(i);
    }

    private void deleteSelectedBookmarks()
    {
        MutableSelection<BrowserBookmarkItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((bookmark) -> (BrowserBookmark)bookmark)
                .toList()
                .subscribe(this::delete));
    }

    private void delete(List<BrowserBookmark> bookmarks)
    {
        disposables.add(viewModel.deleteBookmarks(bookmarks)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((__) -> onBookmarksDeleted(bookmarks),
                        (e) -> onBookmarksDeleteFailed(bookmarks, e))
        );
    }

    private void onBookmarksDeleted(List<BrowserBookmark> bookmarks)
    {
        String message = getResources().getQuantityString(
                R.plurals.browser_bookmark_deleted, bookmarks.size());
        Snackbar.make(binding.coordinatorLayout,
                message,
                Snackbar.LENGTH_SHORT)
                .show();
    }

    private void onBookmarksDeleteFailed(List<BrowserBookmark> bookmarks, Throwable e)
    {
        Log.e(TAG, Log.getStackTraceString(e));

        String message = getResources().getQuantityString(
                R.plurals.browser_bookmark_delete_failed, bookmarks.size());
        Snackbar.make(binding.coordinatorLayout,
                message,
                Snackbar.LENGTH_SHORT)
                .show();
    }

    private void shareBookmark(BrowserBookmark bookmark)
    {
        startActivity(Intent.createChooser(
                Utils.makeShareUrlIntent(bookmark.url),
                getString(R.string.share_via)));
    }

    private void shareSelectedBookmarks()
    {
        MutableSelection<BrowserBookmarkItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((item) -> item.url)
                .toList()
                .subscribe((urlList) -> {
                    startActivity(Intent.createChooser(
                            Utils.makeShareUrlIntent(urlList),
                            getString(R.string.share_via)));
                }));
    }

    @SuppressLint("RestrictedApi")
    private void selectAllBookmarks()
    {
        int n = adapter.getItemCount();
        if (n > 0) {
            selectionTracker.startRange(0);
            selectionTracker.extendRange(adapter.getItemCount() - 1);
        }
    }

    final ActivityResultLauncher<Intent> editBookmark = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    String action = data.getAction();
                    if (action == null)
                        return;

                    String message = null;
                    switch (action) {
                        case EditBookmarkActivity.TAG_RESULT_ACTION_DELETE_BOOKMARK:
                            message = getResources().getQuantityString(R.plurals.browser_bookmark_deleted, 1);
                            break;
                        case EditBookmarkActivity.TAG_RESULT_ACTION_DELETE_BOOKMARK_FAILED:
                            message = getResources().getQuantityString(R.plurals.browser_bookmark_delete_failed, 1);
                            break;
                        case EditBookmarkActivity.TAG_RESULT_ACTION_APPLY_CHANGES_FAILED:
                            message = getString(R.string.browser_bookmark_change_failed);
                            break;
                    }
                    if (message != null)
                        Snackbar.make(binding.coordinatorLayout, message, Snackbar.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }

        return true;
    }

    @Override
    public void onBackPressed()
    {
        finish();
    }
}
