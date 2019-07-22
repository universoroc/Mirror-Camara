package com.rocsoftware.mymirror;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;

import android.app.FragmentManager;
import android.util.Range;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import android.widget.SeekBar;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;


import static com.rocsoftware.mymirror.R.id.fragment_mirror_holder;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MyMirror.MainActivity";
    private Range mAeCompensationRange;
    private int minAeRangeOrg;
    private int maxAeRangeOrg;
    private Rational mAeCompensationStep;
    private float stepExposure;
    private int mAeCompensation;
    private float  mAeCompensationInStep;
    private Toast  toast;
    private Camera2BasicFragment mirror;

    private SharedPreferences sharedPreferences;
    public static final String PREF_SKBEXPOSURE = "PREF_SKBEXPOSURE"; // value in seekbar
    public static final String PREF_AECOMPENSATION = "PREF_AECOMPENSATION"; // value for camera
    public int userPrefExposure=0;
    AdView mAdView;


    @Override
    protected void onResume() {
        super.onResume();
        // The activity has become visible (it is now "resumed").

        mirror = (Camera2BasicFragment) getFragmentManager().findFragmentById(fragment_mirror_holder);

        if (mirror != null) {


            mAeCompensationRange = mirror.getAeCompensationRange();
            mAeCompensationStep = mirror.getAeCompensationStep();

            stepExposure = mAeCompensationStep.floatValue();
            minAeRangeOrg = (int) mAeCompensationRange.getLower();
            maxAeRangeOrg = (int) mAeCompensationRange.getUpper();

            float minAeRangeStepExp = (int) (minAeRangeOrg * stepExposure);
            float maxAeRangeStepExp = (int) (maxAeRangeOrg * stepExposure);

            SeekBar skb_action_exposure = (SeekBar) findViewById(R.id.skb_action_exposure);
            skb_action_exposure.setKeyProgressIncrement(+1);
            skb_action_exposure.setMax(Math.abs(maxAeRangeOrg - minAeRangeOrg));
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            skb_action_exposure.setProgress( sharedPreferences.getInt(PREF_SKBEXPOSURE, 0) );

            mAeCompensation = skb_action_exposure.getProgress() + minAeRangeOrg;  // in mAeCompensationRange[]
            mAeCompensationInStep = mAeCompensation * stepExposure;

        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        FragmentManager fmg= getFragmentManager();
        fmg.beginTransaction()
                .replace(fragment_mirror_holder,  Camera2BasicFragment.newInstance())
                .commit();



        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        FloatingActionButton fab_camera = (FloatingActionButton) findViewById(R.id.flb_action_camera);
        fab_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              //  Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
              //          .setAction("Action", null).show();
                Camera2BasicFragment mirror = (Camera2BasicFragment)
                        getFragmentManager().findFragmentById(fragment_mirror_holder);

                if (mirror != null) {
                    mirror.takePicture();

                }
            }

        }
        );


        SeekBar skb_action_exposure = (SeekBar) findViewById(R.id.skb_action_exposure);

        skb_action_exposure.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

                mAeCompensation = seekBar.getProgress() + minAeRangeOrg;  // Integer value
                mAeCompensationInStep = (float)mAeCompensation * stepExposure;
                toast = Toast.makeText(getApplicationContext(), " Brillo: " + String.format("%+2.2f ",mAeCompensationInStep )+" EV" , Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.BOTTOM | Gravity.LEFT, 0, 210);
