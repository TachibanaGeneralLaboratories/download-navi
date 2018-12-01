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

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * The class encapsulates information about download.
 */

public class DownloadInfo implements Parcelable, Comparable<DownloadInfo>
{
    /*
     * Lists the states that the download task can set on a download
     * to notify application of the download progress.
     * Status codes are compatible with the system DownloadManager.
     * The codes follow the HTTP families:
     * 1xx: informational
     * 2xx: success
     * 3xx: redirects (not used)
     * 4xx: client errors
     * 5xx: server errors
     */
    /* This download hasn't started yet */
    public static final int STATUS_PENDING = 190;
    /* This download has started */
    public static final int STATUS_RUNNING = 192;
    /* This download encountered some network error and is waiting before retrying the request */
    public static final int STATUS_WAITING_TO_RETRY = 194;
    /* This download is waiting for network connectivity to proceed */
    public static final int STATUS_WAITING_FOR_NETWORK = 195;
    public static final int STATUS_PAUSED = 197;
    /*
     * This download couldn't be completed due to insufficient storage
     * space. Typically, this is because the SD card is full
     */
    public static final int STATUS_INSUFFICIENT_SPACE_ERROR = 198;
    /*
     * This download has successfully completed.
     * Warning: there might be other status values that indicate success
     * in the future.
     * Use isStatusSuccess() to capture the entire category
     */
    public static final int STATUS_SUCCESS = 200;
    /*
     * This request couldn't be parsed. This is also used when processing
     * requests with unknown/unsupported URI schemes
     */
    public static final int STATUS_BAD_REQUEST = 400;
    /* Some possibly transient error occurred, but we can't resume the download */
    public static final int STATUS_CANNOT_RESUME = 489;
    /* This download was cancelled */
    public static final int STATUS_CANCELLED = 490;
    /*
     * This download has completed with an error.
     * Warning: there will be other status values that indicate errors in
     * the future. Use isStatusError() to capture the entire category
     */
    public static final int STATUS_UNKNOWN_ERROR = 491;
    /*
     * This download couldn't be completed because of a storage issue.
     * Typically, that's because the filesystem is missing or full
     */
    public static final int STATUS_FILE_ERROR = 492;
    /*
     * This download couldn't be completed because of an HTTP
     * redirect response that the download manager couldn't
     * handle
     */
    public static final int STATUS_UNHANDLED_REDIRECT = 493;
    /*
     * This download couldn't be completed because of an
     * unspecified unhandled HTTP code.
     */
    public static final int STATUS_UNHANDLED_HTTP_CODE = 494;
    /*
     * This download couldn't be completed because of an
     * error receiving or processing data at the HTTP level
     */
    public static final int STATUS_HTTP_DATA_ERROR = 495;
    /*
     * This download couldn't be completed because there were
     * too many redirects.
     */
    public static final int STATUS_TOO_MANY_REDIRECTS = 497;

    /* Piece number can't be less or equal zero */
    public static final int MIN_PIECES = 1;

    private UUID id;
    /* SAF storage */
    private Uri filePath;
    private String url;
    private String fileName;
    private String description;
    private String mimeType;
    private long totalBytes = -1;
    private int numPieces = MIN_PIECES;
    private HashMap<String, String> requestHeaders = new HashMap<>();
    private int statusCode = STATUS_PENDING;
    private boolean wifiOnly = false;
    private boolean retry = true;
    /* Indicates that server support partial download */
    private boolean partialSupport = true;
    private String statusMsg;

    public DownloadInfo(Uri filePath, String url,
                        String fileName, String mimeType)
    {
        this(UUID.nameUUIDFromBytes(filePath.toString().getBytes()),
                filePath, url, fileName, mimeType);
    }

    public DownloadInfo(UUID id, Uri filePath, String url,
                        String fileName, String mimeType)
    {
        this.id = id;
        this.filePath = filePath;
        this.url = url;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.numPieces = numPieces;
    }

    public DownloadInfo(Parcel source)
    {
        id = (UUID)source.readSerializable();
        filePath = source.readParcelable(Uri.class.getClassLoader());
        url = source.readString();
        fileName = source.readString();
        description = source.readString();
        mimeType = source.readString();
        totalBytes = source.readLong();
        requestHeaders = (HashMap<String, String>)source.readSerializable();
        statusCode = source.readInt();
        wifiOnly = source.readByte() > 0;
        numPieces = source.readInt();
        retry = source.readByte() > 0;
        statusMsg = source.readString();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeSerializable(id);
        dest.writeParcelable(filePath, flags);
        dest.writeString(url);
        dest.writeString(fileName);
        dest.writeString(description);
        dest.writeString(mimeType);
        dest.writeLong(totalBytes);
        dest.writeSerializable(requestHeaders);
        dest.writeInt(statusCode);
        dest.writeByte((byte)(wifiOnly ? 1 : 0));
        dest.writeInt(numPieces);
        dest.writeByte((byte)(retry ? 1 : 0));
        dest.writeString(statusMsg);
    }

