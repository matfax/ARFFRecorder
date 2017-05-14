package de.uni_passau.fim.arffrecorder;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.v7.app.ActionBar;
import android.util.Log;

import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class ArffRecorderActivity extends AppCompatPreferenceActivity {

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        Boolean serviceEnabled = preferences.getBoolean(ArffGlobals.ARFF_ENABLE_SERVICE,
                ArffGlobals.ARFF_DEFAULT_ENABLE_SERVICE);
        if (serviceEnabled) {
            Log.i(getLocalClassName(), "Service is to be started.");
            Intent serviceIntent = new Intent(ArffRecorderActivity.this, ArffService.class);
            startService(serviceIntent);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName);
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            build();
        }

        @Override
        public void onResume() {
            super.onResume();
            // Refresh the UI in case the service has been stopped via the notification.
            refresh();
        }

        /**
         * Refresh the UI without closing the fragment.
         */
        private void refresh() {
            setPreferenceScreen(null);
            build();
        }

        /**
         * Load the UI and its listeners.
         */
        private void build() {
            addPreferencesFromResource(R.xml.pref_general);

            SwitchPreference enableService = (SwitchPreference) findPreference(ArffGlobals.ARFF_ENABLE_SERVICE);

            enableService.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object prefObject) {
                    Boolean enabled = (Boolean) prefObject;
                    Intent serviceIntent = new Intent(getActivity(), ArffService.class);
                    if (enabled) {
                        Log.i(getActivity().getLocalClassName(), "Service is to be started.");
                        getActivity().startService(serviceIntent);
                    } else {
                        Log.i(getActivity().getLocalClassName(), "Service is to be stopped.");
                        getActivity().stopService(serviceIntent);
                    }
                    return true;
                }
            });
        }
    }
}
