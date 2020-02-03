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

import androidx.annotation.NonNull;

import java.io.FileDescriptor;
import java.io.IOException;

public class FakeSysCall implements SysCall
{
    private long availableBytes = -1;

    @Override
    public void lseek(@NonNull FileDescriptor fd, long offset) { }

    @Override
    public void fallocate(@NonNull FileDescriptor fd, long length) { }

    @Override
    public long availableBytes(@NonNull FileDescriptor fd)
    {
        return availableBytes;
    }

    public void setAvailableBytes(long availableBytes)
    {
        this.availableBytes = availableBytes;
    }
}
