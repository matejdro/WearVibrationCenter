package com.matejdro.wearvibrationcenter.ui;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.matejdro.wearutils.companionnotice.WearCompanionPhoneActivity;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.common.CommPaths;
import com.matejdro.wearvibrationcenter.notification.NotificationService;
import com.matejdro.wearvibrationcenter.preferences.PerAppSettings;

public class MainActivity extends WearCompanionPhoneActivity implements NavigationView.OnNavigationItemSelectedListener, AppPickerFragment.AppPickerCallback, TitleUtils.TitledActivity, FragmentManager.OnBackStackChangedListener {
    private ActionBarDrawerToggle actionBarDrawerToggle;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        navigationView = (NavigationView) findViewById(R.id.navigation_view);
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        setSupportActionBar(toolbar);

        actionBarDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open_drawer, R.string.close_drawer);
        drawerLayout.addDrawerListener(actionBarDrawerToggle);

        navigationView.setNavigationItemSelectedListener(this);

        getFragmentManager().addOnBackStackChangedListener(this);

        if (savedInstanceState == null)
        {
            onNavigationItemSelected(navigationView.getMenu().findItem(R.id.home));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        checkServiceRunning();
    }


    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);
        actionBarDrawerToggle.syncState();
    }

    private void checkServiceRunning() {
        if (NotificationService.active) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.service_not_running).setNegativeButton(R.string.cancel, null);
        builder.setMessage(R.string.service_not_running_description);
        builder.setPositiveButton(R.string.open_settings, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            }
        });


        builder.show();
    }

    private void switchToFragment(Fragment fragment)
    {
        // Clear back stack
        for (int i = 0; i < getFragmentManager().getBackStackEntryCount(); i++) {
            getFragmentManager().popBackStack();
        }

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.content_frame, fragment)
                .commit();

        getFragmentManager().executePendingTransactions();

        onFragmentSwitched(fragment);
    }

    private void switchToFragmentWithBackStack(Fragment fragment)
    {
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.content_frame, fragment)
                .addToBackStack(null)
                .commit();

        getFragmentManager().executePendingTransactions();
    }


    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        navigationView.setCheckedItem(item.getItemId());

        if (item.getItemId() == R.id.home)
        {
            switchToFragment(new HomeFragment());
        }
        else if (item.getItemId() == R.id.global_settings)
        {
            switchToFragment(new GlobalSettingsFragment());
        }
        else if (item.getItemId() == R.id.user_apps)
        {
            switchToFragment(AppPickerFragment.newInstance(false));
        }
        else if (item.getItemId() == R.id.system_apps)
        {
            switchToFragment(AppPickerFragment.newInstance(true));
        }
        else if (item.getItemId() == R.id.default_per_app)
        {
            switchToFragment(PerAppSettingsFragment.newInstance(PerAppSettings.VIRTUAL_APP_DEFAULT_SETTINGS,
                    getString(R.string.default_app_settings)));
        }

        drawerLayout.closeDrawers();

        return true;
    }

    private void onFragmentSwitched(Fragment newFragment)
    {
        if (newFragment instanceof TitleUtils.TitledFragment)
        {
            updateTitle(((TitleUtils.TitledFragment) newFragment).getTitle());
        }
    }

    private void switchToAppFragment(String appPackage, String appLabel)
    {
        switchToFragmentWithBackStack(PerAppSettingsFragment.newInstance(appPackage, appLabel));
    }

    @Override
    public void onAppPicked(String appPackage, String appLabel) {
        switchToAppFragment(appPackage, appLabel);
    }

    @Override
    public void updateTitle(CharSequence newTitle) {
        //noinspection ConstantConditions
        getSupportActionBar().setTitle(newTitle);
    }

    @Override
    public void onBackStackChanged() {
        onFragmentSwitched(getFragmentManager().findFragmentById(R.id.content_frame));
    }

    @Override
    public String getWatchAppPresenceCapability() {
        return CommPaths.WATCH_APP_CAPABILITY;
    }
}