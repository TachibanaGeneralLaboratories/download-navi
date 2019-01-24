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

package com.tachibana.downloader.fragment;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import com.tachibana.downloader.R;
import com.tachibana.downloader.adapter.DownloadItem;
import com.tachibana.downloader.adapter.DownloadListAdapter;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.databinding.FragmentDownloadListBinding;
import com.tachibana.downloader.dialog.BaseAlertDialog;
import com.tachibana.downloader.viewmodel.DownloadsViewModel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;

/*
 * A base fragment for individual fragment with sorted content (queued and completed downloads)
 */

public abstract class DownloadsFragment extends Fragment
    implements DownloadListAdapter.ClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadsFragment.class.getSimpleName();

    private static final String TAG_DOWNLOAD_LIST_STATE = "download_list_state";
    private static final String SELECTION_TRACKER_ID = "selection_tracker_0";
    private static final String TAG_DELETE_DOWNLOADS_DIALOG = "delete_downloads_dialog";

    protected AppCompatActivity activity;
    protected DownloadListAdapter adapter;
    protected LinearLayoutManager layoutManager;
    /* Save state scrolling */
    private Parcelable downloadListState;
    private SelectionTracker<DownloadItem> selectionTracker;
    private ActionMode actionMode;
    protected FragmentDownloadListBinding binding;
    protected DownloadsViewModel viewModel;
    protected CompositeDisposable disposable = new CompositeDisposable();
    private BaseAlertDialog deleteDownloadsDialog;
    private BaseAlertDialog.SharedViewModel dialogViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_download_list, container, false);

        adapter = new DownloadListAdapter(this);
        /*
         * A RecyclerView by default creates another copy of the ViewHolder in order to
         * fade the views into each other. This causes the problem because the old ViewHolder gets
         * the payload but then the new one doesn't. So needs to explicitly tell it to reuse the old one.
         */
        DefaultItemAnimator animator = new DefaultItemAnimator()
        {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder)
            {
                return true;
            }
        };
        layoutManager = new LinearLayoutManager(activity);
        binding.downloadList.setLayoutManager(layoutManager);
        binding.downloadList.setItemAnimator(animator);
        binding.downloadList.setEmptyView(binding.emptyViewDownloadList);
        binding.downloadList.setAdapter(adapter);

        selectionTracker = new SelectionTracker.Builder<>(
                SELECTION_TRACKER_ID,
                binding.downloadList,
                new DownloadListAdapter.KeyProvider(adapter),
                new DownloadListAdapter.ItemLookup(binding.downloadList),
                StorageStrategy.createParcelableStorage(DownloadItem.class))
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
                .build();

        selectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onSelectionChanged()
            {
                super.onSelectionChanged();

                if (selectionTracker.hasSelection() && actionMode == null) {
                    actionMode = activity.startSupportActionMode(actionModeCallback);
                    setActionModeTitle(selectionTracker.getSelection().size());

                } else if (!selectionTracker.hasSelection()) {
                    if (actionMode != null)
                        actionMode.finish();
                    actionMode = null;

                } else {
                    setActionModeTitle(selectionTracker.getSelection().size());
                }
            }

            @Override
            public void onSelectionRestored()
            {
                super.onSelectionRestored();

                actionMode = activity.startSupportActionMode(actionModeCallback);
                setActionModeTitle(selectionTracker.getSelection().size());
            }
        });

        if (savedInstanceState != null)
            selectionTracker.onRestoreInstanceState(savedInstanceState);
        adapter.setSelectionTracker(selectionTracker);

        return binding.getRoot();
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Override
    public void onStop()
    {
        super.onStop();

        disposable.clear();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAlertDialog();
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (deleteDownloadsDialog == null)
                        return;
                    switch (event) {
                        case POSITIVE_BUTTON_CLICKED:
                            Dialog dialog = deleteDownloadsDialog.getDialog();
                            if (dialog != null) {
                                CheckBox withFile = dialog.findViewById(R.id.delete_with_file);
                                deleteDownloads(withFile.isChecked());
                            }
                            if (actionMode != null)
                                actionMode.finish();
                        case NEGATIVE_BUTTON_CLICKED:
                            deleteDownloadsDialog.dismiss();
                            break;
                    }
                });
        disposable.add(d);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        viewModel = ViewModelProviders.of(this).get(DownloadsViewModel.class);

        FragmentManager fm = getFragmentManager();
        if (fm != null)
            deleteDownloadsDialog = (BaseAlertDialog)fm.findFragmentByTag(TAG_DELETE_DOWNLOADS_DIALOG);
        dialogViewModel = ViewModelProviders.of(activity).get(BaseAlertDialog.SharedViewModel.class);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (downloadListState != null)
            layoutManager.onRestoreInstanceState(downloadListState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null)
            downloadListState = savedInstanceState.getParcelable(TAG_DOWNLOAD_LIST_STATE);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        downloadListState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_DOWNLOAD_LIST_STATE, downloadListState);
        selectionTracker.onSaveInstanceState(outState);

        super.onSaveInstanceState(outState);
    }

    protected void subscribeAdapter(Predicate<DownloadItem> filter)
    {
        disposable.add(viewModel.observerAllInfoAndPieces()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((infoAndPiecesList) ->
                        Flowable.fromIterable(infoAndPiecesList)
                                .map(DownloadItem::new)
                                .filter(filter)
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::submitList,
                        (Throwable t) -> {
                            Log.e(TAG, "Getting info and pieces error: " +
                                    Log.getStackTraceString(t));
                        }));
    }

    @Override
    public abstract void onItemClicked(@NonNull DownloadItem item);

    private void setActionModeTitle(int itemCount)
    {
        actionMode.setTitle(String.valueOf(itemCount));
    }

    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback()
    {
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu)
        {
            return false;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu)
        {
            mode.getMenuInflater().inflate(R.menu.download_list_action_mode, menu);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item)
        {
            switch (item.getItemId()) {
                case R.id.delete_menu:
                    deleteDownloadsDialog();
                    break;
                case R.id.share_menu:
                    shareDownloads();
                    mode.finish();
                    break;
                case R.id.select_all_menu:
                    selectAllDownloads();
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

    private void deleteDownloadsDialog()
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_DELETE_DOWNLOADS_DIALOG) == null) {
            deleteDownloadsDialog = BaseAlertDialog.newInstance(
                    getString(R.string.deleting),
                    (selectionTracker.getSelection().size() > 1 ?
                            getString(R.string.delete_selected_downloads) :
                            getString(R.string.delete_selected_download)),
                    R.layout.dialog_delete_downloads,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null);

            deleteDownloadsDialog.show(fm, TAG_DELETE_DOWNLOADS_DIALOG);
        }
    }

    private void deleteDownloads(boolean withFile)
    {
        disposable.add(Observable.fromIterable(selectionTracker.getSelection())
                .map((selection -> selection.info))
                .toList()
                .observeOn(Schedulers.io())
                .flatMapCompletable((infoList) -> viewModel.deleteDownloads(infoList, withFile))
                .subscribe());
    }

    private void shareDownloads()
    {
        disposable.add(Observable.fromIterable(selectionTracker.getSelection())
                .toList()
                .subscribe((items) -> {
                    startActivity(Intent.createChooser(
                            Utils.makeFileShareIntent(items),
                            getString(R.string.share_via)));
                }));
    }

    private void selectAllDownloads()
    {
        int n = adapter.getItemCount();
        if (n > 0) {
//            selectionTracker.startRange(0);
//            selectionTracker.extendRange(adapter.getItemCount() - 1);
        }
    }
}
