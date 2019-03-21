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

package com.tachibana.downloader.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.utils.FileUtils;
import com.tachibana.downloader.dialog.filemanager.FileManagerConfig;
import com.tachibana.downloader.dialog.filemanager.FileManagerDialog;
import com.takisoft.preferencex.PreferenceFragmentCompat;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

public class StorageSettingsFragment extends PreferenceFragmentCompat
{
    @SuppressWarnings("unused")
    private static final String TAG = StorageSettingsFragment.class.getSimpleName();

    private static final String TAG_DIR_CHOOSER_BIND_PREF = "dir_chooser_bind_pref";

    private static final int DOWNLOAD_DIR_CHOOSE_REQUEST = 1;

    /* Preference that is associated with the current dir selection dialog */
    private String dirChooserBindPref;

    public static StorageSettingsFragment newInstance()
    {
        StorageSettingsFragment fragment = new StorageSettingsFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null)
            dirChooserBindPref = savedInstanceState.getString(TAG_DIR_CHOOSER_BIND_PREF);

        SharedPreferences pref = SettingsManager.getInstance(getActivity().getApplicationContext())
                .getPreferences();

        String keySaveDownloadsIn = getString(R.string.pref_key_save_downloads_in);
        Preference saveDownloadsIn = findPreference(keySaveDownloadsIn);
        Uri saveInPath = Uri.parse(pref.getString(keySaveDownloadsIn,
                                                  SettingsManager.Default.saveDownloadsIn));
        if (saveInPath != null)
            saveDownloadsIn.setSummary(FileUtils.getDirName(getActivity().getApplicationContext(), saveInPath));
        saveDownloadsIn.setOnPreferenceClickListener((Preference preference) -> {
            dirChooserBindPref = getString(R.string.pref_key_save_downloads_in);
            dirChooseDialog(saveInPath);

            return true;
        });

        String keyMoveAfterDownload = getString(R.string.pref_key_move_after_download);
        SwitchPreferenceCompat moveAfterDownload =
                (SwitchPreferenceCompat)findPreference(keyMoveAfterDownload);
        moveAfterDownload.setChecked(pref.getBoolean(keyMoveAfterDownload,
                                                     SettingsManager.Default.moveAfterDownload));

        String keyMoveAfterDownloadIn = getString(R.string.pref_key_move_after_download_in);
        Preference moveAfterDownloadIn = findPreference(keyMoveAfterDownloadIn);
        Uri moveInPath = Uri.parse(pref.getString(keyMoveAfterDownloadIn,
                                                  SettingsManager.Default.moveAfterDownloadIn));
        if (moveInPath != null)
            moveAfterDownloadIn.setSummary(FileUtils.getDirName(getActivity().getApplicationContext(), moveInPath));
        moveAfterDownloadIn.setOnPreferenceClickListener((Preference preference) -> {
            dirChooserBindPref = getString(R.string.pref_key_move_after_download_in);
            dirChooseDialog(moveInPath);

            return true;
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putString(TAG_DIR_CHOOSER_BIND_PREF, dirChooserBindPref);
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_storage, rootKey);
    }

    private void dirChooseDialog(Uri dirUri)
    {
        String dirPath = null;
        if (dirUri != null && FileUtils.isFileSystemPath(dirUri))
            dirPath = dirUri.getPath();

        Intent i = new Intent(getActivity(), FileManagerDialog.class);
        FileManagerConfig config = new FileManagerConfig(dirPath, null, FileManagerConfig.DIR_CHOOSER_MODE);
        i.putExtra(FileManagerDialog.TAG_CONFIG, config);

        startActivityForResult(i, DOWNLOAD_DIR_CHOOSE_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == DOWNLOAD_DIR_CHOOSE_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data == null || data.getData() == null || dirChooserBindPref == null)
                return;

            Context context = getActivity().getApplicationContext();
            Uri dirPath = data.getData();

            SharedPreferences pref = SettingsManager.getInstance(context).getPreferences();
            try {
                FileUtils.takeUriPermission(context, dirPath);

            } catch (SecurityException e) {
                /* Save default value */
                pref.edit().putString(dirChooserBindPref, SettingsManager.Default.saveDownloadsIn).apply();
                Preference p = findPreference(dirChooserBindPref);
                p.setSummary(FileUtils.getDirName(context, Uri.parse(SettingsManager.Default.saveDownloadsIn)));

                return;
            }
            pref.edit().putString(dirChooserBindPref, dirPath.toString()).apply();
            Preference p = findPreference(dirChooserBindPref);
            p.setSummary(FileUtils.getDirName(context, dirPath));
        }
    }
}