//                toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
                toast.show();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

                mAeCompensation = seekBar.getProgress() + minAeRangeOrg;  // Integer value
                mAeCompensationInStep = (float)mAeCompensation * stepExposure;
                mirror.setAeCompensation(mAeCompensation);  // At Stop:: only here
                toast = Toast.makeText(getApplicationContext(), " Brillo: " + String.format("%+2.2f ",mAeCompensationInStep )+" EV" , Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.BOTTOM | Gravity.LEFT, 0, 210);
                //   toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
                toast.show();
                sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

                MyLog.d(TAG,"progress seek bar: "+Integer.toString(seekBar.getProgress()));
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(PREF_SKBEXPOSURE, seekBar.getProgress());
                editor.putInt(PREF_AECOMPENSATION,mAeCompensation);
                editor.apply();
                if (editor.commit())
                   MyLog.d(TAG,"user pref  exposure(despues de grabar): "+sharedPreferences.getInt(PREF_SKBEXPOSURE,0));
            }

        });

            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                    this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
            drawer.addDrawerListener(toggle);
            toggle.syncState();

            NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
            navigationView.setNavigationItemSelectedListener(this);

            // Load user preferences
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            updateUIFromPreferences();

            // Gets the ad view defined in layout/ad_fragment.xml with ad unit ID set in
            // values/strings.xml.
            mAdView=(AdView) findViewById(R.id.adView);

            mAdView.setAdListener(new

            AdListener() {
                private void showToast (String message){
                    // View view = this.getActivity();
                    // Activity activity;
                    // activity.getWindow().
                    // if (view != null) {
                    Toast.makeText(getWindow().getContext(), message, Toast.LENGTH_SHORT).show();
                    // }
                }

                @Override
                public void onAdLoaded () {
                    showToast("Ad loaded.");
                }

                @Override
                public void onAdFailedToLoad ( int errorCode){
                    showToast(String.format("Ad failed to load with error code %d.", errorCode));
                }

                @Override
                public void onAdOpened () {
                    showToast("Ad opened.");
                    MyLog.d(TAG, "AD opened...");
                    // closeCamera();
                    // stopBackgroundThread();
                }

                @Override
                public void onAdClosed () {
                    showToast("Ad closed.");
                }

                @Override
                public void onAdLeftApplication () {

                    showToast("Ad left application.");
                    //closeCamera();
                    //stopBackgroundThread();
                }
            }

            );

            // Create an ad request. Check your MyLogcat output for the hashed device ID to
            // get test ads on a physical device. e.g.
            // "Use AdRequest.Builder.addTestDevice("ABCDEF012345") to get test ads on this device."
            //AdRequest.DEVICE_ID_EMULATOR
            AdRequest adRequest = new AdRequest.Builder()
                    .addTestDevice("BB0E24C0DE8CD420086C69DBE37E4814")
                    .build();

            // Start loading the ad in the background.
            mAdView.loadAd(adRequest);


        }

    private void updateUIFromPreferences() {
        // update user interface with user preferences
        int userExposure = sharedPreferences.getInt(PREF_SKBEXPOSURE, 0);

    }

        @Override
        public void onBackPressed () {
            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            if (drawer.isDrawerOpen(GravityCompat.START)) {
                drawer.closeDrawer(GravityCompat.START);
            } else {
                super.onBackPressed();
            }
        }

        @Override
        public boolean onCreateOptionsMenu (Menu menu){
            // Inflate the menu; this adds items to the action bar if it is present.
            getMenuInflater().inflate(R.menu.main, menu);
            return true;
        }

        @Override
        public boolean onOptionsItemSelected (MenuItem item){
            // Handle action bar item clicks here. The action bar will
            // automatically handle clicks on the Home/Up button, so long
            // as you specify a parent activity in AndroidManifest.xml.
            int id = item.getItemId();

            //noinspection SimplifiableIfStatement
            if (id == R.id.action_settings) {
                return true;
            }

            return super.onOptionsItemSelected(item);
        }

        @SuppressWarnings("StatementWithEmptyBody")
        @Override
        public boolean onNavigationItemSelected (MenuItem item){
            // Handle navigation view item clicks here.
            int id = item.getItemId();

            if (id == R.id.nav_camera) {
                // Handle the camera action
            } else if (id == R.id.nav_gallery) {

            } else if (id == R.id.nav_slideshow) {

            } else if (id == R.id.nav_manage) {

            } else if (id == R.id.nav_share) {

            } else if (id == R.id.nav_send) {

            }

            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            drawer.closeDrawer(GravityCompat.START);
            return true;

        }

