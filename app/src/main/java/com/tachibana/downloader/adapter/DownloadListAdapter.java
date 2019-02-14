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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.StatusCode;
import com.tachibana.downloader.core.entity.DownloadPiece;
import com.tachibana.downloader.core.utils.DateUtils;
import com.tachibana.downloader.core.utils.MimeTypeUtils;
import com.tachibana.downloader.core.utils.Utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

public class DownloadListAdapter extends ListAdapter<DownloadItem, DownloadListAdapter.ViewHolder>
    implements Selectable<DownloadItem>
{
    private static final int VIEW_QUEUE = 0;
    private static final int VIEW_FINISH = 1;

    private ClickListener listener;
    private SelectionTracker<DownloadItem> selectionTracker;

    public DownloadListAdapter(ClickListener listener)
    {
        super(diffCallback);

        this.listener = listener;
    }

    public void setSelectionTracker(SelectionTracker<DownloadItem> selectionTracker)
    {
        this.selectionTracker = selectionTracker;
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
        DownloadItem item = getItem(position);

        return StatusCode.isStatusCompleted(item.info.statusCode) ? VIEW_FINISH : VIEW_QUEUE;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
        DownloadItem item = getItem(position);

        if (selectionTracker != null)
            holder.setSelected(selectionTracker.isSelected(item));

        if (holder instanceof QueueViewHolder) {
            QueueViewHolder queueHolder = (QueueViewHolder)holder;
            queueHolder.bind(item, (QueueClickListener)listener);
        } else if (holder instanceof FinishViewHolder) {
            FinishViewHolder finishHolder = (FinishViewHolder)holder;
            finishHolder.bind(item, (FinishClickListener)listener);
        }
    }

    @Override
    public DownloadItem getItemKey(int position)
    {
        return getItem(position);
    }

    @Override
    public int getItemPosition(DownloadItem key)
    {
        return getCurrentList().indexOf(key);
    }

    interface ViewHolderWithDetails
    {
        ItemDetails getItemDetails();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
        implements ViewHolderWithDetails
    {
        protected CardView cardView;
        protected TextView filename;
        protected TextView status;
        /* For selection support */
        private ItemDetails details = new ItemDetails();
        private boolean isSelected;

        ViewHolder(View itemView)
        {
            super(itemView);

            filename = itemView.findViewById(R.id.filename);
            status = itemView.findViewById(R.id.status);
        }

        void bind(DownloadItem item, ClickListener listener)
        {
            Context context = itemView.getContext();
            details.adapterPosition = getAdapterPosition();
            details.selectionKey = item;

            cardView = (CardView)itemView;
            if (isSelected)
                cardView.setCardBackgroundColor(Utils.getAttributeColor(context, R.attr.selectableColor));
            else
                cardView.setCardBackgroundColor(Utils.getAttributeColor(context, R.attr.foreground));

            cardView.setOnClickListener((v) -> {
                /* Skip selecting and deselecting */
                if (isSelected)
                    return;

                if (listener != null)
                    listener.onItemClicked(item);
            });

            filename.setText(item.info.fileName);
        }

        private void setSelected(boolean isSelected)
        {
            this.isSelected = isSelected;
        }

        @Override
        public ItemDetails getItemDetails()
        {
            return details;
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

        void bind(DownloadItem item, QueueClickListener listener)
        {
            super.bind(item, listener);

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
            long downloadedBytes = 0;
            long speed = 0;
            if (size > 0) {
                for (DownloadPiece piece : item.pieces) {
                    downloadedBytes += item.info.getDownloadedBytes(piece);
                    speed += piece.speed;
                }
                /* Average speed */
                speed /= size;
            }
            long ETA = Utils.calcETA(item.info.totalBytes, downloadedBytes, speed);

            if (item.info.statusCode == StatusCode.STATUS_RUNNING) {
                progressBar.setVisibility(View.VISIBLE);
                if (item.info.totalBytes > 0) {
                    int progress = (int)((downloadedBytes * 100) / item.info.totalBytes);
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(progress);
                } else {
                    progressBar.setIndeterminate(true);
                }
                status.setText(String.format(context.getString(R.string.download_queued_progress_template),
                        Formatter.formatFileSize(context, downloadedBytes),
                        (item.info.totalBytes == -1 ? context.getString(R.string.not_available) :
                                Formatter.formatFileSize(context, item.info.totalBytes)),
                        (ETA == -1 ? Utils.INFINITY_SYMBOL :
                                DateUtils.formatElapsedTime(context, ETA)),
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
                    case StatusCode.STATUS_FETCH_METADATA:
                        statusStr = context.getString(R.string.fetching_metadata);
                        break;
                }
                if (item.info.statusCode == StatusCode.STATUS_FETCH_METADATA) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setIndeterminate(true);
                } else {
                    progressBar.setVisibility(View.GONE);
                }

                status.setText(String.format(context.getString(R.string.download_queued_template),
                        Formatter.formatFileSize(context, downloadedBytes),
                        (item.info.totalBytes == -1 ? context.getString(R.string.not_available) :
                                Formatter.formatFileSize(context, item.info.totalBytes)),
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

        void bind(DownloadItem item, FinishClickListener listener)
        {
            super.bind(item, listener);

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
        void onItemClicked(@NonNull DownloadItem item);
    }

    public interface QueueClickListener extends ClickListener
    {
        void onItemPauseClicked(@NonNull DownloadItem item);

        void onItemCancelClicked(@NonNull DownloadItem item);
    }

    public interface FinishClickListener extends ClickListener
    {
        void onItemMenuClicked(int menuId, @NonNull DownloadItem item);
    }

    public static final DiffUtil.ItemCallback<DownloadItem> diffCallback = new DiffUtil.ItemCallback<DownloadItem>()
    {
        @Override
        public boolean areContentsTheSame(@NonNull DownloadItem oldItem,
                                          @NonNull DownloadItem newItem)
        {
            return oldItem.equalsContent(newItem);
        }

        @Override
        public boolean areItemsTheSame(@NonNull DownloadItem oldItem,
                                       @NonNull DownloadItem newItem)
        {
            return oldItem.equals(newItem);
        }
    };

    /*
     * Selection support stuff
     */

    public static final class KeyProvider extends ItemKeyProvider<DownloadItem>
    {
        Selectable<DownloadItem> selectable;

        public KeyProvider(Selectable<DownloadItem> selectable)
        {
            super(SCOPE_MAPPED);

            this.selectable = selectable;
        }

        @Nullable
        @Override
        public DownloadItem getKey(int position)
        {
            return selectable.getItemKey(position);
        }

        @Override
        public int getPosition(@NonNull DownloadItem key)
        {
            return selectable.getItemPosition(key);
        }
    }

    public static final class ItemDetails extends ItemDetailsLookup.ItemDetails<DownloadItem>
    {
        public DownloadItem selectionKey;
        public int adapterPosition;

        @Nullable
        @Override
        public DownloadItem getSelectionKey()
        {
            return selectionKey;
        }

        @Override
        public int getPosition()
        {
            return adapterPosition;
        }
    }

    public static class ItemLookup extends ItemDetailsLookup<DownloadItem>
    {
        private final RecyclerView recyclerView;

        public ItemLookup(RecyclerView recyclerView)
        {
            this.recyclerView = recyclerView;
        }

        @Nullable
        @Override
        public ItemDetails<DownloadItem> getItemDetails(@NonNull MotionEvent e)
        {
            View view = recyclerView.findChildViewUnder(e.getX(), e.getY());
            if (view != null) {
                RecyclerView.ViewHolder viewHolder = recyclerView.getChildViewHolder(view);
                if (viewHolder instanceof DownloadListAdapter.ViewHolder)
                    return ((ViewHolder)viewHolder).getItemDetails();
            }

            return null;
        }
    }
}
