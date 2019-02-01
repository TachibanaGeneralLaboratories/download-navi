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

package com.tachibana.downloader;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.tachibana.downloader.adapter.DownloadListPagerAdapter;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.receiver.NotificationReceiver;

public class MainActivity extends AppCompatActivity
{
    private static final String TAG_PERM_DIALOG_IS_SHOW = "perm_dialog_is_show";

    /* Android data binding doesn't work with layout aliases */
    private CoordinatorLayout coordinatorLayout;
    private Toolbar toolbar;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle toggle;
    private TabLayout tabLayout;
    private ViewPager viewPager;
    private DownloadListPagerAdapter adapter;
    private FloatingActionButton fab;
    private boolean permDialogIsShow = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        setTheme(Utils.getAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        if (getIntent().getAction() != null &&
            getIntent().getAction().equals(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP)) {
            finish();
            return;
        }

        if (savedInstanceState != null)
            permDialogIsShow = savedInstanceState.getBoolean(TAG_PERM_DIALOG_IS_SHOW);

        if (!Utils.checkStoragePermission(getApplicationContext()) && !permDialogIsShow) {
            permDialogIsShow = true;
            startActivity(new Intent(this, RequestPermissions.class));
        }

        setContentView(R.layout.activity_main);

        initLayout();
    }

    private void initLayout()
    {
        toolbar = findViewById(R.id.toolbar);
        coordinatorLayout = findViewById(R.id.coordinator);
        navigationView = findViewById(R.id.navigation_view);
        drawerLayout = findViewById(R.id.drawer_layout);
        tabLayout = findViewById(R.id.download_list_tabs);
        viewPager = findViewById(R.id.download_list_viewpager);
        fab = findViewById(R.id.add_fab);

        toolbar.setTitle(R.string.app_name);
        /* Disable elevation for portrait mode */
        if (!Utils.isTwoPane(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            toolbar.setElevation(0);
        setSupportActionBar(toolbar);

        if (drawerLayout != null) {
            toggle = new ActionBarDrawerToggle(this,
                    drawerLayout,
                    toolbar,
                    R.string.open_navigation_drawer,
                    R.string.close_navigation_drawer);
            drawerLayout.addDrawerListener(toggle);
        }

        adapter = new DownloadListPagerAdapter(getApplicationContext(), getSupportFragmentManager());
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(DownloadListPagerAdapter.NUM_FRAGMENTS);
        tabLayout.setupWithViewPager(viewPager);

        fab.setOnClickListener((View v) -> startActivity(new Intent(this, AddDownloadActivity.class)));
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putBoolean(TAG_PERM_DIALOG_IS_SHOW, permDialogIsShow);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);

        if (toggle != null)
            toggle.syncState();
    }
}
