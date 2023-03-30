/*
 * Copyright (C) 2018-2021 Tachibana General Laboratories, LLC
 * Copyright (C) 2018-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.ui.adddownload;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.Observable;
import androidx.databinding.library.baseAdapters.BR;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.tachibana.downloader.R;
import com.tachibana.downloader.core.exception.FreeSpaceException;
import com.tachibana.downloader.core.exception.HttpException;
import com.tachibana.downloader.core.exception.NormalizeUrlException;
import com.tachibana.downloader.core.model.data.entity.UserAgent;
import com.tachibana.downloader.core.system.FileSystemContracts;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.databinding.DialogAddDownloadBinding;
import com.tachibana.downloader.ui.BaseAlertDialog;
import com.tachibana.downloader.ui.ClipboardDialog;
import com.tachibana.downloader.ui.FragmentCallback;
import com.tachibana.downloader.ui.PermissionDeniedDialog;
import com.tachibana.downloader.ui.PermissionManager;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class AddDownloadDialog extends DialogFragment {
    @SuppressWarnings("unused")
    private static final String TAG = AddDownloadDialog.class.getSimpleName();

    private static final String TAG_ADD_USER_AGENT_DIALOG = "add_user_agent_dialog";
    private static final String TAG_INIT_PARAMS = "init_params";
    private static final String TAG_CREATE_FILE_ERROR_DIALOG = "create_file_error_dialog";
    private static final String TAG_OPEN_DIR_ERROR_DIALOG = "open_dir_error_dialog";
    private static final String TAG_URL_CLIPBOARD_DIALOG = "url_clipboard_dialog";
    private static final String TAG_CHECKSUM_CLIPBOARD_DIALOG = "checksum_clipboard_dialog";
    private static final String TAG_REFERER_CLIPBOARD_DIALOG = "referer_clipboard_dialog";
    private static final String TAG_CUR_CLIPBOARD_TAG = "cur_clipboard_tag";
    private static final String TAG_PERM_DENIED_DIALOG = "perm_denied_dialog";

    private AlertDialog alert;
    private AppCompatActivity activity;
    private UserAgentAdapter userAgentAdapter;
    private AddDownloadViewModel viewModel;
    private BaseAlertDialog addUserAgentDialog;
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private DialogAddDownloadBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private ClipboardDialog clipboardDialog;
    private ClipboardDialog.SharedViewModel clipboardViewModel;
    private String curClipboardTag;
    private SharedPreferences localPref;
    private PermissionDeniedDialog permDeniedDialog;
    private PermissionManager permissionManager;

    public static AddDownloadDialog newInstance(@NonNull AddInitParams initParams)
    {
        AddDownloadDialog frag = new AddDownloadDialog();

        Bundle args = new Bundle();
        args.putParcelable(TAG_INIT_PARAMS, initParams);
        frag.setArguments(args);

        return frag;
    }

    @Override
    public void onAttach(@NonNull Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity) context;
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

        unsubscribeClipboardManager();
        unsubscribeParamsChanged();
        disposables.clear();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeParamsChanged();
        subscribeAlertDialog();
        subscribeClipboardManager();
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents().subscribe(this::handleAlertDialogEvent);
        disposables.add(d);
        d = clipboardViewModel.observeSelectedItem().subscribe((item) -> {
            switch (item.dialogTag) {
                case TAG_URL_CLIPBOARD_DIALOG:
                    handleUrlClipItem(item.str);
                    break;
                case TAG_CHECKSUM_CLIPBOARD_DIALOG:
                    handleChecksumClipItem(item.str);
                    break;
                case TAG_REFERER_CLIPBOARD_DIALOG:
                    handleRefererClipItem(item.str);
            }
        });
        disposables.add(d);
    }

    private void subscribeClipboardManager()
    {
        ClipboardManager clipboard = (ClipboardManager)activity.getSystemService(Activity.CLIPBOARD_SERVICE);
        clipboard.addPrimaryClipChangedListener(clipListener);
    }

    private void unsubscribeClipboardManager()
    {
        ClipboardManager clipboard = (ClipboardManager)activity.getSystemService(Activity.CLIPBOARD_SERVICE);
        clipboard.removePrimaryClipChangedListener(clipListener);
    }

    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = this::switchClipboardButton;

    private void switchClipboardButton()
    {
        ClipData clip = Utils.getClipData(activity.getApplicationContext());
        viewModel.showClipboardButton.set(clip != null);
    }

    private final ViewTreeObserver.OnWindowFocusChangeListener onFocusChanged =
            (__) -> switchClipboardButton();

    private void handleAlertDialogEvent(BaseAlertDialog.Event event)
    {
        if (event.dialogTag == null) {
            return;
        }
        if (event.dialogTag.equals(TAG_ADD_USER_AGENT_DIALOG) && addUserAgentDialog != null) {
            switch (event.type) {
                case POSITIVE_BUTTON_CLICKED:
                    Dialog dialog = addUserAgentDialog.getDialog();
                    if (dialog != null) {
                        TextInputEditText editText = dialog.findViewById(R.id.text_input_dialog);
                        Editable e = editText.getText();
                        String userAgent = (e == null ? null : e.toString());
                        if (!TextUtils.isEmpty(userAgent))
                            disposables.add(viewModel.addUserAgent(new UserAgent(userAgent))
                                    .subscribeOn(Schedulers.io())
                                    .subscribe());
                    }
                case NEGATIVE_BUTTON_CLICKED:
                    addUserAgentDialog.dismiss();
                    break;
            }
        } else if (event.dialogTag.equals(TAG_PERM_DENIED_DIALOG)) {
            if (event.type != BaseAlertDialog.EventType.DIALOG_SHOWN) {
                permDeniedDialog.dismiss();
            }
            if (event.type == BaseAlertDialog.EventType.NEGATIVE_BUTTON_CLICKED) {
                permissionManager.requestPermissions();
            }
        }
    }

    private void handleUrlClipItem(String item)
    {
        if (TextUtils.isEmpty(item))
            return;

        viewModel.params.setUrl(item);
        doAutoFetch();
    }

    private void handleChecksumClipItem(String item)
    {
        if (TextUtils.isEmpty(item))
            return;

        viewModel.params.setChecksum(item);
    }

    private void handleRefererClipItem(String item)
    {
        if (TextUtils.isEmpty(item))
            return;

        viewModel.params.setReferer(item);
    }

    private void subscribeParamsChanged() {
        viewModel.params.addOnPropertyChangedCallback(onParamsChanged);
    }

    private void unsubscribeParamsChanged() {
        viewModel.params.removeOnPropertyChangedCallback(onParamsChanged);
    }

    private final Observable.OnPropertyChangedCallback onParamsChanged = new Observable.OnPropertyChangedCallback() {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId) {
            switch (propertyId) {
                case BR.retry:
                    localPref.edit()
                            .putBoolean(getString(R.string.add_download_retry_flag),
                                    viewModel.params.isRetry())
                            .apply();
                    break;
                case BR.replaceFile:
                    localPref.edit()
                            .putBoolean(getString(R.string.add_download_replace_file_flag),
                                    viewModel.params.isReplaceFile())
                            .apply();
                    break;
                case BR.unmeteredConnectionsOnly:
                    localPref.edit()
                            .putBoolean(getString(R.string.add_download_unmetered_only_flag),
                                    viewModel.params.isUnmeteredConnectionsOnly())
                            .apply();
                    break;
                case BR.numPieces:
                    localPref.edit()
                            .putInt(getString(R.string.add_download_num_pieces),
                                    viewModel.params.getNumPieces())
                            .apply();
                    break;
                case BR.uncompressArchive:
                    localPref.edit()
                            .putBoolean(getString(R.string.add_download_uncompress_archive_flag),
                                    viewModel.params.isUncompressArchive())
                            .apply();
                    break;
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        ViewModelProvider provider = new ViewModelProvider(activity);
        viewModel = provider.get(AddDownloadViewModel.class);
        dialogViewModel = provider.get(BaseAlertDialog.SharedViewModel.class);
        clipboardViewModel = provider.get(ClipboardDialog.SharedViewModel.class);
        localPref = PreferenceManager.getDefaultSharedPreferences(activity);

        AddInitParams initParams = getArguments().getParcelable(TAG_INIT_PARAMS);
        /* Clear init params */
        getArguments().putParcelable(TAG_INIT_PARAMS, null);
        if (initParams != null)
            viewModel.initParams(initParams);

        FragmentManager fm = getChildFragmentManager();
        permissionManager = new PermissionManager(activity, new PermissionManager.Callback() {
            @Override
            public void onStorageResult(boolean isGranted, boolean shouldRequestStoragePermission) {
                if (!isGranted && shouldRequestStoragePermission) {
                    if (fm.findFragmentByTag(TAG_PERM_DENIED_DIALOG) == null) {
                        permDeniedDialog = PermissionDeniedDialog.newInstance();
                        FragmentTransaction ft = fm.beginTransaction();
                        ft.add(permDeniedDialog, TAG_PERM_DENIED_DIALOG);
                        ft.commitAllowingStateLoss();
                    }
                }
            }

            @Override
            public void onNotificationResult(boolean isGranted, boolean shouldRequestNotificationPermission) {
                permissionManager.setDoNotAskNotifications(!isGranted);
            }
        });

        if (!permissionManager.checkPermissions() && permDeniedDialog == null) {
            permissionManager.requestPermissions();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        if (savedInstanceState != null)
            curClipboardTag = savedInstanceState.getString(TAG_CUR_CLIPBOARD_TAG);

        FragmentManager fm = getChildFragmentManager();
        addUserAgentDialog = (BaseAlertDialog)fm.findFragmentByTag(TAG_ADD_USER_AGENT_DIALOG);
        clipboardDialog = (ClipboardDialog)fm.findFragmentByTag(curClipboardTag);
        permDeniedDialog = (PermissionDeniedDialog)fm.findFragmentByTag(TAG_PERM_DENIED_DIALOG);

        LayoutInflater i = LayoutInflater.from(activity);
        binding = DataBindingUtil.inflate(i, R.layout.dialog_add_download, null, false);
        binding.setViewModel(viewModel);

        initLayoutView();

        binding.getRoot().getViewTreeObserver().addOnWindowFocusChangeListener(onFocusChanged);

        return alert;
    }

    @Override
    public void onDestroyView()
    {
        binding.getRoot().getViewTreeObserver().removeOnWindowFocusChangeListener(onFocusChanged);

        super.onDestroyView();
    }

    private void initLayoutView()
    {
        binding.expansionHeader.setOnClickListener((View view) -> {
            binding.advancedLayout.toggle();
            binding.expansionHeader.toggleExpand();
        });

        binding.piecesNumberSelect.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                /* Increment because progress starts from zero */
                viewModel.params.setNumPieces(++progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { /* Nothing */}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { /* Nothing */}
        });
        binding.piecesNumberValue.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s)
            {
                if (TextUtils.isEmpty(s))
                    return;

                int numPieces;
                try {
                    numPieces = Integer.parseInt(s.toString());

                } catch (NumberFormatException e) {
                    binding.piecesNumberValue.setText(String.valueOf(viewModel.maxNumPieces));
                    return;
                }

                viewModel.params.setNumPieces(numPieces);
            }
        });
        binding.piecesNumberValue.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && TextUtils.isEmpty(binding.piecesNumberValue.getText()))
                binding.piecesNumberValue.setText(String.valueOf(viewModel.params.getNumPieces()));
        });
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
        binding.checksum.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s)
            {
                checkChecksumField(s);
            }
        });

        binding.folderChooserButton.setOnClickListener((v) ->
            chooseDir.launch(viewModel.params.getDirPath())
        );
        binding.urlClipboardButton.setOnClickListener((v) ->
                showClipboardDialog(TAG_URL_CLIPBOARD_DIALOG));
        binding.checksumClipboardButton.setOnClickListener((v) ->
                showClipboardDialog(TAG_CHECKSUM_CLIPBOARD_DIALOG));
        binding.refererClipboardButton.setOnClickListener((v) ->
                showClipboardDialog(TAG_REFERER_CLIPBOARD_DIALOG));

        userAgentAdapter = new UserAgentAdapter(activity, (userAgent) -> {
            disposables.add(viewModel.deleteUserAgent(userAgent)
                    .subscribeOn(Schedulers.io())
                    .subscribe());
        });
        viewModel.observeUserAgents().observe(this, (userAgentList) -> {
            int curUserAgentPos = -1;
            String curUserAgent = viewModel.params.getUserAgent();
            if (curUserAgent != null) {
                for (int i = 0; i < userAgentList.size(); i++) {
                    if (curUserAgent.equals(userAgentList.get(i).userAgent)) {
                        curUserAgentPos = i;
                        break;
                    }
                }
            }
            /* Add non-existent agent and make it read only */
            if (curUserAgentPos < 0 && curUserAgent != null) {
                UserAgent userAgent = new UserAgent(curUserAgent);
                userAgent.readOnly = true;
                userAgentList.add(userAgent);
                curUserAgentPos = userAgentList.size() - 1;
            }

            userAgentAdapter.clear();
            userAgentAdapter.addAll(userAgentList);
            binding.userAgent.setSelection(curUserAgentPos);
        });

        binding.userAgent.setAdapter(userAgentAdapter);
        binding.userAgent.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l)
            {
                Object item = binding.userAgent.getItemAtPosition(i);
                if (item != null) {
                    viewModel.params.setUserAgent(((UserAgent)item).userAgent);
                    viewModel.savePrefUserAgent(((UserAgent)item));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView)
            {
                /* Nothing */
            }
        });
        binding.addUserAgent.setOnClickListener((v) -> addUserAgentDialog());
        binding.piecesNumberSelect.setEnabled(false);
        binding.piecesNumberValue.setEnabled(false);

        switchClipboardButton();

        initAlertDialog(binding.getRoot());
    }

    private void initAlertDialog(View view)
    {
        alert = new AlertDialog.Builder(activity)
                .setTitle(R.string.add_download)
                .setPositiveButton(R.string.connect, null)
                .setNegativeButton(R.string.add, null)
                .setNeutralButton(R.string.cancel, null)
                .setView(view)
                .create();

        alert.setCanceledOnTouchOutside(false);
        alert.setOnShowListener((DialogInterface dialog) -> {
            viewModel.fetchState.observe(AddDownloadDialog.this, (state) -> {
                switch (state.status) {
                    case FETCHING:
                        onFetching();
                        break;
                    case ERROR:
                        onFetched();
                        showFetchError(state.error);
                        break;
                    case FETCHED:
                        onFetched();
                        break;
                    case UNKNOWN:
                        doAutoFetch();
                        break;
                }
            });

            Button connectButton = alert.getButton(AlertDialog.BUTTON_POSITIVE);
            Button addButton = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button cancelButton = alert.getButton(AlertDialog.BUTTON_NEUTRAL);
            connectButton.setOnClickListener((v) -> fetchLink());
            addButton.setOnClickListener((v) -> addDownload());
            cancelButton.setOnClickListener((v) ->
                    finish(new Intent(), FragmentCallback.ResultCode.CANCEL));
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        outState.putString(TAG_CUR_CLIPBOARD_TAG, curClipboardTag);

        super.onSaveInstanceState(outState);
    }

    private void addUserAgentDialog()
    {
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_ADD_USER_AGENT_DIALOG) == null) {
            addUserAgentDialog = BaseAlertDialog.newInstance(
                    getString(R.string.dialog_add_user_agent_title),
                    null,
                    R.layout.dialog_text_input,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    false);

            addUserAgentDialog.show(fm, TAG_ADD_USER_AGENT_DIALOG);
        }
    }

    private void onFetching()
    {
        hideFetchError();
        binding.link.setEnabled(false);
        binding.afterFetchLayout.setVisibility(View.GONE);
        binding.fetchProgress.show();
    }

    private void onFetched()
    {
        binding.link.setEnabled(true);
        binding.fetchProgress.hide();
        binding.afterFetchLayout.setVisibility(View.VISIBLE);
        binding.partialSupportWarning.setVisibility((viewModel.params.isPartialSupport() ? View.GONE : View.VISIBLE));
        binding.piecesNumberValue.setEnabled(viewModel.params.isPartialSupport() && viewModel.params.getTotalBytes() > 0);
        binding.piecesNumberSelect.setEnabled(viewModel.params.isPartialSupport() && viewModel.params.getTotalBytes() > 0);
    }

    private void hideFetchError()
    {
        binding.layoutLink.setErrorEnabled(false);
        binding.layoutLink.setError(null);
    }

    private boolean checkUrlField()
    {
        if (TextUtils.isEmpty(viewModel.params.getUrl())) {
            binding.layoutLink.setErrorEnabled(true);
            binding.layoutLink.setError(getString(R.string.download_error_empty_link));
            binding.layoutLink.requestFocus();

            return false;
        }

        binding.layoutLink.setErrorEnabled(false);
        binding.layoutLink.setError(null);

        return true;
    }

    private boolean checkNameField()
    {
        String name = viewModel.params.getFileName();
        if (TextUtils.isEmpty(name)) {
            binding.layoutName.setErrorEnabled(true);
            binding.layoutName.setError(getString(R.string.download_error_empty_name));
            binding.layoutName.requestFocus();

            return false;
        }
        if (!viewModel.fs.isValidFatFilename(name)) {
            binding.layoutName.setErrorEnabled(true);
            binding.layoutName.setError(getString(R.string.download_error_name_is_not_correct,
                    viewModel.fs.buildValidFatFilename(name)));
            binding.layoutName.requestFocus();

            return false;
        }

        binding.layoutName.setErrorEnabled(false);
        binding.layoutName.setError(null);

        return true;
    }

    private void checkChecksumField(Editable s)
    {
        if (!TextUtils.isEmpty(s) && !viewModel.isChecksumValid(s.toString())) {
            binding.layoutChecksum.setErrorEnabled(true);
            binding.layoutChecksum.setError(getString(R.string.error_invalid_checksum));
            binding.layoutChecksum.requestFocus();

            return;
        }

        binding.layoutChecksum.setErrorEnabled(false);
        binding.layoutChecksum.setError(null);
    }

    private void showFetchError(Throwable e)
    {
        if (e == null) {
            hideFetchError();
            return;
        }

        String errorStr;

        if (e instanceof MalformedURLException) {
            errorStr = getString(R.string.fetch_error_invalid_url, e.getMessage());

        } else if (e instanceof ConnectException) {
            errorStr = getString(R.string.fetch_error_default_fmt,
                    getString(R.string.fetch_error_network_disconnected));

        } else if (e instanceof HttpException) {
            HttpException httpErr = (HttpException)e;
            if (httpErr.getResponseCode() > 0)
                errorStr = getString(R.string.fetch_error_http_response, httpErr.getResponseCode());
            else
                errorStr = getString(R.string.fetch_error_default_fmt, httpErr.getMessage());

        } else if (e instanceof IOException) {
            errorStr = getString(R.string.fetch_error_io, e.getMessage());

        } else {
            errorStr = getString(R.string.fetch_error_default_fmt, e.getMessage());
        }

        binding.link.setEnabled(true);
        binding.layoutLink.setErrorEnabled(true);
        binding.layoutLink.setError(errorStr);
        binding.layoutLink.requestFocus();
    }

    private void doAutoFetch()
    {
        if (!TextUtils.isEmpty(viewModel.params.getUrl()) && viewModel.pref.autoConnect())
            fetchLink();
    }

    private void fetchLink()
    {
        if (!checkUrlField())
            return;

        viewModel.startFetchTask();
    }

    private void addDownload()
    {
        if (!checkUrlField())
            return;
        if (!checkNameField())
            return;

        try {
            viewModel.addDownload();

        } catch (IOException e) {
            showCreateFileErrorDialog();
            return;
        } catch (FreeSpaceException e) {
            showFreeSpaceErrorToast();
            return;
        } catch (NormalizeUrlException e) {
            showInvalidUrlDialog();
            return;
        }

        Toast.makeText(activity.getApplicationContext(),
                getString(R.string.download_ticker_notify, viewModel.params.getFileName()),
                Toast.LENGTH_SHORT)
                .show();

        finish(new Intent(), FragmentCallback.ResultCode.OK);
    }

    private void showClipboardDialog(String tag)
    {
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(tag) == null) {
            curClipboardTag = tag;
            clipboardDialog = ClipboardDialog.newInstance();
            clipboardDialog.show(fm, tag);
        }
    }

    final ActivityResultLauncher<Uri> chooseDir = registerForActivityResult(
            new FileSystemContracts.OpenDirectory(),
            uri -> {
                if (uri == null) {
                    return;
                }
                try {
                    viewModel.fs.takePermissions(uri);
                    viewModel.params.setDirPath(uri);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to open directory: " + Log.getStackTraceString(e));
                    showOpenDirErrorDialog();
                }
            }
    );

    private void showCreateFileErrorDialog()
    {
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_CREATE_FILE_ERROR_DIALOG) == null) {
            BaseAlertDialog createFileErrorDialog = BaseAlertDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.unable_to_create_file),
                    0,
                    getString(R.string.ok),
                    null,
                    null,
                    true);

            createFileErrorDialog.show(fm, TAG_CREATE_FILE_ERROR_DIALOG);
        }
    }

    private void showOpenDirErrorDialog()
    {
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_OPEN_DIR_ERROR_DIALOG) == null) {
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

    private void showInvalidUrlDialog()
    {
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_CREATE_FILE_ERROR_DIALOG) == null) {
            BaseAlertDialog invalidUrlDialog = BaseAlertDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.add_download_error_invalid_url),
                    0,
                    getString(R.string.ok),
                    null,
                    null,
                    true);

            invalidUrlDialog.show(fm, TAG_CREATE_FILE_ERROR_DIALOG);
        }
    }

    private void showFreeSpaceErrorToast()
    {
        String totalSizeStr = Formatter.formatFileSize(activity, viewModel.params.getTotalBytes());
        String availSizeStr = Formatter.formatFileSize(activity, viewModel.params.getStorageFreeSpace());

        Toast.makeText(activity.getApplicationContext(),
                activity.getString(R.string.download_error_no_enough_free_space, availSizeStr, totalSizeStr),
                Toast.LENGTH_LONG)
                .show();
    }

    public void onBackPressed()
    {
        finish(new Intent(), FragmentCallback.ResultCode.BACK);
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code)
    {
        viewModel.finish();

        alert.dismiss();
        ((FragmentCallback)activity).fragmentFinished(intent, code);
    }
}
