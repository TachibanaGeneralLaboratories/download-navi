/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of DownloadNavi.
 *
 * DownloadNavi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DownloadNavi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DownloadNavi.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tachibana.downloader.dialog;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
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
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.github.aakira.expandablelayout.ExpandableLayoutListener;
import com.github.aakira.expandablelayout.ExpandableLinearLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tachibana.downloader.FragmentCallback;
import com.tachibana.downloader.R;
import com.tachibana.downloader.adapter.UserAgentAdapter;
import com.tachibana.downloader.core.AddDownloadParams;
import com.tachibana.downloader.core.DownloadInfo;
import com.tachibana.downloader.core.HttpConnection;
import com.tachibana.downloader.core.exception.HttpException;
import com.tachibana.downloader.core.storage.DownloadStorage;
import com.tachibana.downloader.core.utils.FileUtils;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.service.DownloadScheduler;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.concurrent.atomic.AtomicReference;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

public class AddDownloadDialog extends DialogFragment
    implements BaseAlertDialog.OnClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = AddDownloadDialog.class.getSimpleName();

    private static final String TAG_ADD_USER_AGENT_DIALOG = "add_user_agent_dialog";
    private static final int CREATE_FILE_REQUEST_CODE = 1;

    /* In the absence of any parameter need set 0 or null */

    private AlertDialog alert;
    private AppCompatActivity activity;
    private TextInputEditText linkField, nameField, descriptionField;
    private RelativeLayout partialSupportWarning;
    private ImageView partialSupportWarningImg;
    private TextInputLayout linkFieldLayout, nameFieldLayout;
    private LinearLayout paramsLayout;
    private ContentLoadingProgressBar progressBar;
    private TextView sizeView;
    private CheckBox wifiOnly, retry;
    private Spinner userAgentSpinner;
    private UserAgentAdapter userAgentAdapter;
    private ImageButton addUserAgentButton;
    private TextView numPiecesView;
    private AppCompatSeekBar selectNumPieces;
    private FetchLinkTask fetchTask;
    private AtomicReference<State> fetchState = new AtomicReference<>(State.UNKNOWN);
    private Throwable fetchError;
    private AddDownloadParams params;
    private enum State
    {
        UNKNOWN,
        FETCHING,
        FETCHED,
        ERROR
    }

    public static AddDownloadDialog newInstance()
    {
        AddDownloadDialog frag = new AddDownloadDialog();

        Bundle args = new Bundle();
        frag.setArguments(args);

        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        /* Retain this fragment across configuration changes */
        setRetainInstance(true);
    }

    @Override
    public void onDestroyView()
    {
        Dialog dialog = getDialog();
        /* Handles https://code.google.com/p/android/issues/detail?id=17423 */
        if (dialog != null && getRetainInstance())
            dialog.setDismissMessage(null);

        super.onDestroyView();
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

        LayoutInflater i = LayoutInflater.from(activity);
        View v = i.inflate(R.layout.dialog_add_download, null);
        initLayoutView(v);

        return alert;
    }

    private void initLayoutView(View view)
    {
        if (view == null)
            return;

        initAdvancedLayout(view);

        linkField = view.findViewById(R.id.download_link);
        linkFieldLayout = view.findViewById(R.id.layout_download_link);
        partialSupportWarning = view.findViewById(R.id.partial_support_warning);
        partialSupportWarningImg = view.findViewById(R.id.partial_support_warning_img);
        partialSupportWarningImg.getDrawable().setColorFilter(
                ContextCompat.getColor(activity, R.color.warning), PorterDuff.Mode.SRC_IN);
        nameField = view.findViewById(R.id.download_name);
        nameFieldLayout = view.findViewById(R.id.layout_download_name);
        descriptionField = view.findViewById(R.id.download_description);
        paramsLayout = view.findViewById(R.id.download_params_layout);
        progressBar = view.findViewById(R.id.download_fetch_progress);
        sizeView = view.findViewById(R.id.download_size);
        updateSizeView();
        wifiOnly = view.findViewById(R.id.download_wifi_only);
        retry = view.findViewById(R.id.download_retry);
        userAgentSpinner = view.findViewById(R.id.download_user_agent);
        addUserAgentButton = view.findViewById(R.id.add_user_agent);
        numPiecesView = view.findViewById(R.id.download_pieces_number);
        selectNumPieces = view.findViewById(R.id.download_pieces_number_select);
        numPiecesView.setText(String.format(
                getString(R.string.download_pieces_number_title), DownloadInfo.MIN_PIECES));
        selectNumPieces.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                /* Because progress starts from zero */
                progress += 1;
                if (params != null)
                    params.numPieces = progress;
                numPiecesView.setText(String.format(
                        getString(R.string.download_pieces_number_title), progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { /* Nothing */}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { /* Nothing */}
        });

        /* Dismiss error label if user has changed the text */
        linkField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Nothing */ }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                hideFetchError();
            }

            @Override
            public void afterTextChanged(Editable s) { /* Nothing */ }
        });
        nameField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Nothing */ }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                nameFieldLayout.setErrorEnabled(false);
                nameFieldLayout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) { /* Nothing */ }
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
            if (url != null)
                linkField.setText(url);
        }

        userAgentAdapter = new UserAgentAdapter(activity);
        userAgentSpinner.setAdapter(userAgentAdapter);
        userAgentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l)
            {
                if (params != null) {
                    Object item = userAgentSpinner.getItemAtPosition(i);
                    if (item != null)
                        params.userAgent = (String)item;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView)
            {
                /* Nothing */
            }
        });
        addUserAgentButton.setOnClickListener((View v) -> addUserAgentDialog());

        initAlertDialog(view);
    }

    private void updateSizeView()
    {
        String sizeStr = (params != null && params.totalBytes >= 0 ?
                Formatter.formatFileSize(activity, params.totalBytes) :
                getString(R.string.not_available));
        sizeView.setText(String.format(getString(R.string.download_size), sizeStr));
    }

    private void initAlertDialog(View view)
    {
        alert = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.add_download)
                .setPositiveButton(R.string.connect, null)
                .setNegativeButton(R.string.cancel, null)
                .setView(view)
                .create();

        alert.setOnShowListener((DialogInterface dialog) -> {
            switch (fetchState.get()) {
                case FETCHING:
                    enableFetchMode(false);
                    break;
                case FETCHED:
                    enableAddMode();
                    break;
                case ERROR:
                    showFetchError();
                    break;
            }

            Button positiveButton = alert.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
            positiveButton.setOnClickListener((View v) -> {
                switch (fetchState.get()) {
                    case FETCHED:
                        addDownload();
                        break;
                    case UNKNOWN:
                    case ERROR:
                        String url = linkField.getText().toString();
                        if (checkUrlField(url, linkFieldLayout)) {
                            url = Utils.normalizeURL(url);
                            linkField.setText(url);
                            startFetchTask(url);
                        }
                        break;
                }
            });
            negativeButton.setOnClickListener((View v) ->
                    finish(new Intent(), FragmentCallback.ResultCode.CANCEL));
        });
    }

    private void initAdvancedLayout(View v)
    {
        final RelativeLayout expandableSpinner = v.findViewById(R.id.expandable_spinner);
        final RelativeLayout expandButton = v.findViewById(R.id.expand_button);
        final ExpandableLinearLayout advancedLayout = v.findViewById(R.id.advanced_layout);
        advancedLayout.setListener(new ExpandableLayoutListener() {
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
                createRotateAnimator(expandButton, 0f, 180f).start();
            }

            @Override
            public void onPreClose()
            {
                createRotateAnimator(expandButton, 180f, 0f).start();
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
        expandableSpinner.setOnClickListener((View view) -> advancedLayout.toggle());
    }

    private ObjectAnimator createRotateAnimator(final View target, final float from, final float to)
    {
        ObjectAnimator animator = ObjectAnimator.ofFloat(target, "rotation", from, to);
        animator.setDuration(300);
        animator.setInterpolator(com.github.aakira.expandablelayout.Utils.createInterpolator(
                com.github.aakira.expandablelayout.Utils.LINEAR_INTERPOLATOR));

        return animator;
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
            if (!TextUtils.isEmpty(userAgent)) {
                int position = userAgentAdapter.addAgent(userAgent);
                if (position > 0)
                    userAgentSpinner.setSelection(position);
            }
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

    private void enableFetchMode(boolean firstRun)
    {
        hideFetchError();
        linkField.setEnabled(false);
        if (firstRun)
            progressBar.show();
        else
            progressBar.setVisibility(View.VISIBLE);
        alert.getButton(DialogInterface.BUTTON_POSITIVE).setVisibility(View.GONE);
    }

    private void disableFetchMode()
    {
        linkField.setEnabled(true);
        progressBar.hide();
        alert.getButton(DialogInterface.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
    }

    private void enableAddMode()
    {
        linkField.setEnabled(false);
        paramsLayout.setVisibility(View.VISIBLE);
        alert.getButton(DialogInterface.BUTTON_POSITIVE).setText(R.string.add);
        partialSupportWarning.setVisibility((params.partialSupport ? View.GONE : View.VISIBLE));
        numPiecesView.setEnabled(params.partialSupport && params.totalBytes > 0);
        selectNumPieces.setEnabled(params.partialSupport && params.totalBytes > 0);
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

    private void startFetchTask(String url)
    {
        if (fetchTask != null && fetchTask.getStatus() != FetchLinkTask.Status.FINISHED)
            return;

        fetchTask = new FetchLinkTask(this);
        fetchTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url);
    }

    private static class FetchLinkTask extends AsyncTask<String, Void, Throwable>
    {
        private final WeakReference<AddDownloadDialog> fragment;

        private FetchLinkTask(AddDownloadDialog fragment)
        {
            this.fragment = new WeakReference<>(fragment);
        }

        @Override
        protected void onPreExecute()
        {
            if (fragment.get() != null) {
                fragment.get().fetchState.set(State.FETCHING);
                fragment.get().enableFetchMode(true);
            }
        }

        @Override
        protected Throwable doInBackground(String... params)
        {
            if (fragment.get() == null || isCancelled())
                return null;

            String url = params[0];
            if (url == null)
                return null;

            HttpConnection connection;
            try {
                connection = new HttpConnection(url);
            } catch (Exception e) {
                return e;
            }

            NetworkInfo netInfo = Utils.getActiveNetworkInfo(fragment.get().activity.getApplicationContext());
            if (netInfo == null || !netInfo.isConnected())
                return new ConnectException("Network is disconnected");

            Exception err[] = new Exception[1];
            connection.setListener(new HttpConnection.Listener() {
                @Override
                public void onConnectionCreated(HttpURLConnection conn)
                {
                    /* TODO: user agent spoofing (from settings) */
                }

                @Override
                public void onResponseHandle(HttpURLConnection conn, int code, String message)
                {
                    switch (code) {
                        case HttpURLConnection.HTTP_OK:
                            fragment.get().parseOkHeaders(conn);
                            break;
                        default:
                            err[0] = new HttpException("Failed to fetch link, response code: " + code, code);
                            break;
                    }
                }

                @Override
                public void onMovedPermanently(String newUrl)
                {
                    /* Nothing */
                }

                @Override
                public void onIOException(IOException e)
                {
                    err[0] = e;
                }

                @Override
                public void onTooManyRedirects()
                {
                    err[0] = new HttpException("Too many redirects");
                }
            });
            connection.run();

            return err[0];
        }

        @Override
        protected void onPostExecute(Throwable e)
        {
            if (fragment.get() == null)
                return;

            fragment.get().disableFetchMode();
            if (e != null) {
                Log.e(TAG, Log.getStackTraceString(e));
                fragment.get().fetchState.set(State.ERROR);
                fragment.get().fetchError = e;
                fragment.get().showFetchError();

                return;
            } else {
                fragment.get().fetchState.set(State.FETCHED);
            }
            fragment.get().onLinkFetched();
        }
    }

    private void parseOkHeaders(HttpURLConnection conn)
    {
        if (params == null)
            params = new AddDownloadParams(linkField.getText().toString());

        String contentDisposition = conn.getHeaderField("Content-Disposition");
        String contentLocation = conn.getHeaderField("Content-Location");

        params.fileName = Utils.getHttpFileName(params.url, contentDisposition, contentLocation);
        params.mimeType = Intent.normalizeMimeType(conn.getContentType());
        if (params.mimeType == null)
            params.mimeType = "application/octet-stream";
        params.etag = conn.getHeaderField("ETag");
        Object item = userAgentSpinner.getSelectedItem();
        if (item != null)
            params.userAgent = (String)item;
        final String transferEncoding = conn.getHeaderField("Transfer-Encoding");
        if (transferEncoding == null) {
            try {
                params.totalBytes = Long.parseLong(conn.getHeaderField("Content-Length"));

            } catch (NumberFormatException e) {
                params.totalBytes = -1;
            }
        } else {
            params.totalBytes = -1;
        }
        params.partialSupport = "bytes".equalsIgnoreCase(conn.getHeaderField("Accept-Ranges"));
    }

    private void onLinkFetched()
    {
        if (params == null)
            return;

        nameField.setText(params.fileName);
        updateSizeView();
        enableAddMode();
    }

    private void showFetchError()
    {
        if (fetchError == null) {
            hideFetchError();
            return;
        }

        String errorStr;

        if (fetchError instanceof MalformedURLException) {
            errorStr = String.format(getString(R.string.fetch_error_invalid_url), fetchError.getMessage());

        } else if (fetchError instanceof ConnectException) {
            errorStr = String.format(getString(R.string.fetch_error_default_fmt),
                    getString(R.string.fetch_error_network_disconnected));

        } else if (fetchError instanceof HttpException) {
            HttpException e = (HttpException)fetchError;
            if (e.getResponseCode() > 0)
                errorStr = String.format(getString(R.string.fetch_error_http_response), e.getResponseCode());
            else
                errorStr = String.format(getString(R.string.fetch_error_default_fmt), e.getMessage());

        } else if (fetchError instanceof IOException) {
            errorStr = String.format(getString(R.string.fetch_error_io), fetchError.getMessage());

        } else {
            errorStr = String.format(getString(R.string.fetch_error_default_fmt), fetchError.getMessage());
        }

        linkFieldLayout.setErrorEnabled(true);
        linkFieldLayout.setError(errorStr);
        linkFieldLayout.requestFocus();
    }

    private void hideFetchError()
    {
        linkFieldLayout.setErrorEnabled(false);
        linkFieldLayout.setError(null);
    }

    private void addDownload()
    {
        if (params == null)
            return;

        String fileName = nameField.getText().toString();
        if (!checkNameField(fileName, nameFieldLayout))
            return;
        params.fileName = fileName;

        createFileDialog();
    }

    private void addDownload(Uri filePath)
    {
        if (params == null || filePath == null)
            return;

        params.filePath = filePath;
        params.wifiOnly = wifiOnly.isChecked();
        /*
         * File name could have changed in the file creation dialog or
         * an extension was added to it
         */
        DocumentFile file = DocumentFile.fromSingleUri(activity, params.filePath);
        String fileName = file.getName();
        if (fileName != null)
            params.fileName = fileName;
        params.description = descriptionField.getText().toString();

        DownloadInfo info = params.toDownloadInfo();
        info.setRetry(retry.isChecked());

        DownloadStorage storage = new DownloadStorage(activity.getApplicationContext());
        if (!storage.addInfo(info)) {
            Toast.makeText(activity.getApplicationContext(),
                    getString(R.string.download_error_unable_to_add_download),
                    Toast.LENGTH_LONG)
                    .show();
            return;
        }

        DownloadScheduler.runDownload(activity.getApplicationContext(), info.getId());

        finish(new Intent(), FragmentCallback.ResultCode.OK);
    }

    private void createFileDialog()
    {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(params.mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, params.fileName);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (params == null || requestCode != CREATE_FILE_REQUEST_CODE || resultCode != Activity.RESULT_OK)
            return;

        if (data == null) {
            Toast.makeText(activity.getApplicationContext(),
                    getString(R.string.add_download_error_unable_to_create_file),
                    Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        Uri filePath = data.getData();
        if (!checkFreeSpace(filePath))
            return;

        addDownload(filePath);
    }

    private boolean checkFreeSpace(Uri filePath)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            long availBytes = -1L;
            try {
                ParcelFileDescriptor pfd = activity.getContentResolver().openFileDescriptor(filePath, "r");
                availBytes = FileUtils.getAvailableBytes(pfd.getFileDescriptor());

            } catch (Exception e) {
                /* Ignore */
            }

            if (availBytes < params.totalBytes) {
                String totalSizeStr = Formatter.formatFileSize(activity, params.totalBytes);
                String availSizeStr = Formatter.formatFileSize(activity, availBytes);
                String format = getString(R.string.download_error_no_enough_free_space);

                Toast.makeText(activity.getApplicationContext(),
                        String.format(format, availSizeStr, totalSizeStr),
                        Toast.LENGTH_LONG)
                        .show();

                return false;
            }

            return true;
        }

        return true;
    }

    public void onBackPressed()
    {
        finish(new Intent(), FragmentCallback.ResultCode.BACK);
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code)
    {
        if (fetchTask != null)
            fetchTask.cancel(true);

        alert.dismiss();
        ((FragmentCallback)activity).fragmentFinished(intent, code);
    }
}