// Android 6+ permission handling:

//    final private int MY_PERMISSIONS_REQUEST_CAMERA = 0;
//    final private int MY_PERMISSIONS_REQUEST_STORAGE = 1;

    /** Show a "rationale" to the user for needing a particular permission, then request that permission again
     *  once they close the dialog.
     */
   // @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    /*
   private void showRequestPermissionRationale(final int permission_code) {
        if(MyLog.DEBUG )
            MyLog.d(TAG, "showRequestPermissionRational: " + permission_code);
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M ) {
            if(MyLog.DEBUG )
                MyLog.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        boolean ok = true;
        String [] permissions = null;
        int message_id = 0;
        if( permission_code == MY_PERMISSIONS_REQUEST_CAMERA ) {
            if(MyLog.DEBUG )
                MyLog.d(TAG, "display rationale for camera permission");
            permissions = new String[]{Manifest.permission.CAMERA};
            message_id = R.string.permission_rationale_camera;
        }
        else if( permission_code == MY_PERMISSIONS_REQUEST_STORAGE ) {
            if(MyLog.DEBUG )
                MyLog.d(TAG, "display rationale for storage permission");
            permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
            message_id = R.string.permission_rationale_storage;
        }

        else {
            if(MyLog.DEBUG )
                MyLog.e(TAG, "showRequestPermissionRational unknown permission_code: " + permission_code);
            ok = false;
        }

        if( ok ) {
            final String [] permissions_f = permissions;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.permission_rationale_title)
                    .setMessage(message_id)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.ok, null)
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        public void onDismiss(DialogInterface dialog) {
                            if(MyLog.DEBUG )
                                MyLog.d(TAG, "requesting permission...");
                            ActivityCompat.requestPermissions(MainActivity.this, permissions_f, permission_code);
                        }
                    }).show();
        }
    }

    void requestCameraPermission() {
        if(MyLog.DEBUG )
            MyLog.d(TAG, "requestCameraPermission");
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M ) {
            if(MyLog.DEBUG )
                MyLog.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        if( ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_CAMERA);
        }
        else {
            // Can go ahead and request the permission
            if(MyLog.DEBUG )
                MyLog.d(TAG, "requesting camera permission...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }

    void requestStoragePermission() {
        if(MyLog.DEBUG )
            MyLog.d(TAG, "requestStoragePermission");
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M ) {
            if(MyLog.DEBUG )
                MyLog.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        if( ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_STORAGE);
        }
        else {
            // Can go ahead and request the permission
            if(MyLog.DEBUG )
                MyLog.d(TAG, "requesting storage permission...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_STORAGE);
        }
    }




    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if(MyLog.DEBUG )
            MyLog.d(TAG, "onRequestPermissionsResult: requestCode " + requestCode);
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M ) {
            if(MyLog.DEBUG )
                MyLog.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        switch( requestCode ) {
            case MY_PERMISSIONS_REQUEST_CAMERA:
            {
                // If request is cancelled, the result arrays are empty.
                if( grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED ) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if(MyLog.DEBUG )
                        MyLog.d(TAG, "camera permission granted");
                   // preview.retryOpenCamera();
                }
                else {
                    if(MyLog.DEBUG )
                        MyLog.d(TAG, "camera permission denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // Open Camera doesn't need to do anything: the camera will remain closed
                }
                return;
            }
            case MY_PERMISSIONS_REQUEST_STORAGE:
            {
                // If request is cancelled, the result arrays are empty.
                if( grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED ) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if(MyLog.DEBUG )
                        MyLog.d(TAG, "storage permission granted");
                    //preview.retryOpenCamera();
                }
                else {
                    if(MyLog.DEBUG )
                        MyLog.d(TAG, "storage permission denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // Open Camera doesn't need to do anything: the camera will remain closed
                }
                return;
            }

            default:
            {
                if(MyLog.DEBUG )
                    MyLog.e(TAG, "unknown requestCode " + requestCode);
            }
        }
    }
*/
    }


