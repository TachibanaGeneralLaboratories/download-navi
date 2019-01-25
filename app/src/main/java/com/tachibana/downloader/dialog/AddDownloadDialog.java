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

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import com.github.aakira.expandablelayout.ExpandableLayoutListener;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tachibana.downloader.FragmentCallback;
import com.tachibana.downloader.R;
import com.tachibana.downloader.RequestPermissions;
import com.tachibana.downloader.adapter.UserAgentAdapter;
import com.tachibana.downloader.core.entity.UserAgent;
import com.tachibana.downloader.core.exception.HttpException;
import com.tachibana.downloader.core.utils.FileUtils;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.databinding.DialogAddDownloadBinding;
import com.tachibana.downloader.dialog.filemanager.FileManagerConfig;
import com.tachibana.downloader.dialog.filemanager.FileManagerDialog;
import com.tachibana.downloader.viewmodel.AddDownloadViewModel;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class AddDownloadDialog extends DialogFragment
{
    @SuppressWarnings("unused")
    private static final String TAG = AddDownloadDialog.class.getSimpleName();

    private static final String TAG_ADD_USER_AGENT_DIALOG = "add_user_agent_dialog";
    private static final int CREATE_FILE_REQUEST_CODE = 1;
    private static final int CHOOSE_PATH_TO_SAVE_REQUEST_CODE = 2;
    private static final String TAG_USER_AGENT_SPINNER_POS = "user_agent_spinner_pos";
    private static final String TAG_URL = "url";
    private static final String TAG_CONN_IMMEDIATELY = "conn_immediately";
    private static final String TAG_PERM_DIALOG_IS_SHOW = "perm_dialog_is_show";

    /* In the absence of any parameter need set 0 or null */

    private AlertDialog alert;
    private AppCompatActivity activity;
    private UserAgentAdapter userAgentAdapter;
    private AddDownloadViewModel viewModel;
    private BaseAlertDialog addLinkDialog;
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private DialogAddDownloadBinding binding;
    private int userAgentSpinnerPos = 0;
    private boolean connImmediately = false;
    private CompositeDisposable disposable = new CompositeDisposable();
    private boolean permDialogIsShow = false;

    public static AddDownloadDialog newInstance(String url)
    {
        AddDownloadDialog frag = new AddDownloadDialog();

        Bundle args = new Bundle();
        args.putString(TAG_URL, url);
        frag.setArguments(args);

        return frag;
    }

    @Override
    public void onAttach(Context context)
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

        disposable.clear();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null)
            permDialogIsShow = savedInstanceState.getBoolean(TAG_PERM_DIALOG_IS_SHOW);

        if (!Utils.checkStoragePermission(activity.getApplicationContext()) && !permDialogIsShow) {
            permDialogIsShow = true;
            startActivity(new Intent(activity, RequestPermissions.class));
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        viewModel = ViewModelProviders.of(activity).get(AddDownloadViewModel.class);

        FragmentManager fm = getFragmentManager();
        if (fm != null)
            addLinkDialog = (BaseAlertDialog)fm.findFragmentByTag(TAG_ADD_USER_AGENT_DIALOG);
        dialogViewModel = ViewModelProviders.of(activity).get(BaseAlertDialog.SharedViewModel.class);
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (addLinkDialog == null)
                        return;
                    switch (event) {
                        case POSITIVE_BUTTON_CLICKED:
                            Dialog dialog = addLinkDialog.getDialog();
                            if (dialog != null) {
                                TextInputEditText editText = dialog.findViewById(R.id.text_input_dialog);
                                String userAgent = editText.getText().toString();
                                if (!TextUtils.isEmpty(userAgent))
                                    disposable.add(viewModel.addUserAgent(new UserAgent(userAgent))
                                            .subscribeOn(Schedulers.io())
                                            .subscribe());
                            }
                        case NEGATIVE_BUTTON_CLICKED:
                            addLinkDialog.dismiss();
                            break;
                    }
                });
        disposable.add(d);

        LayoutInflater i = LayoutInflater.from(activity);
        binding = DataBindingUtil.inflate(i, R.layout.dialog_add_download, null, false);
        binding.setModel(viewModel);

        if (savedInstanceState != null) {
            userAgentSpinnerPos = savedInstanceState.getInt(TAG_USER_AGENT_SPINNER_POS);
            connImmediately = savedInstanceState.getBoolean(TAG_CONN_IMMEDIATELY);
        }

        initLayoutView();

        return alert;
    }

    private void initLayoutView()
    {
        initAdvancedLayout();

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

        /* Init link field */
        if (TextUtils.isEmpty(viewModel.params.getUrl())) {
            connImmediately = true;
            String url = getArguments().getString(TAG_URL);
            if (TextUtils.isEmpty(url)) {
                connImmediately = false;
                /* Inserting a link from the clipboard */
                String clipboard = Utils.getClipboard(activity.getApplicationContext());
                if (clipboard != null) {
                    String c = clipboard.toLowerCase();
                    if (c.startsWith(Utils.HTTP_PREFIX) ||
                        c.startsWith(Utils.HTTPS_PREFIX) ||
                        c.startsWith(Utils.FTP_PREFIX)) {
                        url = clipboard;
                    }
                }
            }
            if (url != null)
                viewModel.params.setUrl(url);
        }

        userAgentAdapter = new UserAgentAdapter(activity, (userAgent) -> {
            disposable.add(viewModel.deleteUserAgent(userAgent)
                    .subscribeOn(Schedulers.io())
                    .subscribe());
        });
        /* Get other user agents */
        viewModel.observerUserAgents().observe(this, (userAgentList) -> {
            userAgentAdapter.clear();
            /* System user agent is always first */
            UserAgent systemUserAgent = new UserAgent(Utils.getSystemUserAgent(activity));
            systemUserAgent.readOnly = true;
            userAgentAdapter.add(systemUserAgent);
            userAgentAdapter.addAll(userAgentList);
            if (userAgentSpinnerPos > 0)
                binding.userAgent.setSelection(userAgentSpinnerPos);
        });

        binding.userAgent.setAdapter(userAgentAdapter);
        binding.userAgent.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l)
            {
                userAgentSpinnerPos = i;
                Object item = binding.userAgent.getItemAtPosition(i);
                if (item != null)
                    viewModel.params.setUserAgent(((UserAgent)item).userAgent);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView)
            {
                /* Nothing */
            }
        });
        binding.addUserAgent.setOnClickListener((View v) -> addUserAgentDialog());

        initAlertDialog(binding.getRoot());
    }

    private void initAlertDialog(View view)
    {
        alert = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.add_download)
                .setPositiveButton(R.string.connect, null)
                .setNegativeButton(R.string.cancel, null)
                .setView(view)
                .create();

        alert.setCanceledOnTouchOutside(false);
        alert.setOnShowListener((DialogInterface dialog) -> {
            viewModel.fetchState.observe(AddDownloadDialog.this, (state) -> {
                switch (state.status) {
                    case FETCHING:
                        enableFetchMode();
                        break;
                    case ERROR:
                        disableFetchMode();
                        showFetchError(state.error);
                        break;
                    case FETCHED:
                        disableFetchMode();
                        enableAddMode();
                        break;
                }
            });

            Button positiveButton = alert.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
            positiveButton.setOnClickListener((View v) -> {
                AddDownloadViewModel.State state = viewModel.fetchState.getValue();
                if (state == null)
                    return;
                switch (state.status) {
                    case FETCHED:
                        addDownload();
                        break;
                    case UNKNOWN:
                    case ERROR:
                        fetchLink();
                        break;
                }
            });
            negativeButton.setOnClickListener((View v) ->
                    finish(new Intent(), FragmentCallback.ResultCode.CANCEL));

            /* Emulate connect button click */
            if (connImmediately) {
                connImmediately = false;
                positiveButton.callOnClick();
            }
        });
    }

    private void initAdvancedLayout()
    {
        binding.advancedLayout.setListener(new ExpandableLayoutListener() {
            @Override
            public void onAnimationStart()
            {
                /* Nothing */
            }

            @Override
            public void onAnimationEnd()
            {
                /* Nothing */
            }

            @Override
            public void onPreOpen()
            {
                createRotateAnimator(binding.expandButton, 0f, 180f).start();
            }

            @Override
            public void onPreClose()
            {
                createRotateAnimator(binding.expandButton, 180f, 0f).start();
            }

            @Override
            public void onOpened()
            {
                /* Nothing */
            }

            @Override
            public void onClosed()
            {
                /* Nothing */
            }
        });
        binding.expandableSpinner.setOnClickListener((View view) -> binding.advancedLayout.toggle());
    }

    private ObjectAnimator createRotateAnimator(final View target, final float from, final float to)
    {
        ObjectAnimator animator = ObjectAnimator.ofFloat(target, "rotation", from, to);
        animator.setDuration(300);
        animator.setInterpolator(com.github.aakira.expandablelayout.Utils.createInterpolator(
                com.github.aakira.expandablelayout.Utils.LINEAR_INTERPOLATOR));

        return animator;
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        outState.putInt(TAG_USER_AGENT_SPINNER_POS, userAgentSpinnerPos);
        outState.putBoolean(TAG_CONN_IMMEDIATELY, connImmediately);
        outState.putBoolean(TAG_PERM_DIALOG_IS_SHOW, permDialogIsShow);

        super.onSaveInstanceState(outState);
    }

    private void addUserAgentDialog()
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_ADD_USER_AGENT_DIALOG) == null) {
            addLinkDialog = BaseAlertDialog.newInstance(
                    getString(R.string.dialog_add_user_agent_title),
                    null,
                    R.layout.dialog_text_input,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    false);

            addLinkDialog.show(fm, TAG_ADD_USER_AGENT_DIALOG);
        }
    }

    private void enableFetchMode()
    {
        hideFetchError();
        binding.link.setEnabled(false);
        binding.fetchProgress.show();
        alert.getButton(DialogInterface.BUTTON_POSITIVE).setVisibility(View.GONE);
    }

    private void disableFetchMode()
    {
        binding.link.setEnabled(true);
        binding.fetchProgress.hide();
        alert.getButton(DialogInterface.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
    }

    private void hideFetchError()
    {
        binding.layoutLink.setErrorEnabled(false);
        binding.layoutLink.setError(null);
    }

    private void enableAddMode()
    {
        binding.link.setEnabled(false);
        binding.paramsLayout.setVisibility(View.VISIBLE);
        alert.getButton(DialogInterface.BUTTON_POSITIVE).setText(R.string.add);
        binding.partialSupportWarning.setVisibility((viewModel.params.isPartialSupport() ? View.GONE : View.VISIBLE));
        binding.piecesNumber.setEnabled(viewModel.params.isPartialSupport() && viewModel.params.getTotalBytes() > 0);
        binding.piecesNumberSelect.setEnabled(viewModel.params.isPartialSupport() && viewModel.params.getTotalBytes() > 0);
    }

    private boolean checkUrlField(String s, TextInputLayout layout)
    {
        if (s == null || layout == null)
            return false;

        if (TextUtils.isEmpty(s)) {
            layout.setErrorEnabled(true);
            layout.setError(getString(R.string.download_error_empty_link));
            layout.requestFocus();

            return false;
        }

        layout.setErrorEnabled(false);
        layout.setError(null);

        return true;
    }

    private boolean checkNameField(String s, TextInputLayout layout)
    {
        if (s == null || layout == null)
            return false;

        if (TextUtils.isEmpty(s)) {
            layout.setErrorEnabled(true);
            layout.setError(getString(R.string.download_error_empty_name));
            layout.requestFocus();

            return false;
        }
        if (!FileUtils.isValidFatFilename(s)) {
            String format = getString(R.string.download_error_name_is_not_correct);
            layout.setErrorEnabled(true);
            layout.setError(String.format(format, FileUtils.buildValidFatFilename(s)));
            layout.requestFocus();

            return false;
        }

        layout.setErrorEnabled(false);
        layout.setError(null);

        return true;
    }

    private void showFetchError(Throwable e)
    {
        if (e == null) {
            hideFetchError();
            return;
        }

        String errorStr;

        if (e instanceof MalformedURLException) {
            errorStr = String.format(getString(R.string.fetch_error_invalid_url), e.getMessage());

        } else if (e instanceof ConnectException) {
            errorStr = String.format(getString(R.string.fetch_error_default_fmt),
                    getString(R.string.fetch_error_network_disconnected));

        } else if (e instanceof HttpException) {
            HttpException httpErr = (HttpException)e;
            if (httpErr.getResponseCode() > 0)
                errorStr = String.format(getString(R.string.fetch_error_http_response), httpErr.getResponseCode());
            else
                errorStr = String.format(getString(R.string.fetch_error_default_fmt), httpErr.getMessage());

        } else if (e instanceof IOException) {
            errorStr = String.format(getString(R.string.fetch_error_io), e.getMessage());

        } else {
            errorStr = String.format(getString(R.string.fetch_error_default_fmt), e.getMessage());
        }

        binding.layoutLink.setErrorEnabled(true);
        binding.layoutLink.setError(errorStr);
        binding.layoutLink.requestFocus();
    }

    private void fetchLink()
    {
        if (!checkUrlField(binding.link.getText().toString(), binding.layoutLink))
            return;

        viewModel.startFetchTask();
    }

    private void addDownload()
    {
        if (!checkNameField(binding.name.getText().toString(), binding.layoutName))
            return;

        createFileDialog();
    }

    private void createFileDialog()
    {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(viewModel.params.getMimeType());
        intent.putExtra(Intent.EXTRA_TITLE, viewModel.params.getFileName());
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        try {
            startActivityForResult(intent, CREATE_FILE_REQUEST_CODE);

        } catch (ActivityNotFoundException e) {
            /* Device doesn't support SAF, (e.g. Android TV) */
            createCustomFileDialog();
        }
    }

    private void createCustomFileDialog()
    {
        Intent i = new Intent(activity, FileManagerDialog.class);

        String fileName = viewModel.params.getFileName();
        String extension = null;
        if (TextUtils.isEmpty(FileUtils.getExtension(fileName)))
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(
                    viewModel.params.getMimeType());

        FileManagerConfig config = new FileManagerConfig(
                FileUtils.getUserDirPath(),
                getString(R.string.select_folder_to_save),
                null,
                FileManagerConfig.SAVE_FILE_MODE)
                .setFileName((extension == null) ?
                        fileName :
                        fileName + FileUtils.EXTENSION_SEPARATOR + extension);
        i.putExtra(FileManagerDialog.TAG_CONFIG, config);
        startActivityForResult(i, CHOOSE_PATH_TO_SAVE_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (resultCode != Activity.RESULT_OK)
            return;

        if (data == null) {
            createFileErrorToast();
            return;
        }
        switch (requestCode) {
            case CREATE_FILE_REQUEST_CODE:
                viewModel.addDownload(data.getData());
                break;
            case CHOOSE_PATH_TO_SAVE_REQUEST_CODE:
                if (!data.hasExtra(FileManagerDialog.TAG_RETURNED_PATH)) {
                    createFileErrorToast();
                    return;
                }
                try {
                    File f = new File(data.getStringExtra(FileManagerDialog.TAG_RETURNED_PATH));
                    boolean success = f.createNewFile();
                    if (!success) {
                        createFileErrorToast();
                        return;
                    }
                    viewModel.addDownload(Uri.fromFile(f));
                } catch (IOException e) {
                    Log.e(TAG, Log.getStackTraceString(e));
                    createFileErrorToast();
                    return;
                }
                break;
        }

        Toast.makeText(activity.getApplicationContext(),
                String.format(getString(R.string.download_ticker_notify),
                              viewModel.params.getFileName()),
                Toast.LENGTH_SHORT)
                .show();

        finish(new Intent(), FragmentCallback.ResultCode.OK);
    }

    private void createFileErrorToast()
    {
        Toast.makeText(activity.getApplicationContext(),
                getString(R.string.add_download_error_unable_to_create_file),
                Toast.LENGTH_SHORT)
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
