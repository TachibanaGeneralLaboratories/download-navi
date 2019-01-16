package com.tachibana.downloader.adapter;

public interface Selectable<T>
{
    T getItemKey(int position);

    int getItemPosition(T key);
}
