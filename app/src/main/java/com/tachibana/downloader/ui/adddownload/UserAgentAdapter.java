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

package com.tachibana.downloader.ui.adddownload;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.model.data.entity.UserAgent;

public class UserAgentAdapter extends ArrayAdapter<UserAgent>
{
    private final DeleteListener deleteListener;

    public UserAgentAdapter(@NonNull Context context, DeleteListener deleteListener)
    {
        super(context, R.layout.spinner_user_agent_item);

        this.deleteListener = deleteListener;
    }

    @Override
    public View getDropDownView(int position, View view, ViewGroup parent)
    {
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.spinner_user_agent_item, parent, false);
        }

        UserAgent userAgent = getItem(position);
        if (userAgent != null) {
            TextView textView = view.findViewById(android.R.id.text1);
            textView.setText(userAgent.userAgent);
        }

        /* Ignore read only system agents (e.g system user agent) and null */
        ImageView deleteButton = view.findViewById(R.id.delete);
        if (userAgent != null && !userAgent.readOnly)
            deleteButton.setOnClickListener((View v) -> {
                if (deleteListener != null)
                    deleteListener.onDelete(userAgent);
            });
        else
            deleteButton.setVisibility(View.GONE);

        return view;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent)
    {
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.spinner_user_agent_view, parent, false);
        }

        UserAgent userAgent = getItem(position);
        if (userAgent != null) {
            TextView textView = view.findViewById(android.R.id.text1);
            textView.setText(userAgent.userAgent);
        }

        return view;
    }

    public interface DeleteListener
    {
        void onDelete(UserAgent userAgent);
    }
}
