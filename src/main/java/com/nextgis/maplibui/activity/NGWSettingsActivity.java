/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2012-2015. NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.maplibui.activity;


import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.INGWLayer;
import com.nextgis.maplib.datasource.ngw.SyncAdapter;
import com.nextgis.maplib.map.Layer;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.util.AccountUtil;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.api.ILayerUI;
import com.nextgis.maplibui.fragment.NGWSettingsFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.nextgis.maplib.util.Constants.NGW_ACCOUNT_TYPE;
import static com.nextgis.maplib.util.Constants.NOT_FOUND;
import static com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SYNC_PERIOD;
import static com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SYNC_PERIOD_SEC_LONG;


public class NGWSettingsActivity
        extends PreferenceActivity
        implements OnAccountsUpdateListener
{
    protected static final String ACCOUNT_ACTION = "com.nextgis.maplibui.ACCOUNT";

    protected AccountManager mAccountManager;
    protected final Handler mHandler = new Handler();
    protected String mAction;

    protected static String mDeleteAccountSummary;
    protected static String mDeleteAccountWarnMsg;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setStrings();

        if (null == mAccountManager) {
            mAccountManager = AccountManager.get(this.getApplicationContext());
        }

        ViewGroup root = ((ViewGroup) findViewById(android.R.id.content));
        if (null != root) {
            View content = root.getChildAt(0);
            if (null != content) {
                LinearLayout toolbarContainer =
                        (LinearLayout) View.inflate(this, R.layout.activity_settings, null);

                root.removeAllViews();
                toolbarContainer.addView(content);
                root.addView(toolbarContainer);

                Toolbar toolbar = (Toolbar) toolbarContainer.findViewById(R.id.main_toolbar);
                toolbar.getBackground().setAlpha(255);
                toolbar.setTitle(getTitle());
                toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
                toolbar.setNavigationOnClickListener(
                        new View.OnClickListener()
                        {
                            @Override
                            public void onClick(View v)
                            {
                                NGWSettingsActivity.this.finish();
                            }
                        });
            }
        }

        createView();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onBuildHeaders(List<Header> target)
    {
        if (null == mAccountManager) {
            mAccountManager = AccountManager.get(this.getApplicationContext());
        }

        Header header;

        if (null != mAccountManager) {
            for (Account account : mAccountManager.getAccountsByType(
                    NGW_ACCOUNT_TYPE)) {
                header = new Header();
                header.title = account.name;
                header.fragment = NGWSettingsFragment.class.getName();

                Bundle bundle = new Bundle();
                bundle.putParcelable("account", account);
                header.fragmentArguments = bundle;
                target.add(header);
            }

            //add "Add account" header
            header = new Header();
            header.title = getString(R.string.add_account);
            header.summary = getString(R.string.add_account_summary);
            header.intent = new Intent(this, NGWLoginActivity.class);
            target.add(header);
        }
    }


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public Header onGetInitialHeader()
    {
        if (null != mAccountManager) {
            if (mAccountManager.getAccountsByType(NGW_ACCOUNT_TYPE).length > 0) {
                return super.onGetInitialHeader();
            }
        }
        //in Android 5.0 or higher need to have one header with fragment
        Header header = new Header();
        header.fragment = NGWSettingsFragment.class.getName();
        return header;
    }


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public Header onGetNewHeader()
    {
        if (!onIsHidingHeaders()) {
            return onGetInitialHeader();
        }
        return super.onGetNewHeader();
    }


    @Override
    public void onResume()
    {
        super.onResume();
        if (null != mAccountManager) {
            mAccountManager.addOnAccountsUpdatedListener(this, mHandler, true);
        }
    }


    @Override
    public void onPause()
    {
        if (null != mAccountManager) {
            mAccountManager.removeOnAccountsUpdatedListener(this);
        }
        super.onPause();
    }


    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    protected boolean isValidFragment(String fragmentName)
    {
        return NGWSettingsFragment.class.getName().equals(fragmentName);
    }


    // for overriding in a subclass
    protected void setStrings()
    {
        mDeleteAccountSummary = getString(R.string.delete_account_summary);
        mDeleteAccountWarnMsg = getString(R.string.delete_account_warn_msg);
    }


    // for overriding in a subclass
    protected void createView()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(this);
            mAction = getIntent().getAction();
            if (mAction != null && mAction.equals(ACCOUNT_ACTION)) {
                Account account = getIntent().getParcelableExtra("account");
                fillAccountPreferences(screen, account);
            } else {
                fillPreferences(screen);
            }

            setPreferenceScreen(screen);
        }
    }


    protected void fillPreferences(PreferenceScreen screen)
    {
        if (null != mAccountManager) {
            for (Account account : mAccountManager.getAccountsByType(
                    NGW_ACCOUNT_TYPE)) {
                Preference preference = new Preference(this);
                preference.setTitle(account.name);

                Bundle bundle = new Bundle();
                bundle.putParcelable("account", account);
                Intent intent = new Intent(this, NGWSettingsActivity.class);
                intent.putExtras(bundle);
                intent.setAction(ACCOUNT_ACTION);

                preference.setIntent(intent);

                if (null != screen) {
                    screen.addPreference(preference);
                }
            }
            //add "Add account" preference
            Preference preference = new Preference(this);
            preference.setTitle(R.string.add_account);
            preference.setSummary(R.string.add_account_summary);
            Intent intent = new Intent(this, NGWLoginActivity.class);
            preference.setIntent(intent);

            if (null != screen) {
                screen.addPreference(preference);
            }
        }
    }


    public void fillAccountPreferences(
            PreferenceScreen screen,
            final Account account)
    {
        final IGISApplication application = (IGISApplication) getApplicationContext();

        // add sync settings group
        PreferenceCategory syncCategory = new PreferenceCategory(this);
        syncCategory.setTitle(R.string.sync);
        screen.addPreference(syncCategory);

        // add auto sync property
        addAutoSyncProperty(account, application, syncCategory);

        // add time for periodic sync
        addPeriodicSyncTime(account, application, syncCategory);

        // add account layers
        addAccountLayers(screen, account);

        // add actions group
        PreferenceCategory actionCategory = new PreferenceCategory(this);
        actionCategory.setTitle(R.string.actions);
        screen.addPreference(actionCategory);

        // add "Edit account" action
        addEditAccountAction(account, actionCategory);

        // add delete account action
        addDeleteAccountAction(account, actionCategory);
    }


    // for overriding in a subclass
    protected void addAutoSyncProperty(
            final Account account,
            final IGISApplication application,
            PreferenceCategory syncCategory)
    {
        SharedPreferences sharedPreferences = getSharedPreferences(
                Constants.PREFERENCES, Context.MODE_MULTI_PROCESS);

        CheckBoxPreference enablePeriodicSync = new CheckBoxPreference(this);
        enablePeriodicSync.setTitle(R.string.auto_sync);

        boolean isAccountSyncEnabled = isAccountSyncEnabled(account, application.getAuthority());
        enablePeriodicSync.setChecked(isAccountSyncEnabled);
        enablePeriodicSync.setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener()
                {
                    @Override
                    public boolean onPreferenceChange(
                            Preference preference,
                            Object newValue)
                    {
                        boolean isChecked = (boolean) newValue;
                        setAccountSyncEnabled(account, application.getAuthority(), isChecked);
                        return true;
                    }
                });

        long timeStamp =
                sharedPreferences.getLong(SettingsConstants.KEY_PREF_LAST_SYNC_TIMESTAMP, 0);
        if (isAccountSyncEnabled && timeStamp > 0) {
            enablePeriodicSync.setSummary(
                    getString(R.string.last_sync_time) + ": " +
                    new SimpleDateFormat().format(new Date(timeStamp)));
        } else {
            enablePeriodicSync.setSummary(R.string.auto_sync_summary);
        }
        syncCategory.addPreference(enablePeriodicSync);
    }


    // for overriding in a subclass
    protected boolean isAccountSyncEnabled(
            Account account,
            String authority)
    {
        return ContentResolver.getSyncAutomatically(account, authority);
    }


    // for overriding in a subclass
    protected void setAccountSyncEnabled(
            Account account,
            String authority,
            boolean isEnabled)
    {
        ContentResolver.setSyncAutomatically(account, authority, isEnabled);
    }


    // for overriding in a subclass
    protected void addPeriodicSyncTime(
            final Account account,
            final IGISApplication application,
            PreferenceCategory syncCategory)
    {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this);
        String prefValue = "" + sharedPreferences.getLong(KEY_PREF_SYNC_PERIOD_SEC_LONG, NOT_FOUND);

        final CharSequence[] keys = {
                getString(R.string.system_default),
                getString(R.string.five_minutes),
                getString(R.string.ten_minutes),
                getString(R.string.fifteen_minutes),
                getString(R.string.thirty_minutes),
                getString(R.string.one_hour),
                getString(R.string.two_hours)};
        final CharSequence[] values = {"" + NOT_FOUND, "300", "600", "900", "1800", "3600", "7200"};

        final ListPreference timeInterval = new ListPreference(this);
        timeInterval.setKey(KEY_PREF_SYNC_PERIOD);
        timeInterval.setTitle(R.string.sync_interval);
        timeInterval.setDialogTitle(R.string.sync_set_interval);
        timeInterval.setEntries(keys);
        timeInterval.setEntryValues(values);

        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(prefValue)) {
                timeInterval.setValueIndex(i);
                timeInterval.setSummary(keys[i]);
                break;
            }
        }

        timeInterval.setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener()
                {
                    @Override
                    public boolean onPreferenceChange(
                            Preference preference,
                            Object newValue)
                    {
                        long interval = Long.parseLong((String) newValue);

                        for (int i = 0; i < values.length; i++) {
                            if (values[i].equals(newValue)) {
                                timeInterval.setSummary(keys[i]);
                                break;
                            }
                        }

                        if (interval == NOT_FOUND) {
                            ContentResolver.removePeriodicSync(
                                    account, application.getAuthority(), Bundle.EMPTY);
                        } else {
                            ContentResolver.addPeriodicSync(
                                    account, application.getAuthority(), Bundle.EMPTY, interval);
                        }

                        //set KEY_PREF_SYNC_PERIOD_SEC_LONG
                        return sharedPreferences.edit()
                                .putLong(KEY_PREF_SYNC_PERIOD_SEC_LONG, interval)
                                .commit();
                    }
                });

        syncCategory.addPreference(timeInterval);
    }


    // for overriding in a subclass
    protected void addAccountLayers(
            PreferenceScreen screen,
            Account account)
    {
        List<INGWLayer> layers = getLayersForAccount(this, account);
        if (!layers.isEmpty()) {

            PreferenceCategory layersCategory = null;

            for (INGWLayer layer : layers) {

                if (!(layer instanceof NGWVectorLayer)) {
                    continue;
                }

                final NGWVectorLayer ngwLayer = (NGWVectorLayer) layer;

                if(!ngwLayer.isSyncable()){
                    continue;
                }

                CheckBoxPreference layerSync = new CheckBoxPreference(this);
                layerSync.setTitle(ngwLayer.getName());
                layerSync.setChecked(0 == (ngwLayer.getSyncType() & Constants.SYNC_NONE));
                //layerSync.setKey("" + ngwLayer.getId());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB &&
                    ngwLayer instanceof ILayerUI) {
                    ILayerUI layerUI = (ILayerUI) ngwLayer;
                    layerSync.setIcon(layerUI.getIcon());
                }

                layerSync.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener()
                        {
                            @Override
                            public boolean onPreferenceChange(
                                    Preference preference,
                                    Object newValue)
                            {
                                boolean isChecked = (boolean) newValue;
                                if (isChecked) {
                                    ngwLayer.setSyncType(Constants.SYNC_ALL);

                                } else {
                                    ngwLayer.setSyncType(Constants.SYNC_NONE);
                                }
                                ngwLayer.save();
                                return true;
                            }
                        });

                if(null == layersCategory){
                    layersCategory = new PreferenceCategory(this);
                    layersCategory.setTitle(R.string.sync_layers);
                    layersCategory.setSummary(R.string.sync_layers_summary);
                    screen.addPreference(layersCategory);
                }
                layersCategory.addPreference(layerSync);
            }
        }
    }


    // for overriding in a subclass
    protected void addEditAccountAction(
            final Account account,
            PreferenceCategory actionCategory)
    {
        Preference preferenceEdit = new Preference(this);
        preferenceEdit.setTitle(R.string.edit_account);
        preferenceEdit.setSummary(R.string.edit_account_summary);

        AccountManager accountManager = AccountManager.get(getApplicationContext());
        String url = accountManager.getUserData(account, "url");
        String login = accountManager.getUserData(account, "login");

        Intent intent = new Intent(this, NGWLoginActivity.class);
        intent.putExtra(NGWLoginActivity.FOR_NEW_ACCOUNT, false);
        intent.putExtra(NGWLoginActivity.ACCOUNT_URL_TEXT, url);
        intent.putExtra(NGWLoginActivity.ACCOUNT_LOGIN_TEXT, login);
        intent.putExtra(NGWLoginActivity.CHANGE_ACCOUNT_URL, false);
        intent.putExtra(NGWLoginActivity.CHANGE_ACCOUNT_LOGIN, true);
        preferenceEdit.setIntent(intent);

        actionCategory.addPreference(preferenceEdit);
    }


    // for overriding in a subclass
    protected void addDeleteAccountAction(
            final Account account,
            PreferenceCategory actionCategory)
    {
        final boolean[] wasCurrentSyncActive = {false};
        final IGISApplication application = (IGISApplication) getApplicationContext();

        final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
        {
            final ProgressDialog mProgressDialog = new ProgressDialog(NGWSettingsActivity.this);


            @Override
            public void onReceive(
                    Context context,
                    Intent intent)
            {
                Log.d(Constants.TAG, "NGWSettingsActivity - broadcastReceiver.onReceive()");

                if (!mProgressDialog.isShowing()) {
                    Log.d(Constants.TAG, "NGWSettingsActivity - show ProgressDialog");

                    mProgressDialog.setTitle(R.string.waiting);
                    mProgressDialog.setMessage(getString(R.string.wait_sync_stopping));
                    mProgressDialog.setIndeterminate(true);
                    mProgressDialog.show();
                }

                String action;
                if (wasCurrentSyncActive[0]) {
                    if (null == intent) {
                        return;
                    }
                    action = intent.getAction();

                } else {
                    action = SyncAdapter.SYNC_CANCELED;
                }

                switch (action) {
                    case SyncAdapter.SYNC_START:
                        break;

                    case SyncAdapter.SYNC_FINISH:
                        break;

                    case SyncAdapter.SYNC_CANCELED:
                        Log.d(Constants.TAG, "NGWSettingsActivity - sync status - SYNC_CANCELED");

                        wasCurrentSyncActive[0] = false;

                        AccountManager accountManager = AccountManager.get(
                                NGWSettingsActivity.this.getApplicationContext());
                        accountManager.removeAccount(
                                account, null, new Handler());

                        Log.d(Constants.TAG, "NGWSettingsActivity - account is removed");

                        deleteAccountLayers(application, account);
                        Log.d(Constants.TAG, "NGWSettingsActivity - account layers are deleted");

                        unregisterReceiver(this);
                        mProgressDialog.dismiss();

                        onDeleteAccount();
                        break;

                    case SyncAdapter.SYNC_CHANGES:
                        break;
                }
            }
        };

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(NGWSettingsActivity.this);
        dialogBuilder.setIcon(R.drawable.ic_action_warning)
                .setTitle(R.string.delete_account_ask)
                .setMessage(mDeleteAccountWarnMsg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.ok, new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which)
                            {
                                Log.d(Constants.TAG, "NGWSettingsActivity - OK pressed");

                                IntentFilter intentFilter = new IntentFilter();
                                intentFilter.addAction(SyncAdapter.SYNC_CANCELED);
                                registerReceiver(broadcastReceiver, intentFilter);

                                wasCurrentSyncActive[0] = AccountUtil.isSyncActive(
                                        account, application.getAuthority());

                                ContentResolver.removePeriodicSync(
                                        account, application.getAuthority(), Bundle.EMPTY);
                                ContentResolver.setSyncAutomatically(
                                        account, application.getAuthority(), false);

                                ContentResolver.cancelSync(account, application.getAuthority());

                                Log.d(
                                        Constants.TAG,
                                        "NGWSettingsActivity - ContentResolver.cancelSync() is performed");

                                broadcastReceiver.onReceive(NGWSettingsActivity.this, null);
                            }
                        });

        Preference preferenceDelete = new Preference(this);
        preferenceDelete.setTitle(R.string.delete_account);
        preferenceDelete.setSummary(mDeleteAccountSummary);

        if (actionCategory.addPreference(preferenceDelete)) {
            preferenceDelete.setOnPreferenceClickListener(
                    new Preference.OnPreferenceClickListener()
                    {
                        public boolean onPreferenceClick(Preference preference)
                        {
                            dialogBuilder.show();
                            return true;
                        }
                    });
        }
    }


    public static void updateAccountLayersCacheData(
            Context context,
            Account account)
    {
        List<INGWLayer> layers = getLayersForAccount(context, account);

        for (INGWLayer layer : layers) {
            layer.setAccountCacheData();
        }
    }


    protected void deleteAccountLayers(
            IGISApplication application,
            Account account)
    {
        List<INGWLayer> layers = getLayersForAccount(this, account);

        for (INGWLayer layer : layers) {
            ((Layer) layer).delete();
        }

        application.getMap().save();

        if (null != mOnDeleteAccountListener) {
            mOnDeleteAccountListener.onDeleteAccount(account);
        }
    }


    protected static List<INGWLayer> getLayersForAccount(
            Context context,
            Account account)
    {
        List<INGWLayer> out = new ArrayList<>();

        IGISApplication application = (IGISApplication) context.getApplicationContext();
        MapContentProviderHelper.getLayersByAccount(application.getMap(), account.name, out);

        return out;
    }


    // for overriding in a subclass
    protected void onDeleteAccount()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            finish();
        } else {
            if (onIsHidingHeaders()) {
                finish();
            } else {
                invalidateHeaders();
            }
        }
    }


    @Override
    public void onAccountsUpdated(Account[] accounts)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            if (mAction == null || !mAction.equals(ACCOUNT_ACTION)) {
                PreferenceScreen screen = getPreferenceScreen();
                if (null != screen) {
                    screen.removeAll();
                    fillPreferences(screen);
                    //onContentChanged();
                }
            }
        } else {
            invalidateHeaders();
            setSelection(0);
        }
    }


    protected OnDeleteAccountListener mOnDeleteAccountListener;


    public void setOnDeleteAccountListener(OnDeleteAccountListener onDeleteAccountListener)
    {
        mOnDeleteAccountListener = onDeleteAccountListener;
    }


    public interface OnDeleteAccountListener
    {
        public void onDeleteAccount(Account account);
    }
}