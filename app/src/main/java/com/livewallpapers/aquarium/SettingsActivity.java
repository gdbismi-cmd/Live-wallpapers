package com.livewallpapers.aquarium;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceFragment;

/** Settings screen shown from the live wallpaper picker. */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new AquariumPreferences())
                    .commit();
        }
    }

    public static class AquariumPreferences extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
        }
    }
}