    public static final Parcelable.Creator<DownloadInfo> CREATOR =
            new Parcelable.Creator<DownloadInfo>()
            {
                @Override
                public DownloadInfo createFromParcel(Parcel source)
                {
                    return new DownloadInfo(source);
                }

                @Override
                public DownloadInfo[] newArray(int size)
                {
                    return new DownloadInfo[size];
                }
            };

    public void setHeaders(Map<String, String> headers)
    {
        requestHeaders = new HashMap<>(headers);
    }

    public Map<String, String> getHeaders()
    {
        return requestHeaders;
    }

    public UUID getId()
    {
        return id;
    }

    public Uri getFilePath()
    {
        return filePath;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String name)
    {
        this.fileName = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public void setMimeType(String mimeType)
    {
        this.mimeType = mimeType;
    }

    public String getMimeType()
    {
        return mimeType;
    }

    public long getTotalBytes()
    {
        return totalBytes;
    }

    public void setTotalBytes(long bytes)
    {
        this.totalBytes = bytes;
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    public void setWiFiOnly(boolean wifiOnly)
    {
        this.wifiOnly = wifiOnly;
    }

    public boolean isWifiOnly()
    {
        return wifiOnly;
    }

    public boolean isRetry()
    {
        return retry;
    }

    public void setRetry(boolean retry)
    {
        this.retry = retry;
    }

    public boolean isPartialSupport()
    {
        return partialSupport;
    }

    public void setPartialSupport(boolean partialSupport)
    {
        this.partialSupport = partialSupport;
    }

    public void setNumPieces(int numPieces)
    {
        if (numPieces <= 0)
            throw new IllegalArgumentException("Piece number can't be less or equal zero");

        if (!partialSupport && numPieces > 1)
            throw new IllegalStateException("The download doesn't support partial download");

        this.numPieces = numPieces;
    }

    public List<DownloadPiece> makePieces()
    {
        List<DownloadPiece> pieces = new ArrayList<>();
        long piecesSize = -1;
        long lastPieceSize = -1;
        if (totalBytes != -1) {
            piecesSize = totalBytes / numPieces;
            lastPieceSize = piecesSize + totalBytes % numPieces;
        }

        long curBytes = 0;
        for (int i = 0; i < numPieces; i++) {
            long pieceSize = (i == numPieces - 1 ? lastPieceSize : piecesSize);
            pieces.add(new DownloadPiece(getId(), i, pieceSize, curBytes));
            curBytes += pieceSize;
        }

        return pieces;
    }

    public long pieceStartPos(DownloadPiece piece)
    {
        if (piece == null || totalBytes == -1)
            return 0;

        long pieceSize = (int)(totalBytes / numPieces);

        return piece.getIndex() * pieceSize;
    }

    public long pieceEndPos(DownloadPiece piece)
    {
        if (piece == null)
            return 0;

        return (piece.getSize() < 0 ?
                -1 :
                pieceStartPos(piece) + piece.getSize() - 1);
    }

    public int getNumPieces()
    {
        return numPieces;
    }

    public String getStatusMsg()
    {
        return statusMsg;
    }

    public void setStatusMsg(String statusMsg)
    {
        this.statusMsg = statusMsg;
    }

    public void setStatusCode(int statusCode)
    {
        this.statusCode = statusCode;
    }

    /*
     * Returns whether the status is informational (i.e. 1xx)
     */

    public boolean isStatusInformational()
    {
        return statusCode >= 100 && statusCode < 200;
    }

    /*
     * Returns whether the status is a success (i.e. 2xx)
     */

    public boolean isStatusSuccess()
    {
        return statusCode >= 200 && statusCode < 300;
    }

    /*
     * Returns whether the status is an error (i.e. 4xx or 5xx).
     */

    public boolean isStatusError()
    {
        return statusCode >= 400 && statusCode < 600;
    }

    /*
     * Returns whether the status is a client error (i.e. 4xx)
     */

    public boolean isStatusClientError()
    {
        return statusCode >= 400 && statusCode < 500;
    }

    /*
     * Returns whether the status is a server error (i.e. 5xx)
     */

    public boolean isStatusServerError()
    {
        return statusCode >= 500 && statusCode < 600;
    }

    /*
     * Returns whether the download has completed (either with success or
     * error)
     */

    public boolean isStatusCompleted()
    {
        return statusCode >= 200 && statusCode < 300 || statusCode >= 400 && statusCode < 600;
    }

    @Override
    public int compareTo(@NonNull DownloadInfo another)
    {
        return fileName.compareTo(another.getFileName());
    }

    @Override
    public int hashCode()
    {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof DownloadInfo && (o == this || id.equals(((DownloadInfo)o).id));
    }

    @Override
    public String toString()
    {
        return "DownloadInfo{" +
                "id=" + id +
                ", filePath=" + filePath +
                ", url='" + url + '\'' +
                ", fileName='" + fileName + '\'' +
                ", description='" + description + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", totalBytes=" + totalBytes +
                ", numPieces=" + numPieces +
                ", requestHeaders=" + requestHeaders +
                ", statusCode=" + statusCode +
                ", wifiOnly=" + wifiOnly +
                ", retry=" + retry +
                ", partialSupport=" + partialSupport +
                ", statusMsg='" + statusMsg + '\'' +
                '}';
    }
}
