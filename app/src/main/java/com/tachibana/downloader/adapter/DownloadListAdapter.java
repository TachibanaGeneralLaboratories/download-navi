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

package com.tachibana.downloader.adapter;

import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.StatusCode;
import com.tachibana.downloader.core.entity.DownloadPiece;
import com.tachibana.downloader.core.entity.InfoAndPieces;
import com.tachibana.downloader.core.utils.DateFormatUtils;
import com.tachibana.downloader.core.utils.MimeTypeUtils;
import com.tachibana.downloader.core.utils.Utils;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

public class DownloadListAdapter extends ListAdapter<InfoAndPieces, DownloadListAdapter.ViewHolder>
{
    private static final int VIEW_QUEUE = 0;
    private static final int VIEW_FINISH = 1;

    private ClickListener listener;

    public DownloadListAdapter(ClickListener listener)
    {
        super(diffCallback);

        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        if (viewType == VIEW_QUEUE)
            return new QueueViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download_list_queue, parent, false));
        else
            return new FinishViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download_list_finish, parent, false));
    }

    @Override
    public int getItemViewType(int position)
    {
        InfoAndPieces item = getItem(position);

        return StatusCode.isStatusCompleted(item.info.statusCode) ? VIEW_FINISH : VIEW_QUEUE;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
        InfoAndPieces item = getItem(position);

        if (holder instanceof QueueViewHolder) {
            QueueViewHolder queueHolder = (QueueViewHolder)holder;
            queueHolder.bindTo(item, (QueueClickListener)listener);
        } else if (holder instanceof FinishViewHolder) {
            FinishViewHolder finishHolder = (FinishViewHolder)holder;
            finishHolder.bindTo(item, (FinishClickListener)listener);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
    {
        protected TextView filename;
        protected TextView status;

        ViewHolder(View itemView)
        {
            super(itemView);

            filename = itemView.findViewById(R.id.filename);
            status = itemView.findViewById(R.id.status);
        }

        void bindTo(InfoAndPieces item, ClickListener listener)
        {
            itemView.setOnClickListener((v) -> {
                if (listener != null)
                    listener.onItemClicked(item);
            });
            itemView.setOnLongClickListener((v) -> {
                if (listener != null) {
                    listener.onItemLongClicked(item);
                    return true;
                }
                return false;
            });

            filename.setText(item.info.fileName);
        }
    }

    public static class QueueViewHolder extends ViewHolder
    {
        private ImageButton pauseButton;
        private AnimatedVectorDrawableCompat playToPauseAnim;
        private AnimatedVectorDrawableCompat pauseToPlayAnim;
        private AnimatedVectorDrawableCompat currAnim;
        private ProgressBar progressBar;
        private ImageButton cancelButton;

        QueueViewHolder(View itemView)
        {
            super(itemView);

            playToPauseAnim = AnimatedVectorDrawableCompat.create(itemView.getContext(), R.drawable.play_to_pause);
            pauseToPlayAnim = AnimatedVectorDrawableCompat.create(itemView.getContext(), R.drawable.pause_to_play);
            pauseButton = itemView.findViewById(R.id.pause);
            progressBar = itemView.findViewById(R.id.progress);
            Utils.colorizeProgressBar(itemView.getContext(), progressBar);
            cancelButton = itemView.findViewById(R.id.cancel);
        }

        void bindTo(InfoAndPieces item, QueueClickListener listener)
        {
            super.bindTo(item, listener);

            if (item.info.partialSupport) {
                pauseButton.setVisibility(View.VISIBLE);
                setPauseButtonState(item.info.statusCode == StatusCode.STATUS_PAUSED);
                pauseButton.setOnClickListener((v) -> {
                    if (listener != null)
                        listener.onItemPauseClicked(item);
                });
            } else {
                pauseButton.setVisibility(View.GONE);
            }
            cancelButton.setOnClickListener((v) -> {
                if (listener != null)
                    listener.onItemCancelClicked(item);
            });

            Context context = itemView.getContext();
            int size = item.pieces.size();
            long downloadBytes = 0;
            long speed = 0;
            if (size > 0) {
                for (DownloadPiece piece : item.pieces) {
                    downloadBytes += piece.curBytes - item.info.pieceStartPos(piece);
                    speed += piece.speed;
                }
                /* Average speed */
                speed /= size;
            }
            long ETA = Utils.calcETA(item.info.totalBytes, downloadBytes, speed);

            if (item.info.statusCode == StatusCode.STATUS_RUNNING) {
                if (item.info.totalBytes > 0) {
                    int progress = (int)((downloadBytes * 100) / item.info.totalBytes);
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(progress);
                } else {
                    progressBar.setIndeterminate(true);
                }
                status.setText(String.format(context.getString(R.string.download_queued_progress_template),
                        Formatter.formatFileSize(context, downloadBytes),
                        (item.info.totalBytes == -1 ? context.getString(R.string.not_available) :
                                Formatter.formatFileSize(context, item.info.totalBytes)),
                        (ETA == -1 ? Utils.INFINITY_SYMBOL :
                                DateFormatUtils.formatElapsedTime(context, ETA)),
                        Formatter.formatFileSize(context, speed)));
            } else {
                String statusStr = "";
                switch (item.info.statusCode) {
                    case StatusCode.STATUS_PAUSED:
                        statusStr = context.getString(R.string.pause);
                        break;
                    case StatusCode.STATUS_PENDING:
                    case StatusCode.STATUS_WAITING_FOR_NETWORK:
                    case StatusCode.STATUS_WAITING_TO_RETRY:
                        statusStr = context.getString(R.string.pending);
                        break;
                }
                status.setText(String.format(context.getString(R.string.download_queued_template),
                        Formatter.formatFileSize(context, downloadBytes),
                        Formatter.formatFileSize(context, item.info.totalBytes),
                        statusStr));
            }
        }

        void setPauseButtonState(boolean isPause)
        {
            AnimatedVectorDrawableCompat prevAnim = currAnim;
            currAnim = (isPause ? pauseToPlayAnim : playToPauseAnim);
            pauseButton.setImageDrawable(currAnim);
            if (currAnim != prevAnim)
                currAnim.start();
        }
    }

    public static class FinishViewHolder extends ViewHolder
    {
        private ImageView icon;
        private ImageButton menu;
        private TextView error;

        FinishViewHolder(View itemView)
        {
            super(itemView);

            icon = itemView.findViewById(R.id.icon);
            menu = itemView.findViewById(R.id.menu);
            error = itemView.findViewById(R.id.error);
        }

        void bindTo(InfoAndPieces item, FinishClickListener listener)
        {
            super.bindTo(item, listener);

            Context context = itemView.getContext();

            menu.setOnClickListener((v) -> {
                PopupMenu popup = new PopupMenu(v.getContext(), v);
                popup.inflate(R.menu.download_item_popup);
                popup.setOnMenuItemClickListener((MenuItem menuItem) -> {
                    if (listener != null)
                        listener.onItemMenuClicked(menuItem.getItemId(), item);
                    return true;
                });
                popup.show();
            });

           int resId;
            switch (MimeTypeUtils.getCategory(item.info.mimeType)) {
                case DOCUMENT:
                    resId = R.drawable.ic_file_document_grey600_24dp;
                    break;
                case IMAGE:
                    resId = R.drawable.ic_image_grey600_24dp;
                    break;
                case VIDEO:
                    resId = R.drawable.ic_video_grey600_24dp;
                    break;
                case APK:
                    resId = R.drawable.ic_android_grey600_24dp;
                    break;
                case AUDIO:
                    resId = R.drawable.ic_music_note_grey600_24dp;
                    break;
                case ARCHIVE:
                    resId = R.drawable.ic_zip_box_grey600_24dp;
                    break;
                default:
                    resId = R.drawable.ic_file_grey600_24dp;
                    break;
            }
            icon.setImageDrawable(ContextCompat.getDrawable(context, resId));

            String hostname = Utils.getHostFromUrl(item.info.url);
            status.setText(String.format(context.getString(R.string.download_finished_template),
                    (hostname == null ? "" : hostname),
                    Formatter.formatFileSize(context, item.info.totalBytes)));

            if (StatusCode.isStatusError(item.info.statusCode) && item.info.statusMsg != null) {
                error.setVisibility(View.VISIBLE);
                error.setText(String.format(context.getString(R.string.error_template), item.info.statusMsg));
            } else {
                error.setVisibility(View.GONE);
            }
        }
    }

    public interface ClickListener
    {
        void onItemClicked(@NonNull InfoAndPieces item);

        void onItemLongClicked(@NonNull InfoAndPieces item);
    }

    public interface QueueClickListener extends ClickListener
    {
        void onItemPauseClicked(@NonNull InfoAndPieces item);

        void onItemCancelClicked(@NonNull InfoAndPieces item);
    }

    public interface FinishClickListener extends ClickListener
    {
        void onItemMenuClicked(int menuId, @NonNull InfoAndPieces item);
    }

    public static final DiffUtil.ItemCallback<InfoAndPieces> diffCallback = new DiffUtil.ItemCallback<InfoAndPieces>()
    {
        @Override
        public boolean areContentsTheSame(@NonNull InfoAndPieces oldItem,
                                          @NonNull InfoAndPieces newItem)
        {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areItemsTheSame(@NonNull InfoAndPieces oldItem,
                                       @NonNull InfoAndPieces newItem)
        {
            return oldItem.info.id.equals(newItem.info.id);
        }
    };

}
