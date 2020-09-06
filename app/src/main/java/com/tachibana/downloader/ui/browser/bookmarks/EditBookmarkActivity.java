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

package com.tachibana.downloader.ui.browser.bookmarks;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.tachibana.downloader.core.model.data.entity.BrowserBookmark;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.ui.FragmentCallback;

public class EditBookmarkActivity extends AppCompatActivity
        implements FragmentCallback
{
    public static final String TAG_BOOKMARK = "bookmark";
    public static final String TAG_RESULT_ACTION_APPLY_CHANGES = "result_action_apply_changes";
    public static final String TAG_RESULT_ACTION_APPLY_CHANGES_FAILED = "result_action_apply_changes_failed";
    public static final String TAG_RESULT_ACTION_DELETE_BOOKMARK = "result_action_delete_bookmark";
    public static final String TAG_RESULT_ACTION_DELETE_BOOKMARK_FAILED = "result_action_delete_bookmark_failed";
    private static final String TAG_EDIT_BOOKMARK_DIALOG = "edit_bookmark_dialog";

    private EditBookmarkDialog editBookmarkDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        setTheme(Utils.getTranslucentAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        FragmentManager fm = getSupportFragmentManager();
        editBookmarkDialog = (EditBookmarkDialog)fm.findFragmentByTag(TAG_EDIT_BOOKMARK_DIALOG);
        if (editBookmarkDialog == null) {
            Intent i = getIntent();
            if (i == null)
                throw new NullPointerException("intent is null");
            BrowserBookmark bookmark = i.getParcelableExtra(TAG_BOOKMARK);
            if (bookmark == null)
                throw new NullPointerException("bookmark is null");
            editBookmarkDialog = EditBookmarkDialog.newInstance(bookmark);
            editBookmarkDialog.show(fm, TAG_EDIT_BOOKMARK_DIALOG);
        }
    }

    @Override
    public void fragmentFinished(Intent intent, ResultCode code)
    {
        int resultCode = (intent.getAction() == null || code != ResultCode.OK ?
                RESULT_CANCELED :
                RESULT_OK);

        setResult(resultCode, intent);
        finish();
    }

    @Override
    public void onBackPressed()
    {
        editBookmarkDialog.onBackPressed();
    }
}
