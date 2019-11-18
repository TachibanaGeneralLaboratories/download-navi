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

package com.tachibana.downloader.core.system;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;

public class SystemFacadeImpl implements SystemFacade
{
    private Context context;

    public SystemFacadeImpl(@NonNull Context context)
    {
        this.context = context;
    }

    @Override
    public NetworkInfo getActiveNetworkInfo()
    {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        return cm.getActiveNetworkInfo();
    }

    @TargetApi(23)
    @Override
    public NetworkCapabilities getNetworkCapabilities()
    {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null)
            return null;

        return cm.getNetworkCapabilities(network);
    }

    @Override
    public boolean isActiveNetworkMetered()
    {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        return cm.isActiveNetworkMetered();
    }

    @Override
    public String getSystemUserAgent()
    {
        try {
            return WebSettings.getDefaultUserAgent(context);

        } catch (UnsupportedOperationException e) {
            /* Fallback JVM user agent if WebView doesn't supported */
            return System.getProperty("http.agent");
        }
    }
}
