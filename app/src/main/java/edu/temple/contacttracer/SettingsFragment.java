package edu.temple.contacttracer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    Context context;
    SharedPreferences preferences;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(context)
                .registerOnSharedPreferenceChangeListener(this);
        findPreference(Constants.PREF_KEY_UUID).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                UUIDContainer uuidContainer = UUIDContainer.getUUIDContainer(context);
                uuidContainer.generateUUID();
                Toast.makeText(getActivity(), "New UUID generated", Toast.LENGTH_SHORT).show();
                return true;
            }
        });


    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
    }

    @Override
    public void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(context)
                .unregisterOnSharedPreferenceChangeListener(this);


    }

    // Here we do checks to make sure numeric text values are within range.
    // Note that this wouldn't be necessary if we used a ListPreference
    // for that value, however that would mean we would need to present
    // discrete values to the user. That may work in some situations,
    // but not all.
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(Constants.PREF_KEY_CONTACT_TIME)) {
            int distance = Integer.parseInt(((EditTextPreference)findPreference(key)).getText());
            if (distance < 1 || distance > 10) { // Those values really shouldn't be literals, but it's late
                Toast.makeText(getActivity(), "Invalid time - Using default", Toast.LENGTH_SHORT).show();
                preferences.edit().putString(Constants.PREF_KEY_CONTACT_TIME, String.valueOf(Constants.CONTACT_TIME_DEFAULT)).apply();
                ((EditTextPreference)findPreference(key)).setText(
                        String.valueOf(Constants.CONTACT_TIME_DEFAULT)
                );
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.context = null;
    }
}