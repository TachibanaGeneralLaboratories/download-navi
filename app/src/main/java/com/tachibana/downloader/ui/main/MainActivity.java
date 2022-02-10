/*
 * Copyright (C) 2018-2022 Tachibana General Laboratories, LLC
 * Copyright (C) 2018-2022 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.tachibana.downloader.ui.main;

import android.Manifest;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.RefactoredDefaultItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.expandable.RecyclerViewExpandableItemManager;
import com.tachibana.downloader.ui.BatteryOptimizationDialog;
import com.tachibana.downloader.ui.PermissionDeniedDialog;
import com.tachibana.downloader.R;
import com.tachibana.downloader.core.RepositoryHelper;
import com.tachibana.downloader.core.model.DownloadEngine;
import com.tachibana.downloader.core.settings.SettingsRepository;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.receiver.NotificationReceiver;
import com.tachibana.downloader.service.DownloadService;
import com.tachibana.downloader.ui.BaseAlertDialog;
import com.tachibana.downloader.ui.adddownload.AddDownloadActivity;
import com.tachibana.downloader.ui.browser.BrowserActivity;
import com.tachibana.downloader.ui.main.drawer.DrawerExpandableAdapter;
import com.tachibana.downloader.ui.main.drawer.DrawerGroup;
import com.tachibana.downloader.ui.main.drawer.DrawerGroupItem;
import com.tachibana.downloader.ui.settings.SettingsActivity;

import java.util.List;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class MainActivity extends AppCompatActivity
{
    @SuppressWarnings("unused")
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String TAG_ABOUT_DIALOG = "about_dialog";
    private static final String TAG_PERM_DENIED_DIALOG = "perm_denied_dialog";
    private static final String TAG_BATTERY_DIALOG = "battery_dialog";

    /* Android data binding doesn't work with layout aliases */
    private CoordinatorLayout coordinatorLayout;
    private Toolbar toolbar;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle toggle;
    private RecyclerView drawerItemsList;
    private LinearLayoutManager layoutManager;
    private DrawerExpandableAdapter drawerAdapter;
    private RecyclerView.Adapter wrappedDrawerAdapter;
    private RecyclerViewExpandableItemManager drawerItemManager;

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private DownloadListPagerAdapter pagerAdapter;
    private DownloadsViewModel fragmentViewModel;
    private FloatingActionButton fab;
    private SearchView searchView;
    private DownloadEngine engine;
    private SettingsRepository pref;
    protected CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private BaseAlertDialog aboutDialog;
    private PermissionDeniedDialog permDeniedDialog;
    private BatteryOptimizationDialog batteryDialog;

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

        ViewModelProvider provider = new ViewModelProvider(this);
        fragmentViewModel = provider.get(DownloadsViewModel.class);
        dialogViewModel = provider.get(BaseAlertDialog.SharedViewModel.class);
        FragmentManager fm = getSupportFragmentManager();
        aboutDialog = (BaseAlertDialog)fm.findFragmentByTag(TAG_ABOUT_DIALOG);
        permDeniedDialog = (PermissionDeniedDialog)fm.findFragmentByTag(TAG_PERM_DENIED_DIALOG);
        batteryDialog = (BatteryOptimizationDialog)fm.findFragmentByTag(TAG_BATTERY_DIALOG);

        if (!Utils.checkStoragePermission(this) && permDeniedDialog == null) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        setContentView(R.layout.activity_main);

        pref = RepositoryHelper.getSettingsRepository(getApplicationContext());
        Utils.disableBrowserFromSystem(getApplicationContext(), pref.browserDisableFromSystem());
        Utils.enableBrowserLauncherIcon(getApplicationContext(), pref.browserLauncherIcon());

        engine = DownloadEngine.getInstance(getApplicationContext());

        initLayout();
        engine.restoreDownloads();

        if (Utils.shouldShowBatteryOptimizationDialog(this)) {
            showBatteryOptimizationDialog();
        }
    }

    private final ActivityResultLauncher<String> storagePermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (!isGranted && Utils.shouldRequestStoragePermission(this)) {
                    FragmentManager fm = getSupportFragmentManager();
                    if (fm.findFragmentByTag(TAG_PERM_DENIED_DIALOG) == null) {
                        permDeniedDialog = PermissionDeniedDialog.newInstance();
                        FragmentTransaction ft = fm.beginTransaction();
                        ft.add(permDeniedDialog, TAG_PERM_DENIED_DIALOG);
                        ft.commitAllowingStateLoss();
                    }
                }
            });

    private void initLayout()
    {
        toolbar = findViewById(R.id.toolbar);
        coordinatorLayout = findViewById(R.id.coordinator);
        navigationView = findViewById(R.id.navigation_view);
        drawerLayout = findViewById(R.id.drawer_layout);
        tabLayout = findViewById(R.id.download_list_tabs);
        viewPager = findViewById(R.id.download_list_viewpager);
        fab = findViewById(R.id.add_fab);
        drawerItemsList = findViewById(R.id.drawer_items_list);
        layoutManager = new LinearLayoutManager(this);

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
        initDrawer();
        fragmentViewModel.resetSearch();

        pagerAdapter = new DownloadListPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(DownloadListPagerAdapter.NUM_FRAGMENTS);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    switch (position) {
                        case DownloadListPagerAdapter.QUEUED_FRAG_POS:
                            tab.setText(R.string.fragment_title_queued);
                            break;
                        case DownloadListPagerAdapter.COMPLETED_FRAG_POS:
                            tab.setText(R.string.fragment_title_completed);
                            break;
                    }
                }
        ).attach();

        fab.setOnClickListener((v) -> startActivity(new Intent(this, AddDownloadActivity.class)));
    }

    private void initDrawer()
    {
        drawerItemManager = new RecyclerViewExpandableItemManager(null);
        drawerItemManager.setDefaultGroupsExpandedState(false);
        drawerItemManager.setOnGroupCollapseListener((groupPosition, fromUser, payload) -> {
            if (fromUser)
                saveGroupExpandState(groupPosition, false);
        });
        drawerItemManager.setOnGroupExpandListener((groupPosition, fromUser, payload) -> {
            if (fromUser)
                saveGroupExpandState(groupPosition, true);
        });
        GeneralItemAnimator animator = new RefactoredDefaultItemAnimator();
        /*
         * Change animations are enabled by default since support-v7-recyclerview v22.
         * Need to disable them when using animation indicator.
         */
        animator.setSupportsChangeAnimations(false);

        List<DrawerGroup> groups = Utils.getNavigationDrawerItems(this,
                PreferenceManager.getDefaultSharedPreferences(this));
        drawerAdapter = new DrawerExpandableAdapter(groups, drawerItemManager, this::onDrawerItemSelected);
        wrappedDrawerAdapter = drawerItemManager.createWrappedAdapter(drawerAdapter);
        onDrawerGroupsCreated();

        drawerItemsList.setLayoutManager(layoutManager);
        drawerItemsList.setAdapter(wrappedDrawerAdapter);
        drawerItemsList.setItemAnimator(animator);
        drawerItemsList.setHasFixedSize(false);

        drawerItemManager.attachRecyclerView(drawerItemsList);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);

        if (toggle != null)
            toggle.syncState();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAlertDialog();
        subscribeSettingsChanged();
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        disposables.clear();
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (event.dialogTag == null) {
                        return;
                    }
                    if (event.dialogTag.equals(TAG_ABOUT_DIALOG)) {
                        switch (event.type) {
                            case NEGATIVE_BUTTON_CLICKED:
                                openChangelogLink();
                                break;
                            case DIALOG_SHOWN:
                                initAboutDialog();
                                break;
                        }
                    } else if (event.dialogTag.equals(TAG_PERM_DENIED_DIALOG)) {
                        if (event.type != BaseAlertDialog.EventType.DIALOG_SHOWN) {
                            permDeniedDialog.dismiss();
                        }
                        if (event.type == BaseAlertDialog.EventType.NEGATIVE_BUTTON_CLICKED) {
                            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                        }
                    } else if (event.dialogTag.equals(TAG_BATTERY_DIALOG)) {
                        if (event.type != BaseAlertDialog.EventType.DIALOG_SHOWN) {
                            batteryDialog.dismiss();
                            pref.askDisableBatteryOptimization(false);
                        }
                        if (event.type == BaseAlertDialog.EventType.POSITIVE_BUTTON_CLICKED) {
                            Utils.requestDisableBatteryOptimization(this);
                        }
                    }
                });
        disposables.add(d);
    }

    private void showBatteryOptimizationDialog() {
        var fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(TAG_BATTERY_DIALOG) == null) {
            batteryDialog = BatteryOptimizationDialog.newInstance();
            var ft = fm.beginTransaction();
            ft.add(batteryDialog, TAG_BATTERY_DIALOG);
            ft.commitAllowingStateLoss();
        }
    }

    private void subscribeSettingsChanged() {
        invalidateOptionsMenu();
        disposables.add(pref.observeSettingsChanged()
                .subscribe((key) -> {
                    if (key.equals(getString(R.string.pref_key_browser_hide_menu_icon))) {
                        invalidateOptionsMenu();
                    }
                }));
    }

    private void onDrawerGroupsCreated()
    {
        for (int pos = 0; pos < drawerAdapter.getGroupCount(); pos++) {
            DrawerGroup group = drawerAdapter.getGroup(pos);
            if (group == null)
                return;

            Resources res = getResources();
            if (group.id == res.getInteger(R.integer.drawer_category_id)) {
                fragmentViewModel.setCategoryFilter(
                        Utils.getDrawerGroupCategoryFilter(this, group.getSelectedItemId()), false);

            } else if (group.id == res.getInteger(R.integer.drawer_status_id)) {
                fragmentViewModel.setStatusFilter(
                        Utils.getDrawerGroupStatusFilter(this, group.getSelectedItemId()), false);

            } else if (group.id == res.getInteger(R.integer.drawer_date_added_id)) {
                fragmentViewModel.setDateAddedFilter(
                        Utils.getDrawerGroupDateAddedFilter(this, group.getSelectedItemId()), false);

            } else if (group.id == res.getInteger(R.integer.drawer_sorting_id)) {
                fragmentViewModel.setSort(Utils.getDrawerGroupItemSorting(this, group.getSelectedItemId()), false);
            }

            applyExpandState(group, pos);
        }
    }

    private void applyExpandState(DrawerGroup group, int pos)
    {
        if (group.getDefaultExpandState())
            drawerItemManager.expandGroup(pos);
        else
            drawerItemManager.collapseGroup(pos);
    }

    private void saveGroupExpandState(int groupPosition, boolean expanded)
    {
        DrawerGroup group = drawerAdapter.getGroup(groupPosition);
        if (group == null)
            return;

        Resources res = getResources();
        String prefKey = null;
        if (group.id == res.getInteger(R.integer.drawer_category_id))
            prefKey = getString(R.string.drawer_category_is_expanded);

        else if (group.id == res.getInteger(R.integer.drawer_status_id))
            prefKey = getString(R.string.drawer_status_is_expanded);

        else if (group.id == res.getInteger(R.integer.drawer_date_added_id))
            prefKey = getString(R.string.drawer_time_is_expanded);

        else if (group.id == res.getInteger(R.integer.drawer_sorting_id))
            prefKey = getString(R.string.drawer_sorting_is_expanded);

        if (prefKey != null)
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putBoolean(prefKey, expanded)
                    .apply();
    }

    private void onDrawerItemSelected(DrawerGroup group, DrawerGroupItem item)
    {
        Resources res = getResources();
        String prefKey = null;
        if (group.id == res.getInteger(R.integer.drawer_category_id)) {
            prefKey = getString(R.string.drawer_category_selected_item);
            fragmentViewModel.setCategoryFilter(
                    Utils.getDrawerGroupCategoryFilter(this, item.id), true);

        } else if (group.id == res.getInteger(R.integer.drawer_status_id)) {
            prefKey = getString(R.string.drawer_status_selected_item);
            fragmentViewModel.setStatusFilter(
                    Utils.getDrawerGroupStatusFilter(this, item.id), true);

        } else if (group.id == res.getInteger(R.integer.drawer_date_added_id)) {
            prefKey = getString(R.string.drawer_time_selected_item);
            fragmentViewModel.setDateAddedFilter(
                    Utils.getDrawerGroupDateAddedFilter(this, item.id), true);

        } else if (group.id == res.getInteger(R.integer.drawer_sorting_id)) {
            prefKey = getString(R.string.drawer_sorting_selected_item);
            fragmentViewModel.setSort(Utils.getDrawerGroupItemSorting(this, item.id), true);
        }

        if (prefKey != null)
            saveSelectionState(prefKey, item);

        if (drawerLayout != null)
            drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void saveSelectionState(String prefKey, DrawerGroupItem item)
    {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putLong(prefKey, item.id)
                .apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.main, menu);

        searchView = (SearchView)menu.findItem(R.id.search).getActionView();
        initSearch();

        return true;
    }

    private void initSearch()
    {
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setOnCloseListener(() -> {
            fragmentViewModel.resetSearch();

            return false;
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener()
        {
            @Override
            public boolean onQueryTextSubmit(String query)
            {
                fragmentViewModel.setSearchQuery(query);
                /* Submit the search will hide the keyboard */
                searchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText)
            {
                fragmentViewModel.setSearchQuery(newText);

                return true;
            }
        });
        searchView.setQueryHint(getString(R.string.search));
        SearchManager searchManager = (SearchManager)getSystemService(Context.SEARCH_SERVICE);
        /* Assumes current activity is the searchable activity */
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.browser_menu).setVisible(!pref.browserHideMenuIcon());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int itemId = item.getItemId();
        if (itemId == R.id.pause_all_menu) {
            pauseAll();
        } else if (itemId == R.id.resume_all_menu) {
            resumeAll();
        } else if (itemId == R.id.settings_menu) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (itemId == R.id.about_menu) {
            showAboutDialog();
        } else if (itemId == R.id.shutdown_app_menu) {
            closeOptionsMenu();
            shutdown();
        } else if (itemId == R.id.browser_menu) {
            startActivity(new Intent(this, BrowserActivity.class));
        }

        return true;
    }

    private void pauseAll()
    {
        engine.pauseAllDownloads();
    }

    private void resumeAll()
    {
        engine.resumeDownloads(false);
    }

    private void showAboutDialog()
    {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(TAG_ABOUT_DIALOG) == null) {
            aboutDialog = BaseAlertDialog.newInstance(
                    getString(R.string.about_title),
                    null,
                    R.layout.dialog_about,
                    getString(R.string.ok),
                    getString(R.string.about_changelog),
                    null,
                    true);
            aboutDialog.show(fm, TAG_ABOUT_DIALOG);
        }
    }

    private void initAboutDialog()
    {
        if (aboutDialog == null)
            return;

        Dialog dialog = aboutDialog.getDialog();
        if (dialog != null) {
            TextView versionTextView = dialog.findViewById(R.id.about_version);
            TextView descriptionTextView = dialog.findViewById(R.id.about_description);
            String versionName = Utils.getAppVersionName(this);
            if (versionName != null)
                versionTextView.setText(versionName);
            descriptionTextView.setText(Html.fromHtml(getString(R.string.about_description)));
            descriptionTextView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private void openChangelogLink()
    {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(getString(R.string.about_changelog_link)));
        startActivity(i);
    }

    public void shutdown()
    {
        Intent i = new Intent(getApplicationContext(), DownloadService.class);
        i.setAction(DownloadService.ACTION_SHUTDOWN);
        startService(i);
        finish();
    }
}
