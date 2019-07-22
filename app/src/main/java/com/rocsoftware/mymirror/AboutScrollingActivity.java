package com.rocsoftware.mymirror;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;



public class AboutScrollingActivity extends AppCompatActivity {
    private static final String TAG="AboutScrollingActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_scrolling_about);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        TextView noteView = (TextView) findViewById(R.id.text_content5);
        Linkify.addLinks(noteView, Linkify.ALL);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                 menuEmail();
            }
        });
    }


    @Override
    protected void onStop() {
        super.onStop();
       // unloadSecondActivity();
        MyLog.d(TAG, Thread.currentThread().getStackTrace()[2].getMethodName());
    }

    private void unloadSecondActivity() {

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(SettingsActivity.PREF_SECOND_ACTIVITY_RUN, false);
        editor.apply();
        editor.commit();
    }

    public void menuEmail() {
        try {
            MyLog.d(TAG, "Email");
            Resources resources = getResources();
            String emailto = resources.getString(R.string.email_support);
            String appName = resources.getString(R.string.app_name);
            String version = resources.getString(R.string.app_version);
            Intent mailIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto: " + emailto));

            String s = "Only for purpose of support info:";
            s += "\n OS Version: " + System.getProperty("os.version") + "(" + Build.VERSION.INCREMENTAL + ")";
            s += "\n Release: Android " + Build.VERSION.RELEASE;
            s += "\n OS API Level: " + Build.VERSION.SDK_INT;
            s += "\n Brand: " + Build.BRAND;
            s += "\n Device: " + Build.DEVICE;
            s += "\n Manufacturer: " + Build.MANUFACTURER;
            s += "\n Model (and Product): " + Build.MODEL + " (" + Build.PRODUCT + ")";
            s += "\n Language: " + Locale.getDefault().getDisplayLanguage();
            s += "\n";
            s += "\n Your comments: ";
            s += "\n     ";

            String country = getUserCountry(this);
            country = (country == null) ? " " : country; //Locale.getDefault().toString()

            mailIntent.putExtra(Intent.EXTRA_SUBJECT, appName + "(" + version + ")," + country);
            mailIntent.putExtra(Intent.EXTRA_TEXT, s);

            // Check if an Activity exists to perform this action.

            PackageManager pm = getPackageManager();
            ComponentName cn = mailIntent.resolveActivity(pm);
            if (cn == null) {
                MyLog.d(TAG, "There isn't intent email.");
                Toast toast = Toast.makeText(getApplicationContext(), getString(R.string.sentence_notfound_email), Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
                toast.show();
            } else {
                startActivity(Intent.createChooser(mailIntent, getString(R.string.sentence_email)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private  String getUserCountry(Context context) {
        try {
            final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            final String simCountry = tm.getSimCountryIso();
            if (simCountry != null && simCountry.length() == 2) { // SIM country code is available
                return simCountry.toLowerCase(Locale.US);
            } else if (tm.getPhoneType() != TelephonyManager.PHONE_TYPE_CDMA) { // device is not 3G (would be unreliable)
                String networkCountry = tm.getNetworkCountryIso();
                if (networkCountry != null && networkCountry.length() == 2) { // network country code is available
                    return networkCountry.toLowerCase(Locale.US);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}