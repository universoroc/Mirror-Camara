package com.rocsoftware.mymirror;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;


/**
 * Created by Rogelio on 29/11/2016.
 */

public class MyPreferenceFragment extends PreferenceFragment {
    private static final String TAG = "MyPreferenceFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {

        MyLog.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

    }

    public void onResume() {
        super.onResume();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

    }
}
