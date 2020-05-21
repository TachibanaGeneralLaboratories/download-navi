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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.tachibana.downloader.R;
import com.tachibana.downloader.databinding.BrowserContextMenuDialogBinding;
import com.tachibana.downloader.ui.FragmentCallback;

public class BrowserContextMenuDialog extends BottomSheetDialogFragment
{
    @SuppressWarnings("unused")
    private static final String TAG = BrowserContextMenuDialog.class.getSimpleName();

    public static final String TAG_URL = "url";
    public static final String TAG_ACTION_SHARE = "action_share";
    public static final String TAG_ACTION_DOWNLOAD = "action_download";
    public static final String TAG_ACTION_COPY = "action_copy";

    private AppCompatActivity activity;
    private BrowserContextMenuDialogBinding binding;

    public static BrowserContextMenuDialog newInstance(@NonNull String url)
    {
        BrowserContextMenuDialog frag = new BrowserContextMenuDialog();

        Bundle args = new Bundle();
        args.putString(TAG_URL, url);
        frag.setArguments(args);

        return frag;
    }

    @Override
    public void onAttach(@NonNull Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState)
    {
        binding = DataBindingUtil.inflate(inflater, R.layout.browser_context_menu_dialog, container, true);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        /* Make full height */
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout()
            {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                BottomSheetDialog dialog = (BottomSheetDialog)getDialog();
                FrameLayout bottomSheet = dialog.findViewById(R.id.design_bottom_sheet);
                BottomSheetBehavior behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setPeekHeight(0);
            }
        });

        String url = getArguments().getString(TAG_URL);
        if (url != null)
            binding.title.setText(url);

        binding.share.setOnClickListener(onItemClickListener);
        binding.downloadFromLink.setOnClickListener(onItemClickListener);
        binding.copyLink.setOnClickListener(onItemClickListener);
    }

    private final View.OnClickListener onItemClickListener = (v) -> {
        Intent i = new Intent();
        i.putExtra(TAG_URL, getArguments().getString(TAG_URL));

        switch (v.getId()) {
            case R.id.share:
                i.setAction(TAG_ACTION_SHARE);
                break;
            case R.id.download_from_link:
                i.setAction(TAG_ACTION_DOWNLOAD);
                break;
            case R.id.copy_link:
                i.setAction(TAG_ACTION_COPY);
                break;
        }

        finish(i, FragmentCallback.ResultCode.OK);
    };

    private void finish(Intent intent, FragmentCallback.ResultCode code)
    {
        dismiss();
        ((FragmentCallback)activity).fragmentFinished(intent, code);
    }
}
