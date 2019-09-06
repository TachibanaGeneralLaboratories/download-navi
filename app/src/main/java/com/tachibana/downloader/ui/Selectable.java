package com.tachibana.downloader.ui;

public interface Selectable<T>
{
    T getItemKey(int position);

    int getItemPosition(T key);
}
