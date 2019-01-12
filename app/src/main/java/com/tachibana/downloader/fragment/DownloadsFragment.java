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

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.tachibana.downloader.R;
import com.tachibana.downloader.adapter.DownloadListAdapter;
import com.tachibana.downloader.core.entity.InfoAndPieces;
import com.tachibana.downloader.databinding.FragmentDownloadListBinding;
import com.tachibana.downloader.viewmodel.DownloadsViewModel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;

/*
 * A base fragment for individual fragment with sorted content (queued and completed downloads)
 */

public abstract class DownloadsFragment extends Fragment
    implements DownloadListAdapter.ClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = FinishedDownloadsFragment.class.getSimpleName();

    private static final String TAG_DOWNLOADS_LIST_STATE = "downloads_list_state";

    protected AppCompatActivity activity;
    protected DownloadListAdapter adapter;
    protected LinearLayoutManager layoutManager;
    /* Save state scrolling */
    private Parcelable downloadsListState;
    protected FragmentDownloadListBinding binding;
    protected DownloadsViewModel viewModel;
    protected CompositeDisposable disposable = new CompositeDisposable();

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
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        viewModel = ViewModelProviders.of(this).get(DownloadsViewModel.class);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (downloadsListState != null)
            layoutManager.onRestoreInstanceState(downloadsListState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null)
            downloadsListState = savedInstanceState.getParcelable(TAG_DOWNLOADS_LIST_STATE);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        downloadsListState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_DOWNLOADS_LIST_STATE, downloadsListState);

        super.onSaveInstanceState(outState);
    }

    protected void subscribeAdapter(Predicate<InfoAndPieces> filter)
    {
        disposable.add(viewModel.observerAllInfoAndPieces()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((infoAndPiecesList) ->
                        Flowable.fromIterable(infoAndPiecesList)
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
    public abstract void onItemClicked(@NonNull InfoAndPieces item);

    @Override
    public void onItemLongClicked(@NonNull InfoAndPieces item)
    {

    }
}
