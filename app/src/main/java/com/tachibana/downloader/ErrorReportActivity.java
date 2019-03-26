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

package com.tachibana.downloader;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;

import com.google.android.material.textfield.TextInputEditText;
import com.tachibana.downloader.dialog.BaseAlertDialog;
import com.tachibana.downloader.dialog.ErrorReportDialog;

import org.acra.ReportField;
import org.acra.data.CrashReportData;
import org.acra.dialog.BaseCrashReportDialog;
import org.acra.file.CrashReportPersister;
import org.acra.interaction.DialogInteraction;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.ViewModelProviders;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class ErrorReportActivity extends BaseCrashReportDialog
{
    @SuppressWarnings("unused")
    private static final String TAG = ErrorReportDialog.class.getSimpleName();

    private static final String TAG_ERROR_DIALOG = "error_dialog";
    private ErrorReportDialog errDialog;
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private CompositeDisposable disposables = new CompositeDisposable();

    @Override
    protected void init(@Nullable Bundle savedInstanceState)
    {
        super.init(savedInstanceState);

        dialogViewModel = ViewModelProviders.of(this).get(BaseAlertDialog.SharedViewModel.class);
        errDialog = (ErrorReportDialog)getSupportFragmentManager().findFragmentByTag(TAG_ERROR_DIALOG);

        if (errDialog == null) {
            errDialog = ErrorReportDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.app_error_occurred),
                    getStackTrace());

            errDialog.show(getSupportFragmentManager(), TAG_ERROR_DIALOG);
        }
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

        subscribeAlertDialog();
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents().subscribe(this::handleAlertDialogEvent);
        disposables.add(d);
    }

    private void handleAlertDialogEvent(BaseAlertDialog.Event event)
    {
        if (!event.dialogTag.equals(TAG_ERROR_DIALOG) || errDialog == null)
            return;
        switch (event.type) {
            case POSITIVE_BUTTON_CLICKED:
                Dialog dialog = errDialog.getDialog();
                if (dialog != null) {
                    TextInputEditText editText = dialog.findViewById(R.id.comment);
                    Editable e = editText.getText();
                    String comment = (e == null ? null : e.toString());

                    sendCrash(comment, null);
                    finish();
                }
                break;
            case NEGATIVE_BUTTON_CLICKED:
                cancelReports();
                finish();
                break;
        }
    }

    @Override
    public void finish()
    {
        if (errDialog != null)
            errDialog.dismiss();

        super.finish();
    }

    private String getStackTrace()
    {
        Intent i = getIntent();
        if (i == null)
            return null;

        Serializable reportFile = i.getSerializableExtra(DialogInteraction.EXTRA_REPORT_FILE);
        if (!(reportFile instanceof File))
            return null;
        /*
         * TODO: replace with CrashReportDialogHelper#getReportData in ACRA version 5.4.0,
         * see https://github.com/ACRA/acra/pull/736
         */
        CrashReportData crashReportData = getReportData((File)reportFile);
        if (crashReportData == null)
            return null;

        return crashReportData.getString(ReportField.STACK_TRACE);
    }

    @WorkerThread
    private CrashReportData getReportData(File reportFile)
    {
        CrashReportData reportData;
        try {
            reportData = new CrashReportPersister().load(reportFile);

        } catch (JSONException | IOException e) {
            return null;
        }

        return reportData;
    }
}
