/*
 * Copyright (C) 2018 Tachibana General Laboratories, LLC
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.storage.UserAgentStorage;

import androidx.annotation.NonNull;

public class UserAgentAdapter extends ArrayAdapter<String>
{
    private UserAgentStorage storage;

    public UserAgentAdapter(@NonNull Context context)
    {
        super(context, R.layout.spinner_user_agent_item);

        storage = new UserAgentStorage(context);
        /* System user agent is always first */
        add(new WebView(context).getSettings().getUserAgentString());
        addAll(storage.getAll());
    }

    public void removeAgent(String userAgent)
    {
        remove(userAgent);

        if (userAgent != null)
            storage.delete(userAgent);
    }

    public int addAgent(String userAgent)
    {
        add(userAgent);

        if (userAgent != null)
            storage.add(userAgent);

        return getPosition(userAgent);
    }

    @Override
    public View getDropDownView(int position, View view, ViewGroup parent)
    {
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.spinner_user_agent_item, parent, false);
        }

        TextView textView = view.findViewById(android.R.id.text1);
        textView.setText(getItem(position));

        /* Ignore system user agent */
        ImageView deleteButton = view.findViewById(R.id.delete);
        if (position != 0)
            deleteButton.setOnClickListener((View v) -> removeAgent(getItem(position)));
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

        TextView textView = view.findViewById(android.R.id.text1);
        textView.setText(getItem(position));

        return view;
    }
}
