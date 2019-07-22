package com.rocsoftware.mymirror;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

public class SettingsActivity extends  AppCompatActivity {
    private  static final String TAG="SettingsActivity";

    /* All keys of preferences.xml */
    public static final String KEY_PREF_ANIM_UI = "preference_animation_ui_example";
    public static final String KEY_PREF_REM_PHOTO = "preference_remember_last_photo";
    public static final String KEY_PREF_SOUND_EFF = "preference_sound_effect";
    public static final String KEY_PREF_DEL_PHOTO = "preference_delete_photo";
    public static final String PREF_FULLPATHLASTPHOTO = "PREF_FULLPATHLASTPHOTO";
    public static final String PREF_URILASTPHOTO = "PREF_URILASTPHOTO";
    public static final String PREF_SECOND_ACTIVITY_RUN = "PREF_SECOND_ACTIVITY_RUN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);
        MyLog.d(TAG, Thread.currentThread().getStackTrace()[2].getMethodName());

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);  // Back Arrow
        //setTheme(R.style.Theme_AppCompat_Preferences);
        openSettings(savedInstanceState);
    }

    private void openSettings(Bundle savedInstanceState ) {

        MyPreferenceFragment fragment = new MyPreferenceFragment();
        fragment.setArguments(savedInstanceState);

       // getFragmentManager().beginTransaction().replace(R.id.prefs_container, fragment, "PREFERENCE_FRAGMENT").addToBackStack(null).commit();
        getFragmentManager().beginTransaction().replace( android.R.id.content, fragment, "PREFERENCE_FRAGMENT").commit();

    }

    @Override
    protected void onStop() {
        super.onStop();
      //  unloadSecondActivity();
        MyLog.d(TAG, Thread.currentThread().getStackTrace()[2].getMethodName());
    }

    private void unloadSecondActivity() {

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PREF_SECOND_ACTIVITY_RUN, false);
        editor.apply();
        editor.commit();
    }
}
