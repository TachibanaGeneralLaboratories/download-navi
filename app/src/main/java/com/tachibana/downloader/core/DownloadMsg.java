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

package com.tachibana.downloader.core;

import android.os.Bundle;
import android.os.Message;

import java.util.UUID;

/*
 * Provides message with information about the download.
 */

public class DownloadMsg
{
    public static final int MSG_PROGRESS_CHANGED = 1;
    public static final int MSG_FINISHED = 2;
    public static final int MSG_PAUSED = 3;
    public static final int MSG_CANCELLED = 4;
    public static final String TAG_DOWNLOAD_ID = "download_id";
    public static final String TAG_DOWNLOAD_BYTES = "download_bytes";
    public static final String TAG_SPEED = "speed";

    public static Message makeProgressChangedMsg(UUID id, long downloadBytes, long speed)
    {
        Message msg = Message.obtain(null, MSG_PROGRESS_CHANGED, null);

        Bundle b = new Bundle();
        b.putSerializable(TAG_DOWNLOAD_ID, id);
        b.putLong(TAG_DOWNLOAD_BYTES, downloadBytes);
        b.putLong(TAG_SPEED, speed);

        msg.setData(b);

        return msg;
    }

    public static Message makeFinishedMsg(UUID id)
    {
        Message msg = Message.obtain(null, MSG_FINISHED, null);

        Bundle b = new Bundle();
        b.putSerializable(TAG_DOWNLOAD_ID, id);

        msg.setData(b);

        return msg;
    }

    public static Message makePausedMsg(UUID id)
    {
        Message msg = Message.obtain(null, MSG_PAUSED, null);

        Bundle b = new Bundle();
        b.putSerializable(TAG_DOWNLOAD_ID, id);

        msg.setData(b);

        return msg;
    }

    public static Message makeCancelledMsg(UUID id)
    {
        Message msg = Message.obtain(null, MSG_CANCELLED, null);

        Bundle b = new Bundle();
        b.putSerializable(TAG_DOWNLOAD_ID, id);

        msg.setData(b);

        return msg;
    }
}
