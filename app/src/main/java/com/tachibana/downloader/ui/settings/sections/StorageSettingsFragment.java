/*
 * Copyright (C) 2019-2021 Tachibana General Laboratories, LLC
 * Copyright (C) 2019-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.ui.settings.sections;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.RepositoryHelper;
import com.tachibana.downloader.core.settings.SettingsRepository;
import com.tachibana.downloader.core.system.FileSystemContracts;
import com.tachibana.downloader.core.system.FileSystemFacade;
import com.tachibana.downloader.core.system.SystemFacadeHelper;
import com.takisoft.preferencex.PreferenceFragmentCompat;

public class StorageSettingsFragment extends PreferenceFragmentCompat
    implements Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = StorageSettingsFragment.class.getSimpleName();

    private static final String TAG_DIR_CHOOSER_BIND_PREF = "dir_chooser_bind_pref";

    private SettingsRepository pref;
    /* Preference that is associated with the current dir selection dialog */
    private String dirChooserBindPref;
    private FileSystemFacade fs;

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

        Context context = getActivity().getApplicationContext();
        pref = RepositoryHelper.getSettingsRepository(context);
        fs = SystemFacadeHelper.getFileSystemFacade(context);

        String keySaveDownloadsIn = getString(R.string.pref_key_save_downloads_in);
        Preference saveDownloadsIn = findPreference(keySaveDownloadsIn);
        if (saveDownloadsIn != null) {
            String saveInPath = pref.saveDownloadsIn();
            if (saveInPath != null) {
                Uri uri = Uri.parse(saveInPath);
                saveDownloadsIn.setSummary(fs.getDirName(uri));
                saveDownloadsIn.setOnPreferenceClickListener((preference) -> {
                    dirChooserBindPref = getString(R.string.pref_key_save_downloads_in);
                    dirChoose.launch(uri);

                    return true;
                });
            }
        }

        String keyMoveAfterDownload = getString(R.string.pref_key_move_after_download);
        SwitchPreferenceCompat moveAfterDownload = findPreference(keyMoveAfterDownload);
        if (moveAfterDownload != null) {
            moveAfterDownload.setChecked(pref.moveAfterDownload());
            bindOnPreferenceChangeListener(moveAfterDownload);
        }

        String keyMoveAfterDownloadIn = getString(R.string.pref_key_move_after_download_in);
        Preference moveAfterDownloadIn = findPreference(keyMoveAfterDownloadIn);
        if (moveAfterDownloadIn != null) {
            String moveInPath = pref.moveAfterDownloadIn();
            if (moveInPath != null) {
                Uri uri = Uri.parse(moveInPath);
                moveAfterDownloadIn.setSummary(fs.getDirName(uri));
                moveAfterDownloadIn.setOnPreferenceClickListener((preference) -> {
                    dirChooserBindPref = getString(R.string.pref_key_move_after_download_in);
                    dirChoose.launch(uri);

                    return true;
                });
            }
        }

        String keyDeleteFileIfError = getString(R.string.pref_key_delete_file_if_error);
        SwitchPreferenceCompat deleteFileIfError = findPreference(keyDeleteFileIfError);
        if (deleteFileIfError != null) {
            deleteFileIfError.setChecked(pref.deleteFileIfError());
            bindOnPreferenceChangeListener(deleteFileIfError);
        }

        String keyPreallocateDiskSpace = getString(R.string.pref_key_preallocate_disk_space);
        SwitchPreferenceCompat preallocateDiskSpace = findPreference(keyPreallocateDiskSpace);
        if (preallocateDiskSpace != null) {
            preallocateDiskSpace.setChecked(pref.preallocateDiskSpace());
            preallocateDiskSpace.setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
            bindOnPreferenceChangeListener(preallocateDiskSpace);
        }
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

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    final ActivityResultLauncher<Uri> dirChoose = registerForActivityResult(
            new FileSystemContracts.OpenDirectory(),
            uri -> {
                if (uri == null || dirChooserBindPref == null) {
                    return;
                }
                Preference p = findPreference(dirChooserBindPref);
                if (p == null) {
                    return;
                }

                fs.takePermissions(uri);
                if (dirChooserBindPref.equals(getString(R.string.pref_key_save_downloads_in))) {
                    pref.saveDownloadsIn(uri.toString());
                } else if (dirChooserBindPref.equals(getString(R.string.pref_key_move_after_download_in))) {
                    pref.moveAfterDownloadIn(uri.toString());
                }
                p.setSummary(fs.getDirName(uri));
            }
    );

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        if (preference.getKey().equals(getString(R.string.pref_key_move_after_download))) {
            pref.moveAfterDownload((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_delete_file_if_error))) {
            pref.deleteFileIfError((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_preallocate_disk_space))) {
            pref.preallocateDiskSpace((boolean)newValue);
        }

        return true;
    }
}
