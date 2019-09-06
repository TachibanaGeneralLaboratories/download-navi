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

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import com.tachibana.downloader.R;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.viewmodel.settings.SettingsViewModel;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProviders;

public class SettingsActivity extends AppCompatActivity
{
    @SuppressWarnings("unused")
    private static final String TAG = SettingsActivity.class.getSimpleName();

    private Toolbar toolbar;
    private TextView detailTitle;
    private SettingsViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        setTheme(Utils.getSettingsTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        viewModel = ViewModelProviders.of(this).get(SettingsViewModel.class);

        setContentView(R.layout.activity_settings);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.settings));
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        detailTitle = findViewById(R.id.detail_title);
        viewModel.detailTitleChanged.observe(this, title -> {
            if (title != null && detailTitle != null)
                detailTitle.setText(title);
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
            finish();

        return true;
    }
}
