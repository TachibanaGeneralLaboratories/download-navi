package com.tachibana.downloader.viewmodel.filemanager;

import android.content.Context;

import com.tachibana.downloader.dialog.filemanager.FileManagerConfig;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class FileManagerViewModelFactory extends ViewModelProvider.NewInstanceFactory
{
    private final Context context;
    private final FileManagerConfig config;

    public FileManagerViewModelFactory(@NonNull Context context, FileManagerConfig config)
    {
        this.context = context;
        this.config = config;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass)
    {
        if (modelClass == FileManagerViewModel.class)
            return (T)new FileManagerViewModel(context, config);

        return null;
    }
}
