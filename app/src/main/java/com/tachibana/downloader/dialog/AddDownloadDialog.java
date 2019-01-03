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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import com.github.aakira.expandablelayout.ExpandableLayoutListener;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tachibana.downloader.FragmentCallback;
import com.tachibana.downloader.R;
import com.tachibana.downloader.adapter.UserAgentAdapter;
import com.tachibana.downloader.core.entity.UserAgent;
import com.tachibana.downloader.core.exception.HttpException;
import com.tachibana.downloader.core.utils.FileUtils;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.databinding.DialogAddDownloadBinding;
import com.tachibana.downloader.viewmodel.AddDownloadViewModel;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;

public class AddDownloadDialog extends DialogFragment
    implements BaseAlertDialog.OnClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = AddDownloadDialog.class.getSimpleName();

    private static final String TAG_ADD_USER_AGENT_DIALOG = "add_user_agent_dialog";
    private static final int CREATE_FILE_REQUEST_CODE = 1;
    private static final String TAG_USER_AGENT_SPINNER_POS = "user_agent_spinner_pos";

    /* In the absence of any parameter need set 0 or null */

    private AlertDialog alert;
    private AppCompatActivity activity;
    private UserAgentAdapter userAgentAdapter;
    private AddDownloadViewModel viewModel;
    private DialogAddDownloadBinding binding;
    private int userAgentSpinnerPos = 0;

    public static AddDownloadDialog newInstance()
    {
        AddDownloadDialog frag = new AddDownloadDialog();

        Bundle args = new Bundle();
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
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        viewModel = ViewModelProviders.of(activity).get(AddDownloadViewModel.class);

        LayoutInflater i = LayoutInflater.from(activity);
        binding = DataBindingUtil.inflate(i, R.layout.dialog_add_download, null, false);
        binding.setModel(viewModel);

        if (savedInstanceState != null)
            userAgentSpinnerPos = savedInstanceState.getInt(TAG_USER_AGENT_SPINNER_POS);

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

        /* Inserting a link from the clipboard */
        String clipboard = Utils.getClipboard(activity.getApplicationContext());
        String url = null;
        if (clipboard != null) {
            String c = clipboard.toLowerCase();
            if (c.startsWith(Utils.HTTP_PREFIX) ||
                c.startsWith(Utils.HTTPS_PREFIX) ||
                c.startsWith(Utils.FTP_PREFIX)) {
                url = clipboard;
            }
            if (url != null && TextUtils.isEmpty(viewModel.params.getUrl()))
                viewModel.params.setUrl(url);
        }

        userAgentAdapter = new UserAgentAdapter(activity, viewModel::deleteUserAgent);
        String systemUserAgent = new WebView(activity).getSettings().getUserAgentString();
        /* Get other user agents */
        viewModel.observerUserAgents().observe(this, (userAgentList) -> {
            userAgentAdapter.clear();
            /* System user agent is always first */
            userAgentAdapter.add(new UserAgent(systemUserAgent));
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

        super.onSaveInstanceState(outState);
    }

    private void addUserAgentDialog()
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_ADD_USER_AGENT_DIALOG) == null) {
            BaseAlertDialog addLinkDialog = BaseAlertDialog.newInstance(
                    getString(R.string.dialog_add_user_agent_title),
                    null,
                    R.layout.dialog_text_input,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    this);

            addLinkDialog.show(fm, TAG_ADD_USER_AGENT_DIALOG);
        }
    }

    @Override
    public void onPositiveClicked(View v)
    {
        if (v == null)
            return;

        FragmentManager fm = getFragmentManager();
        if (fm == null)
            return;

        if (fm.findFragmentByTag(TAG_ADD_USER_AGENT_DIALOG) != null) {
            TextInputEditText editText = v.findViewById(R.id.text_input_dialog);
            String userAgent = editText.getText().toString();
            if (!TextUtils.isEmpty(userAgent))
                viewModel.addUserAgent(new UserAgent(userAgent));
        }
    }

    @Override
    public void onNegativeClicked(View v)
    {
        /* Nothing */
    }

    @Override
    public void onNeutralClicked(View v)
    {
        /* Nothing */
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

        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode != CREATE_FILE_REQUEST_CODE || resultCode != Activity.RESULT_OK)
            return;

        if (data == null) {
            Toast.makeText(activity.getApplicationContext(),
                    getString(R.string.add_download_error_unable_to_create_file),
                    Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        viewModel.addDownload(data.getData());

        finish(new Intent(), FragmentCallback.ResultCode.OK);
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
