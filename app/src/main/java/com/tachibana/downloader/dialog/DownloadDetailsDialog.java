/*
 * Copyright (C) 2018, 2019 Tachibana General Laboratories, LLC
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.tachibana.downloader.AddDownloadActivity;
import com.tachibana.downloader.FragmentCallback;
import com.tachibana.downloader.R;
import com.tachibana.downloader.RequestPermissions;
import com.tachibana.downloader.adapter.UserAgentAdapter;
import com.tachibana.downloader.core.entity.DownloadInfo;
import com.tachibana.downloader.core.entity.DownloadPiece;
import com.tachibana.downloader.core.entity.UserAgent;
import com.tachibana.downloader.core.exception.FileAlreadyExistsException;
import com.tachibana.downloader.core.exception.FreeSpaceException;
import com.tachibana.downloader.core.exception.HttpException;
import com.tachibana.downloader.core.utils.FileUtils;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.databinding.DialogAddDownloadBinding;
import com.tachibana.downloader.databinding.DialogDownloadDetailsBinding;
import com.tachibana.downloader.dialog.filemanager.FileManagerConfig;
import com.tachibana.downloader.dialog.filemanager.FileManagerDialog;
import com.tachibana.downloader.settings.SettingsManager;
import com.tachibana.downloader.viewmodel.AddDownloadViewModel;
import com.tachibana.downloader.viewmodel.AddInitParams;
import com.tachibana.downloader.viewmodel.DownloadDetailsViewModel;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class DownloadDetailsDialog extends DialogFragment
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadDetailsDialog.class.getSimpleName();

    private static final int CHOOSE_PATH_TO_SAVE_REQUEST_CODE = 1;
    private static final String TAG_OPEN_DIR_ERROR_DIALOG = "open_dir_error_dialog";
    private static final String TAG_REPLACE_FILE_DIALOG = "replace_file_dialog";
    private static final String TAG_ID = "id";

    private AlertDialog alert;
    private AppCompatActivity activity;
    private DialogDownloadDetailsBinding binding;
    private DownloadDetailsViewModel viewModel;
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private CompositeDisposable disposables = new CompositeDisposable();

    public static DownloadDetailsDialog newInstance(UUID downloadId)
    {
        DownloadDetailsDialog frag = new DownloadDetailsDialog();

        Bundle args = new Bundle();
        args.putSerializable(TAG_ID, downloadId);
        frag.setArguments(args);

        return frag;
    }

    @Override
    public void onAttach(@NonNull Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        /* Back button handle */
        getDialog().setOnKeyListener((DialogInterface dialog, int keyCode, KeyEvent event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return true;
                } else {
                    onBackPressed();
                    return true;
                }
            } else {
                return false;
            }
        });
    }

    @Override
    public void onStop()
    {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        observeDownload();
        subscribeAlertDialog();
    }

    private void observeDownload()
    {
        UUID id = (UUID)getArguments().getSerializable(TAG_ID);

        disposables.add(viewModel.observeInfoAndPieces(id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((infoAndPieces) -> {
                            if (infoAndPieces == null) {
                                finish();
                                return;
                            }
                            viewModel.updateInfo(infoAndPieces);
                        },
                        (Throwable t) -> {
                            Log.e(TAG, "Getting info " + id + " error: " +
                                    Log.getStackTraceString(t));
                        }));
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents().subscribe(this::handleAlertDialogEvent);
        disposables.add(d);
    }

    private void handleAlertDialogEvent(BaseAlertDialog.Event event)
    {
        if (!event.dialogTag.equals(TAG_REPLACE_FILE_DIALOG))
            return;
        switch (event.type) {
            case POSITIVE_BUTTON_CLICKED:
                applyChangedParams(true);
                break;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        viewModel = ViewModelProviders.of(this).get(DownloadDetailsViewModel.class);
        dialogViewModel = ViewModelProviders.of(activity).get(BaseAlertDialog.SharedViewModel.class);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        LayoutInflater i = LayoutInflater.from(activity);
        binding = DataBindingUtil.inflate(i, R.layout.dialog_download_details, null, false);
        binding.setViewModel(viewModel);

        initLayoutView();

        return alert;
    }

    private void initLayoutView()
    {
        binding.link.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s)
            {
                binding.layoutLink.setErrorEnabled(false);
                binding.layoutLink.setError(null);
            }
        });
        binding.name.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s)
            {
                binding.layoutName.setErrorEnabled(false);
                binding.layoutName.setError(null);
            }
        });

        binding.folderChooserButton.setOnClickListener((v) -> showChooseDirDialog());

        initAlertDialog(binding.getRoot());
    }

    private void initAlertDialog(View view)
    {
        alert = new AlertDialog.Builder(activity)
                .setTitle(R.string.download_details)
                .setPositiveButton(R.string.close, null)
                .setNegativeButton(R.string.apply, null)
                .setNeutralButton(R.string.redownload, null)
                .setView(view)
                .create();

        alert.setOnShowListener((DialogInterface dialog) -> {
            Button closeButton = alert.getButton(AlertDialog.BUTTON_POSITIVE);
            Button applyButton = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button redownloadButton = alert.getButton(AlertDialog.BUTTON_NEUTRAL);

            viewModel.paramsChanged.observe(this, (changed) -> {
                if (changed)
                    applyButton.setVisibility(View.VISIBLE);
                else
                    applyButton.setVisibility(View.GONE);
            });

            closeButton.setOnClickListener((v) -> finish());
            applyButton.setOnClickListener((v) -> applyChangedParams(false));
            redownloadButton.setOnClickListener((v) -> showAddDownloadDialog());
        });
    }

    private void showAddDownloadDialog()
    {
        DownloadInfo downloadInfo = viewModel.info.getDownloadInfo();
        if (downloadInfo == null)
            return;

        AddInitParams initParams = new AddInitParams();
        initParams.url = downloadInfo.url;
        initParams.dirPath = downloadInfo.dirPath;
        initParams.fileName = downloadInfo.fileName;
        initParams.description = downloadInfo.description;
        initParams.userAgent = downloadInfo.userAgent;
        initParams.wifiOnly = downloadInfo.wifiOnly;
        initParams.retry = downloadInfo.retry;
        initParams.replaceFile = true;

        Intent i = new Intent(activity, AddDownloadActivity.class);
        i.putExtra(AddDownloadActivity.TAG_INIT_PARAMS, initParams);
        startActivity(i);

        finish();
    }

    private void applyChangedParams(boolean checkFileExists)
    {
        if (!checkUrlField(binding.link.getText()))
            return;
        if (!checkNameField(binding.name.getText()))
            return;

        try {
            if (!viewModel.applyChangedParams(checkFileExists))
                return;

        } catch (FreeSpaceException e) {
            showFreeSpaceErrorToast();
            return;
        } catch (FileAlreadyExistsException e) {
            showReplaceFileDialog();
            return;
        }

        finish();
    }

    private boolean checkUrlField(Editable s)
    {
        if (s == null)
            return false;

        if (TextUtils.isEmpty(s)) {
            binding.layoutLink.setErrorEnabled(true);
            binding.layoutLink.setError(getString(R.string.download_error_empty_link));
            binding.layoutLink.requestFocus();

            return false;
        }

        binding.layoutLink.setErrorEnabled(false);
        binding.layoutLink.setError(null);

        return true;
    }

    private boolean checkNameField(Editable s)
    {
        if (s == null)
            return false;

        if (TextUtils.isEmpty(s)) {
            binding.layoutName.setErrorEnabled(true);
            binding.layoutName.setError(getString(R.string.download_error_empty_name));
            binding.layoutName.requestFocus();

            return false;
        }
        if (!FileUtils.isValidFatFilename(s.toString())) {
            String format = getString(R.string.download_error_name_is_not_correct);
            binding.layoutName.setErrorEnabled(true);
            binding.layoutName.setError(String.format(format,
                    FileUtils.buildValidFatFilename(s.toString())));
            binding.layoutName.requestFocus();

            return false;
        }

        binding.layoutName.setErrorEnabled(false);
        binding.layoutName.setError(null);

        return true;
    }

    private void showFreeSpaceErrorToast()
    {
        DownloadInfo downloadInfo = viewModel.info.getDownloadInfo();
        if (downloadInfo == null)
            return;

        String totalSizeStr = Formatter.formatFileSize(activity, downloadInfo.totalBytes);
        String availSizeStr = Formatter.formatFileSize(activity, viewModel.info.getStorageFreeSpace());
        String format = activity.getString(R.string.download_error_no_enough_free_space);

        Toast.makeText(activity.getApplicationContext(),
                String.format(format, availSizeStr, totalSizeStr),
                Toast.LENGTH_LONG)
                .show();
    }

    private void showChooseDirDialog()
    {
        Intent i = new Intent(activity, FileManagerDialog.class);

        String dirPath = null;
        Uri dirUri = viewModel.mutableParams.getDirPath();
        if (dirUri != null && FileUtils.isFileSystemPath(dirUri))
            dirPath = dirUri.getPath();

        FileManagerConfig config = new FileManagerConfig(dirPath,
                getString(R.string.select_folder_to_save),
                FileManagerConfig.DIR_CHOOSER_MODE);

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);
        startActivityForResult(i, CHOOSE_PATH_TO_SAVE_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (resultCode != CHOOSE_PATH_TO_SAVE_REQUEST_CODE && resultCode != Activity.RESULT_OK)
            return;

        if (data == null || data.getData() == null) {
            showOpenDirErrorDialog();
            return;
        }

        viewModel.mutableParams.setDirPath(data.getData());
    }

    private void showOpenDirErrorDialog()
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_OPEN_DIR_ERROR_DIALOG) == null) {
            BaseAlertDialog openDirErrorDialog = BaseAlertDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.unable_to_open_folder),
                    0,
                    getString(R.string.ok),
                    null,
                    null,
                    true);

            openDirErrorDialog.show(fm, TAG_OPEN_DIR_ERROR_DIALOG);
        }
    }

    private void showReplaceFileDialog()
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_REPLACE_FILE_DIALOG) == null) {
            BaseAlertDialog replaceFileDialog = BaseAlertDialog.newInstance(
                    getString(R.string.replace_file),
                    getString(R.string.error_file_exists),
                    0,
                    getString(R.string.yes),
                    getString(R.string.no),
                    null,
                    true);

            replaceFileDialog.show(fm, TAG_REPLACE_FILE_DIALOG);
        }
    }

    private void onBackPressed()
    {
        finish();
    }

    private void finish()
    {
        alert.dismiss();
    }
}
