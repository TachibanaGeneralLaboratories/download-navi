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

package com.tachibana.downloader.core.utils;

import android.content.Context;
import android.text.format.Formatter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;

import com.tachibana.downloader.R;

import java.text.SimpleDateFormat;
import java.util.Date;

public class BindingAdapterUtils
{
    @BindingAdapter(value = {"fileSize", "formatFileSize"}, requireAll = false)
    public static void formatFileSize(@NonNull TextView view,
                                      long fileSize,
                                      @Nullable String formatFileSize)
    {
        Context context = view.getContext();
        String sizeStr = getFileSize(context, fileSize);
        view.setText((formatFileSize == null ? sizeStr : String.format(formatFileSize, sizeStr)));
    }

    @BindingAdapter({"formatDate"})
    public static void formatDate(@NonNull TextView view, long date)
    {
        view.setText(SimpleDateFormat.getDateTimeInstance()
                .format(new Date(date)));
    }

    public static String getFileSize(@NonNull Context context,
                                     long fileSize)
    {
        return fileSize >= 0 ? Formatter.formatFileSize(context, fileSize) :
                context.getString(R.string.not_available);
    }

    public static int getProgress(long downloaded, long total)
    {
        return (total == 0 ? 0 : (int)((downloaded * 100) / total));
    }

    public static String formatProgress(@NonNull Context context,
                                        long downloaded,
                                        long total,
                                        @NonNull String fmt)
    {
        return String.format(fmt,
                getFileSize(context, downloaded),
                getFileSize(context, total),
                getProgress(downloaded, total));
    }
}
