package com.rocsoftware.mymirror;

import android.annotation.SuppressLint;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ShareActionProvider;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.util.Range;
import android.util.Rational;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.doubleclick.PublisherInterstitialAd;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Locale;
import java.util.Random;


import static com.rocsoftware.mymirror.R.id.fragment_mirror_holder;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        Camera2BasicFragment.OnCameraClickedCallBackListener,
        Camera2BasicFragment.OnCameraReadyCallBackListener{

    private static final String TAG = "MyMirror.MainActivity";
    private Range mAeCompensationRange;
    private int minAeRangeOrg;
    private int maxAeRangeOrg;
    private Rational mAeCompensationStep;
    private float stepExposure;
    private int mAeCompensation;
    private float mAeCompensationInStep;
    private Toast toast;
    private Camera2BasicFragment mirror;

    private SharedPreferences sharedPreferences;
    private static final String PREF_SKBEXPOSURE = "PREF_SKBEXPOSURE"; // value in seek bar
    private static final String PREF_SKBZOOM = "PREF_SKBZOOM"; // value in seek bar zoom
    private static final String PREF_AECOMPENSATION = "PREF_AECOMPENSATION"; // value for camera
    private static final String PREF_FULLPATHLASTPHOTO = "PREF_FULLPATHLASTPHOTO"; // full path last photo
    private static final String PREF_URILASTPHOTO = "PREF_URILASTPHOTO"; // Uri last photo
    private static final String PREF_BITMAPLASTPHOTO = "PREF_BITMAPLASTPHOTO";
    private static final String PREF_SECOND_ACTIVITY_RUN  =  "PREF_SECOND_ACTIVITY_RUN";
    private static final String PREF_COLOR_EFFECT  =  "PREF_COLOR_EFFECT";

    /**
     * How many times app show UI help to user?
     */
    public static final String PREF_TIMESSHOWUI = "PREF_TIMESSHOWUI";
    public static final String PREF_SHOWBARS = "PREF_SHOWBARS";
    public static final String PREF_SHOWWIDGETS = " PREF_SHOWWIDGETS";
    public static final String PREF_WIDGETSVISIBLES = "PREF_WIDGETSVISIBLES";


    private AdView mAdView;

    // What button or image is visible or not?
    /**
     * 0   - gallery
     * 1   - share screen
     * 2   - camera
     * 3   - seekbar
     * 4   - image photo
     * 5   - available memory
     * 6   - share action bar
     * 7   - appbarlayout
     */
    private int widgetShowScreen[] = {View.VISIBLE, View.GONE, View.VISIBLE, View.VISIBLE,
            View.GONE, View.VISIBLE, View.GONE, View.VISIBLE, View.VISIBLE, View.VISIBLE, View.GONE};
    private final int IDXGALLERY = 0;
    private final int IDXSHARE = 1;
    private final int IDXCAMERA = 2;
    private final int IDXSEEKBAR = 3;
    private final int IDXPHOTO = 4;
    private final int IDXMEMORY = 5;
    private final int IDXSHARETB = 6;
    private final int IDXBARLAYOUT = 7;
    private final int IDXCOLOREFFECT = 8;
    private final int IDXSKBZOOM = 9;
    private final int IDXDELETETB = 10;


    private static int PICK_IMAGE_REQUEST = 1;
    private Bitmap mBitmapLastPhoto = null;
    private File mFileLastPhoto = null;
    private String mFullPathLastPhoto = null;
    private Uri mUriLastPhoto = null;
    private int mCurrentLevelZoom=0;    // skb_action_zoom in steps

    private PublisherInterstitialAd mPublisherInterstitialAd;
    private FloatingActionButton fab_gallery;
    private FloatingActionButton fab_share;
    private FloatingActionButton fab_camera;
    private FloatingActionButton fab_color_effect;

    private SeekBar skb_action_exposure;
    private SeekBar skb_action_zoom ;
    private ImageView imgV_lastPhoto;
    private TextView txtV_availableMemory;
    private MenuItem shareItem;
    private MenuItem deleteItem;
    private Toolbar toolbar;
    private AppBarLayout appBarLayout;

    private ShareActionProvider myShareActionProvider;


    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private View mControlsView;

    private final Runnable mHidePart2Runnable = new Runnable() {

        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            /** Delayed removal of status and navigation bar */

            /** Hide UI first */
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {

                animationHideActionBar(appBarLayout);
                actionBar.hide();
                widgetShowScreen[IDXBARLAYOUT] = View.GONE;
                if (shareItem.isVisible()) {
                    animationShowButton(fab_share);
                    fab_share.setVisibility(View.VISIBLE);
                    widgetShowScreen[IDXSHARE] = View.VISIBLE;
                }

            }
         /*   if (mControlsView != null) {
                if (mControlsView.getVisibility() == View.VISIBLE) {
                   // animationAdvertising(false);
                   // mControlsView.setVisibility(View.GONE);
                }
            }
            */

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_IMMERSIVE
                    //   | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    // | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );

            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);

            getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);


        }
    };

    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();

            if (actionBar != null) {
                animationShowActionBar(appBarLayout);
                actionBar.show();
                widgetShowScreen[IDXBARLAYOUT] = View.VISIBLE;
                if (shareItem.isVisible()) {
                    animationHideButton(fab_share);
                    fab_share.setVisibility(View.GONE);
                    widgetShowScreen[IDXSHARE] = View.GONE;
                }
            }
/*
            if (mControlsView != null ) {
                if (mControlsView.getVisibility() == View.GONE) {
                  //  animationAdvertising(true);
                  //  mControlsView.setVisibility(View.VISIBLE);
                }
            }
*/
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_LAYOUT_FLAGS);
            //  mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);


        }
    };

    private boolean mShowBars;
    private boolean mShowWidgets;
    private int mLevelHideWidgets=-1;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    private final Runnable mShowRunnable = new Runnable() {
        @Override
        public void run() {
            show();
        }
    };

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;
    private ProgressDialog progressDialog;

    private long debug_time=0;

    // Interface with the camera fragment for interchange information:  camera --> main activity
    @Override
    public void onCameraReadyCallBack(int mColorEffect,
                                      int mCurrentZoomLevel) {

        /** This callback say to main activity when camera and preview are ready */
       /** toast = Toast.makeText(getApplicationContext(), "The Camera and Preview are Ready", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
        toast.show();*/

        mCurrentLevelZoom = mirror.getCurrentZoomLevel();
        skb_action_zoom.setProgress(mCurrentLevelZoom);

    }

    @Override
    public void onCameraClickedCallBack(CameraDevice mCameraDevice,
                                        CaptureRequest mPreviewRequest,
                                        CameraCaptureSession mCaptureSession,
                                        File mFile,
                                        Bitmap mImageBitmap) {
        // Interface with the camera fragment

        progressDialog.dismiss();  // always
        if (mFile != null) {

            setImageViewWithImage(mImageBitmap);
            showImageViewWithImage(mImageBitmap);
            animationPhoto();
            mFileLastPhoto = mFile;
            mFullPathLastPhoto = mFile.getAbsolutePath();
            mUriLastPhoto = getImageContentUri(this, mFile);
            txtV_availableMemory.setText(getAvailableInternalMemorySize());
            widgetShowScreen[IDXMEMORY] = View.VISIBLE;
            prepareSharePhoto();
            prepareDeleteLastPhoto();
            MyLog.d(TAG, "URI photo SELECTED: " + mUriLastPhoto.toString());
            MyLog.d(TAG, "Full path File photo: " + mFullPathLastPhoto);

            MyLog.d(TAG, "onClickedCamera: total time picture: " + (System.currentTimeMillis() - debug_time));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MyLog.d(TAG, Thread.currentThread().getStackTrace()[2].getMethodName());

        setContentView(R.layout.activity_main);

        mShowBars = true;
        mShowWidgets = true;
        progressDialog = new ProgressDialog(MainActivity.this);

        mContentView = findViewById(R.id.fragment_mirror_holder);
        mControlsView = findViewById(R.id.frlayout_content_controls);

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        setBrightnessForCamera(true);

        FragmentManager fmg = getFragmentManager();
        fmg.beginTransaction()
                .replace(fragment_mirror_holder, Camera2BasicFragment.newInstance())
                .commit();

/**
        mPublisherInterstitialAd = new PublisherInterstitialAd(this);
        mPublisherInterstitialAd.setAdUnitId("/6499/example/interstitial");

        mPublisherInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                requestNewInterstitial();

            }
        });

        requestNewInterstitial();
*/

        MyLog.d(TAG, "Free Space: " + getAvailableInternalMemorySize());
        // MyLog.d(TAG,"available: "+available.toString());
        MyLog.d(TAG, "Total Space: " + getTotalInternalMemorySize());

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        appBarLayout = (AppBarLayout) findViewById(R.id.appbar_layout);


        fab_gallery = (FloatingActionButton) findViewById(R.id.flb_action_gallery);
        fab_gallery.setOnClickListener(new View.OnClickListener() {
                                           @Override
                                           public void onClick(View view) {

                                               animationTouchButton(fab_gallery);
                                               pickGalleryPhoto();
                                           }

                                       }
        );

        txtV_availableMemory = (TextView) findViewById(R.id.txtV_availableMemory);
        txtV_availableMemory.setText(getAvailableInternalMemorySize());

        fab_share = (FloatingActionButton) findViewById(R.id.flb_action_share);

        fab_share.setOnClickListener(new View.OnClickListener() {
                                         @Override
                                         public void onClick(View view) {

                                             animationTouchButton(fab_share);
                                             sharePhoto();


                                         }

                                     }
        );

        mirror = (Camera2BasicFragment) getFragmentManager().findFragmentById(fragment_mirror_holder);

        fab_camera = (FloatingActionButton) findViewById(R.id.flb_action_camera);
        fab_camera.setOnClickListener(new View.OnClickListener() {
                                          @Override
                                          public void onClick(View view) {

                                        //      Camera2BasicFragment mirror = (Camera2BasicFragment)
                                           //           getFragmentManager().findFragmentById(fragment_mirror_holder);

                                              if (mirror != null) {
                                                  debug_time = System.currentTimeMillis();
                                                  animationTouchButton(fab_camera);
                                                  showProgressDialog();
                                                  mirror.takePicture();
                                              }
                                          }

                                      }
        );

        fab_color_effect = (FloatingActionButton) findViewById(R.id.flb_action_color_effect);
        fab_color_effect.setOnClickListener(new View.OnClickListener() {
                                          @Override
                                          public void onClick(View view) {

                                          //    Camera2BasicFragment mirror = (Camera2BasicFragment)
                                           //           getFragmentManager().findFragmentById(fragment_mirror_holder);

                                              if (mirror != null) {
                                                  animationTouchButton(fab_color_effect);
                                                  mirror.setNextColorEffect();
                                                  sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                                                  SharedPreferences.Editor editor = sharedPreferences.edit();
                                                  editor.putInt(PREF_COLOR_EFFECT, mirror.getColorEffect());
                                                  editor.apply();
                                                  editor.commit();
                                                  MyLog.d(TAG, "user color effect(despues de grabar): " + sharedPreferences.getInt(PREF_COLOR_EFFECT, 0));

                                              }
                                          }

                                      }
        );

        skb_action_exposure = (SeekBar) findViewById(R.id.skb_action_exposure);

        skb_action_exposure.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
/**
                mAeCompensation = seekBar.getProgress() + minAeRangeOrg;  // Integer value
                mAeCompensationInStep = (float) mAeCompensation * stepExposure;
                toast = Toast.makeText(getApplicationContext(), getResources().getString(R.string.brightness) + ": " + String.format(Locale.getDefault(), "%+2.2f ", mAeCompensationInStep) + " EV", Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.BOTTOM | Gravity.START, 0, 210);
//                toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
                toast.show();
   */
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

                mAeCompensation = seekBar.getProgress() + minAeRangeOrg;  // Integer value
                mAeCompensationInStep = (float) mAeCompensation * stepExposure;
                mirror.setAeCompensation(mAeCompensation);  // At Stop:: only here
                toast = Toast.makeText(getApplicationContext(), getResources().getString(R.string.brightness) + ": " + String.format(Locale.getDefault(), "%+2.2f ", mAeCompensationInStep) + " EV", Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.BOTTOM | Gravity.START, 0, 210);
                //   toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
                toast.show();


                MyLog.d(TAG, "Compensation Exposure: " + Integer.toString(mirror.getAeCurrentCompensation()));
                MyLog.d(TAG, "progress seek bar: " + Integer.toString(seekBar.getProgress()));

                sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(PREF_SKBEXPOSURE, seekBar.getProgress());
                editor.putInt(PREF_AECOMPENSATION, mAeCompensation);
                editor.apply();
                if (editor.commit())
                    MyLog.d(TAG, "user pref  exposure(despues de grabar): " + sharedPreferences.getInt(PREF_SKBEXPOSURE, 0));
            }

        });


        skb_action_zoom = (SeekBar) findViewById(R.id.skb_action_zoom);


        skb_action_zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mirror.setZoom(seekBar.getProgress());  // At Stop:: only here
                mCurrentLevelZoom = seekBar.getProgress();

                MyLog.d(TAG,"ZoomLevel: "+ mCurrentLevelZoom);
                MyLog.d(TAG, "progress seekbar zoom: " + Integer.toString(seekBar.getProgress()));

                sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(PREF_SKBZOOM, seekBar.getProgress());

                editor.apply();
                if (editor.commit())
                    MyLog.d(TAG, "user pref zoom(despues de grabar): " + sharedPreferences.getInt(PREF_SKBZOOM, 0));

            }

        });

        imgV_lastPhoto = (ImageView) findViewById(R.id.imgV_lastPhoto);
        imgV_lastPhoto.setOnClickListener(new View.OnClickListener() {
                                       @Override
                                       public void onClick(View view) {
                                           animationTouchImage(imgV_lastPhoto);
                                           imageGalleryPhoto();
                                       }

                                   }
        );

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Load user preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false); // only one time
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        updateUIFromPreferences();

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            // Gets the ad view defined in layout/ad_fragment.xml with ad unit ID set in
            // values/strings.xml.
            mAdView = (AdView) findViewById(R.id.adView);
/** Publicidad desactivada temporalmente -- chequeo del 26/10/2018
            mAdView.setAdListener(new

                                          AdListener() {
                                              private void showToast(String message) {
                                                  // View view = this.getActivity();
                                                  // Activity activity;
                                                  // activity.getWindow().
                                                  // if (view != null) {
                                                  Toast.makeText(getWindow().getContext(), message, Toast.LENGTH_SHORT).show();
                                                  // }
                                              }

                                              @Override
                                              public void onAdLoaded() {
                                                  //showToast("Ad loaded.");
                                                  animationAdvertising(true);
                                              }

                                              @Override
                                              public void onAdFailedToLoad(int errorCode) {
                                                  //showToast(String.format("Ad failed to load with error code %d.", errorCode));
                                              }

                                              @Override
                                              public void onAdOpened() {
                                                 // showToast("Ad opened.");
                                                  MyLog.d(TAG, "AD opened...");


                                              }

                                              @Override
                                              public void onAdClosed() {
                                                  showToast("Ad closed.");
                                              }

                                              @Override
                                              public void onAdLeftApplication() {

                                                  // showToast("Ad left application.");

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

            */
        }


        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        /** Publicidad desactivada temporalmente -- chequeo del 26/10/2018
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
*/

    }

    @Override
    protected void onResume() {   // Process init or resume for running
        super.onResume();

        MyLog.d(TAG, Thread.currentThread().getStackTrace()[2].getMethodName());

        // The activity has become visible (it is now "resumed").
        txtV_availableMemory.setText(getAvailableInternalMemorySize());
        mirror = (Camera2BasicFragment) getFragmentManager().findFragmentById(fragment_mirror_holder);

        if (mirror != null) {

            /** Here only set seekbar. the camera preview  always set the exposure value from preferences.
            */

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
            skb_action_exposure.setProgress(sharedPreferences.getInt(PREF_SKBEXPOSURE, 0));

            mAeCompensation = skb_action_exposure.getProgress() + minAeRangeOrg;  // in mAeCompensationRange[]
            mAeCompensationInStep = mAeCompensation * stepExposure;


            if (mirror.isZoomSupported()) {
                /** This only is good for Intent previously called. Not for Second Activity, the
                 * AOS process them differently. For this reason there is a OnCameraReadyCallback method
                 * in a Main Activity for  */

                if (mLevelHideWidgets == -1) {
                    skb_action_zoom.setVisibility(View.VISIBLE);
                }
                skb_action_zoom.setMax(mirror.getMaxZoomLevel());
                skb_action_zoom.setKeyProgressIncrement(+1);
                mCurrentLevelZoom = mirror.getCurrentZoomLevel();
                skb_action_zoom.setProgress(mCurrentLevelZoom);

            } else {
                skb_action_zoom.setVisibility(View.GONE);
                widgetShowScreen[IDXSKBZOOM] = View.GONE;
            }
        }
        txtV_availableMemory.setText(getAvailableInternalMemorySize());
        refreshDataLastPhoto();

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
       // MyLog.d(TAG,"onSaveInstance");
        MyLog.d(TAG, Thread.currentThread().getStackTrace()[2].getMethodName());
        outState.putInt(PREF_TIMESSHOWUI, 1);
        outState.putBoolean(PREF_SHOWBARS, mShowBars);
        outState.putBoolean(PREF_SHOWWIDGETS, mShowWidgets);
        outState.putIntArray(PREF_WIDGETSVISIBLES, widgetShowScreen);
        outState.putInt(PREF_SKBZOOM, mCurrentLevelZoom);

        if (mFullPathLastPhoto != null)
            outState.putString(PREF_FULLPATHLASTPHOTO, mFullPathLastPhoto);
        if (mUriLastPhoto != null)
            outState.putString(PREF_URILASTPHOTO, mUriLastPhoto.toString());
        if (mBitmapLastPhoto != null)
            outState.putByteArray(PREF_BITMAPLASTPHOTO, convertBitmapToByteArray(mBitmapLastPhoto));

        super.onSaveInstanceState(outState);
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {

       // MyLog.d(TAG,"onPostCreate");
        MyLog.d(TAG, Thread.currentThread().getStackTrace()[2].getMethodName());

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        Integer firstTimeShowSession = null;
        if (savedInstanceState == null)
            MyLog.d(TAG, "savedinstancestate == null");
        if (savedInstanceState != null) {
            MyLog.d(TAG, "savedinstancestate != null");
            if (savedInstanceState.containsKey(PREF_TIMESSHOWUI))
                firstTimeShowSession = savedInstanceState.getInt(PREF_TIMESSHOWUI);
            if (savedInstanceState.containsKey(PREF_SHOWBARS))
                mShowBars = savedInstanceState.getBoolean(PREF_SHOWBARS);
            if (savedInstanceState.containsKey(PREF_SHOWWIDGETS))
                mShowWidgets = savedInstanceState.getBoolean(PREF_SHOWWIDGETS);
            if (savedInstanceState.containsKey(PREF_WIDGETSVISIBLES))
                widgetShowScreen = savedInstanceState.getIntArray(PREF_WIDGETSVISIBLES);
            if (savedInstanceState.containsKey(PREF_FULLPATHLASTPHOTO)) {
                if (savedInstanceState.containsKey(PREF_URILASTPHOTO))
                    mUriLastPhoto = Uri.parse(savedInstanceState.getString(PREF_URILASTPHOTO));

                mFullPathLastPhoto = savedInstanceState.getString(PREF_FULLPATHLASTPHOTO);
                mBitmapLastPhoto = BitmapFactory.decodeFile(mFullPathLastPhoto);
                mFileLastPhoto = new File(mFullPathLastPhoto);

                /*if (mFileLastPhoto != null && mFileLastPhoto.exists()) {
                    setImageViewWithImage(mBitmapLastPhoto);
                    showImageViewWithImage(mBitmapLastPhoto);
                }
                else {
                    mFullPathLastPhoto = null;
                    mFileLastPhoto = null;
                    mUriLastPhoto = null;
                    mBitmapLastPhoto = null;
                    widgetShowScreen[IDXSHARE] = View.GONE;
                    widgetShowScreen[IDXSHARETB] = View.GONE;
                    widgetShowScreen[IDXPHOTO] = View.GONE;
                }*/
            }
            if (savedInstanceState.containsKey(PREF_SKBZOOM)) {// Only for session
                mCurrentLevelZoom = savedInstanceState.getInt(PREF_SKBZOOM);

                MyLog.d(TAG, "ZoomLevel: " + mCurrentLevelZoom);
            }
        }

        showAnimationExampleUI(firstTimeShowSession);

        //   if (mShowBars) show(); else hide();
        //    if (mShowWidgets) showWidgets(); else hideWidgets();

        super.onPostCreate(savedInstanceState);

    }

    private void showAnimationExampleUI( Integer firstTimeShowSession) {

        Boolean mPrefAnimUI = sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_ANIM_UI, true);
        int timesShow = sharedPreferences.getInt(PREF_TIMESSHOWUI, 0);

        if (mPrefAnimUI && firstTimeShowSession == null) {
            // Trigger the initial hide() shortly after the activity has been
            // created, to briefly hint to the user that UI controls
            // are available.
            //
            delayedHide(3000);
            delayedShow(6000);

            MyLog.d(TAG, "Times show UI: " + Integer.toString(timesShow));
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt(PREF_TIMESSHOWUI, ++timesShow);
            editor.apply();
            if (editor.commit())
                MyLog.d(TAG, "Times show UI(despues de grabar): " + sharedPreferences.getInt(PREF_TIMESSHOWUI, 0));
        }

    }

    private void loadLastPhoto() {
        Boolean mPrefRemPhoto = sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_REM_PHOTO, false);
        if (mPrefRemPhoto) {
            MyLog.d(TAG, "looking for last photo");
            mFullPathLastPhoto = sharedPreferences.getString(SettingsActivity.PREF_FULLPATHLASTPHOTO, "");
            mUriLastPhoto = Uri.parse(sharedPreferences.getString(SettingsActivity.PREF_URILASTPHOTO, ""));
            MyLog.d(TAG, "last photo path: " + mFullPathLastPhoto.toString());
            MyLog.d(TAG, "last photo uri: " + mUriLastPhoto.toString());
            mFileLastPhoto = new File(mFullPathLastPhoto);

            if (mFileLastPhoto.exists()) {
                mBitmapLastPhoto = BitmapFactory.decodeFile(mFullPathLastPhoto);
                setImageViewWithImage(mBitmapLastPhoto);
                prepareSharePhoto();
            }
        }
    }

    private void showLastPhoto() {
        Boolean mPrefRemPhoto = sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_REM_PHOTO, false);
        if (mPrefRemPhoto) {
            showImageViewWithImage(mBitmapLastPhoto);
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        MyLog.d(TAG, Thread.currentThread().getStackTrace()[2].getMethodName());

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);

        shareItem = menu.findItem(R.id.action_share);
        myShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(shareItem);

        deleteItem = menu.findItem(R.id.action_delete);



        if (mShowBars) show();else { hide(); }

        loadLastPhoto();

        if (mShowWidgets) {
            showWidgets();
            showLastPhoto();
        }
        else { hideWidgets();}


        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            openSettings();
            return true;
        }

        if  (id == R.id.action_delete) {
            deleteLastPhoto();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {

        MyLog.d(TAG, Thread.currentThread().getStackTrace()[2].getMethodName());
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_gallery) {
            menuGalleryPhoto();
        } else if (id == R.id.nav_share) {
            sharePhoto();
        } else if (id == R.id.nav_email) {
            menuEmail();
        } else if (id == R.id.nav_settings) {
            openSettings();
        } else if (id == R.id.nav_about) {
            menuAbout();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        MyLog.d(TAG, Thread.currentThread().getStackTrace()[2].getMethodName());
        MyLog.d(TAG,"come back from  share intent or another");
        try {
            txtV_availableMemory.setText(getAvailableInternalMemorySize());
            widgetShowScreen[IDXMEMORY] = View.VISIBLE;
            // When an Image is picked
            if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK
                    && null != data) {
                // Get the Image from data

                Uri selectedImage = data.getData();
                mUriLastPhoto = selectedImage;
                MyLog.d(TAG, "URI IMAGE SELECTED: " + selectedImage.toString());
                String[] filePathColumn = {MediaStore.Images.Media.DATA};

                // Get the cursor
                Cursor cursor = getContentResolver().query(selectedImage,
                        filePathColumn, null, null, null);
                // Move to first row
                assert cursor != null;
                cursor.moveToFirst();

                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                String imgDecodableString = cursor.getString(columnIndex);
                cursor.close();

           /*     MyLog.d(TAG,"path file from gallery: "+imgDecodableString );
                ImageView imgV_lastPhoto = (ImageView) findViewById(R.id.imgV_lastPhoto);


                Bitmap photo = BitmapFactory. decodeFile(imgDecodableString);
                Bitmap resized = Bitmap.createScaledBitmap(photo, imgV_lastPhoto.getWidth(), imgV_lastPhoto.getHeight(), true);
                // Set the Image in ImageView after decoding the String
                imgV_lastPhoto.setImageBitmap(resized);
                animationPhoto();
                imgV_lastPhoto.setVisibility(View.VISIBLE);
              //  imgV_lastPhoto.setImageBitmap(BitmapFactory.decodeFile(imgDecodableString));
                FloatingActionButton fab_share = (FloatingActionButton) findViewById(R.id.flb_action_share);
                fab_share.setVisibility(FloatingActionButton.VISIBLE);
            */
                if (imgDecodableString != null) {
                    mFileLastPhoto = new File(imgDecodableString);
                    mFullPathLastPhoto = imgDecodableString;
                    mBitmapLastPhoto = BitmapFactory.decodeFile(mFullPathLastPhoto);
                    setImageViewWithImage(mBitmapLastPhoto);
                    showImageViewWithImage(mBitmapLastPhoto);
                    animationPhoto();
                    prepareSharePhoto();
                    rememberLastPhoto();
                    prepareDeleteLastPhoto();
                    MyLog.d(TAG, "pick full path photo: " + mFullPathLastPhoto);
                }
            } else {
                Toast.makeText(this, getString(R.string.sentence_havent_picked_image),
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.sentence_wrong), Toast.LENGTH_LONG)
                    .show();
        }

    }

    /**
     * Additional Subroutines
     */

    private void setBrightnessForCamera(boolean turnOnOffBrightnessMax) {
        MyLog.d(TAG, Thread.currentThread().getStackTrace()[2].getMethodName());

        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        if  (turnOnOffBrightnessMax)
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        else
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;

        getWindow().setAttributes(layoutParams);

        WindowManager.LayoutParams layoutParams2 = getWindow().getAttributes();

        MyLog.d(TAG,"Brillo window max value: "+WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL);
        MyLog.d(TAG,"Brillo window none value: "+WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE);
        MyLog.d(TAG,"Brillo window value: "+getWindow().getAttributes().screenBrightness);
    }


    private void showProgressDialog() {


       // progressDialog.getWindow().setBackgroundDrawableResource(R.color.colorPrimary);

        progressDialog.setMessage(getResources().getString(R.string.sentence_wait));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setMax(100);
        progressDialog.show();
    }

    private void saveCurrentZoom() {

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(PREF_SKBZOOM, mCurrentLevelZoom);
        editor.apply();
        editor.commit();

    }

    private void saveCurrentColorEffect() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(PREF_COLOR_EFFECT, mirror.getColorEffect());
        editor.apply();
        editor.commit();
    }

    private void loadCurrentZoom() {

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        mCurrentLevelZoom = sharedPreferences.getInt(PREF_SKBZOOM, 0);


    }

    private void setFlagLoadSecondActivity() {

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PREF_SECOND_ACTIVITY_RUN, true);
        editor.apply();
        editor.commit();
    }

    private void setFlagUnloadSecondActivity() {

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PREF_SECOND_ACTIVITY_RUN, false);
        editor.apply();
        editor.commit();
    }

    private void openSettings() {
       saveCurrentZoom();
       saveCurrentColorEffect();
       setFlagLoadSecondActivity();
       Intent intent = new Intent(this, SettingsActivity.class);
       startActivity(intent);
      /** SettingsActivity will do unloadSecondActivity(); */
    }

    private void menuAbout() {

       // new AboutDialogFragment().show(getFragmentManager(), "Dialog");
       // LayoutInflater inflater = this.getLayoutInflater();
       // RelativeLayout aboutLayout = (RelativeLayout) inflater.inflate(R.layout.about_layout2, null);
       /* ScrollView aboutLayout = (ScrollView) inflater.inflate(R.layout.about_layout2, null);
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        bottomSheetDialog.setContentView(aboutLayout);
        bottomSheetDialog.show(); */

        saveCurrentZoom();
        saveCurrentColorEffect();
        setFlagLoadSecondActivity();
        Intent intent = new Intent(this, AboutScrollingActivity.class);
        startActivity(intent);

    }

    private void deleteLastPhoto() {

        if (mFullPathLastPhoto != null && mFileLastPhoto != null) {
            try {

                if ( mFileLastPhoto.exists() ) {

                    if ( isImageOfThisApp() ) {
                        // Only It can delete file from application's directory
                        Boolean mPrefDelPhoto = sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_DEL_PHOTO, false);
                        if (!mPrefDelPhoto) { // I Dont show dialog
                            getContentResolver().delete(mUriLastPhoto, null, null);
                            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mUriLastPhoto));
                            toast = Toast.makeText(getApplicationContext(), getString(R.string.sentence_photo_deleted), Toast.LENGTH_SHORT);
                            toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
                            toast.show();
                            refreshDataLastPhoto();
                        }else {
                            //new AlertDialog.Builder(this,android.R.style.Theme_Holo_Dialog_NoActionBar_MinWidth )
                            new AlertDialog.Builder(this)
                                    .setMessage(R.string.sentence_ask_del_photo)
                                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            getContentResolver().delete(mUriLastPhoto, null, null);
                                            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mUriLastPhoto));
                                            toast = Toast.makeText(getApplicationContext(), getString(R.string.sentence_photo_deleted), Toast.LENGTH_SHORT);
                                            toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
                                            toast.show();
                                            refreshDataLastPhoto();
                                        }
                                    })
                                    .setNegativeButton(android.R.string.cancel,
                                            new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {


                                                }
                                            })
                                    .create()
                                    .show();
                        }
                    }
                    //  }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void refreshDataLastPhoto() {

        if (mFileLastPhoto != null && !mFileLastPhoto.exists()) {
            mFullPathLastPhoto = null;
            mFileLastPhoto = null;
            mUriLastPhoto = null;
            mBitmapLastPhoto = null;
            widgetShowScreen[IDXSHARE] = View.GONE;
            widgetShowScreen[IDXSHARETB] = View.GONE;
            widgetShowScreen[IDXPHOTO] = View.GONE;
            widgetShowScreen[IDXDELETETB] = View.GONE;

            fab_share.setVisibility(View.GONE);
            shareItem.setVisible(false);
            deleteItem.setVisible(false);
            toolbar.setTitleTextAppearance(getApplication().getApplicationContext(), R.style.MyTitleStyleOrg);

            imgV_lastPhoto.setVisibility(View.GONE);
        }

    }

    private void prepareDeleteLastPhoto() {
        if (mFullPathLastPhoto != null && mFileLastPhoto != null) {
            try {
                if (isImageOfThisApp()) {
                    // I change the title appearance to 16dp size and we can see it complete.
                    toolbar.setTitleTextAppearance(getApplication().getApplicationContext() ,R.style.MyTitleStyle);
                    deleteItem.setVisible(true);
                    widgetShowScreen[IDXDELETETB] = View.VISIBLE;
                } else {
                    deleteItem.setVisible(false); // the image is not mine
                    widgetShowScreen[IDXDELETETB] = View.GONE;
                    toolbar.setTitleTextAppearance(getApplication().getApplicationContext() ,R.style.MyTitleStyleOrg);

                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Boolean isImageOfThisApp() {
        String storagedir = getApplication().getApplicationContext().getResources().getString(R.string.cia_developer);

        if (mFullPathLastPhoto != null && mFileLastPhoto != null) {
            try {

                if (mFileLastPhoto.exists()) {

                    MyLog.d(TAG, "canonical: " + mFileLastPhoto.getName());
                    String absolutePath1 = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/" + storagedir + "/" + mFileLastPhoto.getName();
                    MyLog.d(TAG, "path1: " + absolutePath1);
                    MyLog.d(TAG, "mFullPathLastPhoto: " + mFullPathLastPhoto);
                    return (absolutePath1.equals(mFullPathLastPhoto));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @NonNull
    private static String getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        long available = availableBlocks * blockSize;
        return formatSize(available);
    }

    private static String getTotalInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        return formatSize(totalBlocks * blockSize);
    }

    private static String formatSize(long size) {
        String suffix = null;

        double sizeD = (double) size;
        if (sizeD >= 1024) {
            suffix = "KB";
            sizeD /= 1024;
            if (sizeD >= 1024) {
                suffix = "MB";
                sizeD /= 1024;
                if (sizeD >= 100) {
                    suffix = "GB";
                    sizeD /= 1024;
                }
            }
        }


        StringBuilder resultBuffer = new StringBuilder(String.format(Locale.getDefault(), "%.2f", sizeD));

/*        int commaOffset = resultBuffer.length() - 3;
        while (commaOffset > 0) {
            resultBuffer.insert(commaOffset, ',');
            commaOffset -= 3;
        }
*/
        if (suffix != null) resultBuffer.append(suffix);
        return resultBuffer.toString();
    }

    private void requestNewInterstitial() {
        PublisherAdRequest adRequest = new PublisherAdRequest.Builder()
                .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)        // All emulators
                .addTestDevice("BB0E24C0DE8CD420086C69DBE37E4814")
                .build();

        mPublisherInterstitialAd.loadAd(adRequest);


    }

    private void pickGalleryPhoto() {

        // Create intent to Open Image applications like Gallery, Google Photos
        /** Gallery Intent after chooser. Funciona. */
        Intent galleryIntent;

      /*  if (mUriLastPhoto != null ) {
            MyLog.d(TAG,"GalleryPhoto uri: "+mUriLastPhoto.toString());
            galleryIntent = new Intent(Intent.ACTION_PICK,mUriLastPhoto);
        }
        else { */
        galleryIntent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");
        // }
        // Check if an Activity exists to perform this action.

        PackageManager pm = getPackageManager();
        ComponentName cn = galleryIntent.resolveActivity(pm);
        if (cn == null) {
            MyLog.d(TAG, "There isn't intent gallery.");
            toast = Toast.makeText(getApplicationContext(), getString(R.string.sentence_notfound_gallery), Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
            toast.show();
        } else {
            String storagedir = this.getApplication().getApplicationContext().getResources().getString(R.string.cia_developer);
            toast = Toast.makeText(getApplicationContext(), getString(R.string.sentence_select_directory) + "  " + storagedir, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
            toast.show();
            startActivityForResult(galleryIntent, PICK_IMAGE_REQUEST);
        }

    }

    private void imageGalleryPhoto() {

        // Create intent to Open Image applications like Gallery, Google Photos
        // Run app gallery activity nothing return.Its a main activity
        /** Gallery Intent after chooser. Funciona. */

        if (mUriLastPhoto != null) {
            try {
                MyLog.d(TAG, "GalleryPhoto uri: " + mUriLastPhoto.toString());
                Uri uri = mUriLastPhoto; // example: Uri.parse("content://media/external/images/media/36970");
                Intent galleryIntent = new Intent(Intent.ACTION_VIEW, uri);


                // Check if an Activity exists to perform this action.

                PackageManager pm = getPackageManager();
                ComponentName cn = galleryIntent.resolveActivity(pm);
                if (cn == null) {
                    MyLog.d(TAG, "There isn't intent gallery.");
                    toast = Toast.makeText(getApplicationContext(), getString(R.string.sentence_notfound_galleryorfile), Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
                    toast.show();
                } else {
                    startActivity(galleryIntent);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void menuGalleryPhoto() {

        // Create intent to Open Image applications like Gallery, Google Photos
        // Run app gallery activity nothing return.Its a main activity
        /** Gallery Intent after chooser. Funciona. */
        String REVIEW_ACTION = "com.android.camera.action.REVIEW";
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        try {
            // REVIEW_ACTION means we can view video files without autoplaying
            Intent intent = new Intent(REVIEW_ACTION, uri);
            this.startActivity(intent);
            txtV_availableMemory.setText(getAvailableInternalMemorySize());
            widgetShowScreen[IDXMEMORY] = View.VISIBLE;
        } catch (ActivityNotFoundException e) {
            try {

                //   Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                Intent galleryIntent = new Intent(Intent.ACTION_VIEW, uri);


                // Check if an Activity exists to perform this action.

                PackageManager pm = getPackageManager();
                ComponentName cn = galleryIntent.resolveActivity(pm);
                if (cn == null) {
                    MyLog.d(TAG, "There isn't intent gallery.");
                    toast = Toast.makeText(getApplicationContext(), getString(R.string.sentence_notfound_gallery), Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
                    toast.show();
                } else {
                    startActivity(galleryIntent);

                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
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
                toast = Toast.makeText(getApplicationContext(), getString(R.string.sentence_notfound_email), Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
                toast.show();
            } else {
                startActivity(Intent.createChooser(mailIntent, getString(R.string.sentence_email)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getUserCountry(Context context) {
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

    private  Uri getImageContentUri(Context context, File imageFile) {
        String filePath = imageFile.getAbsolutePath();
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID},
                MediaStore.Images.Media.DATA + "=? ",
                new String[]{filePath}, null);
        if (cursor != null && cursor.moveToFirst()) {
            int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
            cursor.close();
            return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "" + id);
        } else {
            if (imageFile.exists()) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DATA, filePath);
                return context.getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            } else {
                return null;
            }
        }
    }


    private void sharePhoto() {

        if (mFullPathLastPhoto != null && mFileLastPhoto != null) {
            try {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/*");
                MyLog.d(TAG, "Path share: " + mFullPathLastPhoto);
                MyLog.d(TAG, "Path share: " + mFileLastPhoto.getAbsolutePath());
                Uri imageUri = Uri.fromFile(mFileLastPhoto); //parse(mFullPathLastPhoto);
                shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);

                // Check if an Activity exists to perform this action.

                PackageManager pm = getPackageManager();
                ComponentName cn = shareIntent.resolveActivity(pm);
                if (cn == null) {
                    MyLog.d(TAG, "There isn't intent share app.");
                    toast = Toast.makeText(getApplicationContext(), getString(R.string.sentence_notfound_shareorfile), Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
                    toast.show();
                } else {

                    // myShareActionProvider.setShareIntent(shareIntent);

                    startActivity(Intent.createChooser(shareIntent, getResources().getString(R.string.sentence_share)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void prepareSharePhoto() {

        if (mFullPathLastPhoto != null && mFileLastPhoto != null) {
            try {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/*");
                MyLog.d(TAG, "Path share: " + mFullPathLastPhoto);
                MyLog.d(TAG, "Path share: " + mFileLastPhoto.getAbsolutePath());
                Uri imageUri = Uri.fromFile(mFileLastPhoto); //parse(mFullPathLastPhoto);
                shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);

                // Check if an Activity exists to perform this action.

                PackageManager pm = getPackageManager();
                ComponentName cn = shareIntent.resolveActivity(pm);
                if (cn == null) {
                    MyLog.d(TAG, "There isn't intent share app.");
                    toast = Toast.makeText(getApplicationContext(), getString(R.string.sentence_notfound_shareorfile), Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 50, 50);
                    toast.show();
                } else {

                    myShareActionProvider.setShareIntent(shareIntent);


                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void rememberLastPhoto() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PREF_FULLPATHLASTPHOTO, mFullPathLastPhoto);
        editor.putString(PREF_URILASTPHOTO, mUriLastPhoto.toString());
        editor.apply();
        editor.commit();
    }

    private void setImageViewWithImage(Bitmap lastPhoto) {
        final int dimPhoto = 72;
        if (lastPhoto != null) {
            try {
                ImageView imgV_temporalPhoto = (ImageView) findViewById(R.id.imgV_temporalPhoto);
                ImageView imgV_lastPhoto = (ImageView) findViewById(R.id.imgV_lastPhoto);
                mBitmapLastPhoto = lastPhoto;
                Bitmap photo = lastPhoto;
                MyLog.d(TAG,"Bitmap photo dimens: w: "+photo.getWidth()+" h: "+photo.getHeight());
                imgV_temporalPhoto.setImageBitmap(photo);
                //   Bitmap resized = Bitmap.createScaledBitmap(photo, imgV_lastPhoto.getWidth(), imgV_lastPhoto.getHeight(), true);
                Bitmap resized = Bitmap.createScaledBitmap(photo, dimPhoto, dimPhoto, true);
                // Set the Image in ImageView after scale
                imgV_lastPhoto.setImageBitmap(resized);
                //imgV_temporalPhoto.setImageURI(mUriLastPhoto);


            } catch (Exception e) {
                e.printStackTrace();
            }
        } else MyLog.d(TAG, "Photo Bitmap nula");
    }

    private void setImageViewWithImageV0(Bitmap lastPhoto) {

        if (lastPhoto != null) {
            try {
                FloatingActionButton fab_share = (FloatingActionButton) findViewById(R.id.flb_action_share);
                ImageView imgV_lastPhoto = (ImageView) findViewById(R.id.imgV_lastPhoto);
                mBitmapLastPhoto = lastPhoto;
                Bitmap photo = lastPhoto;
                //   Bitmap resized = Bitmap.createScaledBitmap(photo, imgV_lastPhoto.getWidth(), imgV_lastPhoto.getHeight(), true);
                Bitmap resized = Bitmap.createScaledBitmap(photo, 96, 96, true);
                // Set the Image in ImageView after scale
                imgV_lastPhoto.setImageBitmap(resized);

                imgV_lastPhoto.setVisibility(View.VISIBLE);
                widgetShowScreen[IDXPHOTO] = View.VISIBLE;
                shareItem.setVisible(true);
                widgetShowScreen[IDXSHARETB] = View.VISIBLE;
                if (toolbar.getVisibility() == View.VISIBLE) {
                    fab_share.setVisibility(FloatingActionButton.GONE);
                    widgetShowScreen[IDXSHARE] = View.GONE;
                } else {
                    if (fab_share.getVisibility() != FloatingActionButton.VISIBLE) {
                        animationShowButton(fab_share);
                        fab_share.setVisibility(FloatingActionButton.VISIBLE);
                        widgetShowScreen[IDXSHARE] = View.VISIBLE;
                    }


                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        } else MyLog.d(TAG, "Photo Bitmap nula");
    }

    private void showImageViewWithImage(Bitmap lastPhoto) {

        if (lastPhoto != null) {
            try {
                FloatingActionButton fab_share = (FloatingActionButton) findViewById(R.id.flb_action_share);
                ImageView imgV_lastPhoto = (ImageView) findViewById(R.id.imgV_lastPhoto);


                imgV_lastPhoto.setVisibility(View.VISIBLE);
                widgetShowScreen[IDXPHOTO] = View.VISIBLE;
                shareItem.setVisible(true);
                widgetShowScreen[IDXSHARETB] = View.VISIBLE;
                prepareDeleteLastPhoto();
                if (toolbar.getVisibility() == View.VISIBLE) {
                    fab_share.setVisibility(FloatingActionButton.GONE);
                    widgetShowScreen[IDXSHARE] = View.GONE;
                } else {
                    if (fab_share.getVisibility() != FloatingActionButton.VISIBLE) {
                        animationShowButton(fab_share);
                        fab_share.setVisibility(FloatingActionButton.VISIBLE);
                        widgetShowScreen[IDXSHARE] = View.VISIBLE;
                    }


                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        } else MyLog.d(TAG, "Photo Bitmap nula");
    }

    private void animationTouchButton(FloatingActionButton button) {
        Animation animation = AnimationUtils.loadAnimation(this, R.anim.animation_touch);
        button.startAnimation(animation);
    }

    private void animationShowButton(FloatingActionButton button) {
        Animation animation = AnimationUtils.loadAnimation(this, R.anim.animation_show);
        button.startAnimation(animation);
    }

    private void animationHideButton(FloatingActionButton button) {
        Animation animation = AnimationUtils.loadAnimation(this, R.anim.animation_hide);
        button.startAnimation(animation);
    }

    private void animationShowActionBar(AppBarLayout actionBar) {
        Animation animation = AnimationUtils.loadAnimation(this, R.anim.animation_updown_bar);
        actionBar.startAnimation(animation);
    }

    private void animationHideActionBar(AppBarLayout actionBar) {
        Animation animation = AnimationUtils.loadAnimation(this, R.anim.animation_downup_bar);
        actionBar.startAnimation(animation);
    }


    private void animationTouchImage(ImageView imageView) {
        Animation animation = AnimationUtils.loadAnimation(this, R.anim.animation_touch);
        imageView.startAnimation(animation);
    }

    private void animationPhoto() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {

            ImageView imgV_lastPhoto = (ImageView) findViewById(R.id.imgV_lastPhoto);
            ImageView imgView2 = (ImageView) findViewById(R.id.imgV_temporalPhoto);

            if (imgV_lastPhoto.getVisibility() == View.VISIBLE) {
                imgV_lastPhoto.setVisibility(View.GONE);

                imgView2.setVisibility(View.VISIBLE);
                Animation animation = AnimationUtils.loadAnimation(this, R.anim.animation_photo);

                imgView2.startAnimation(animation);
                imgView2.setVisibility(View.GONE);
                imgV_lastPhoto.setVisibility(View.VISIBLE);
            }

        } else animationPhoto_land();
    }

    private void animationPhoto_land() {
        ImageView imgV_lastPhoto = (ImageView) findViewById(R.id.imgV_lastPhoto);
        if (imgV_lastPhoto.getVisibility() == View.VISIBLE) {
            Animation animation = AnimationUtils.loadAnimation(this, R.anim.animation_photo_land);
            imgV_lastPhoto.startAnimation(animation);
        }
    }

    private void animationAdvertising(boolean showAdLayout) {  // Animated Layout with Advertising

        FrameLayout layoutAnimado = (FrameLayout) mControlsView;

        Animation animation = null;
        if (showAdLayout) {
            int sceneAnimation = randomNumber();
            switch (sceneAnimation) {
                case 1:
                    //desde la esquina inferior derecha a la superior izquierda (right to left )
                    animation = AnimationUtils.loadAnimation(this, R.anim.animation_rightleft_adv);
                    break;

                default:
                    //desde esquina inferior izquierda a la superior izquierda (down-up)
                    animation = AnimationUtils.loadAnimation(this, R.anim.animation_downup_adv);
            }
            layoutAnimado.startAnimation(animation);
            //layoutAnimado.setY(layoutAnimado.getY() - 50.0f);
        } else {
            int sceneAnimation = randomNumber();
            switch (sceneAnimation) {
                case 1:
                    //desde la esquina superior izquierda  a la esquina inferior derecha (left to right )
                    animation = AnimationUtils.loadAnimation(this, R.anim.animation_leftright_adv);
                    break;

                default:
                    //desde la esquina superior izquierda a la inferior izquierda (up-down)
                    animation = AnimationUtils.loadAnimation(this, R.anim.animation_updown_adv);
            }
            layoutAnimado.startAnimation(animation);
            // layoutAnimado.setY(layoutAnimado.getY() + 50.0f);
        }

        // layoutAnimado.startAnimation(animation);

    }

    private static byte[] convertBitmapToByteArray(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        } else {
            byte[] b = null;
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 0, byteArrayOutputStream);
                b = byteArrayOutputStream.toByteArray();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return b;
        }
    }

    private void animationAdvertisingV1(boolean showAdLayout) {  // Animated Layout with Advertising

        FrameLayout layoutAnimado = (FrameLayout) mControlsView;

        AnimationSet set = new AnimationSet(true);
        Animation animation = null;
        if (showAdLayout) {
            int sceneAnimation = randomNumber();
            switch (sceneAnimation) {
                case 1:
                    //desde la esquina inferior derecha a la superior izquierda (right to left )
                    animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
                    break;
               /* case 2:
                    //desde la esquina inferior izquierda a la superior derecha (left to right )
                    animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
                    break; */
                default:
                    //desde esquina inferior izquierda a la superior izquierda (down-up)
                    animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
            }
        } else {
            int sceneAnimation = randomNumber();
            switch (sceneAnimation) {
                case 1:
                    //desde la esquina superior izquierda  a la esquina inferior derecha (left to right )
                    animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f);
                    break;
               /* case 2:
                    //desde la esquina superior derecha a la esquina inferior izquierda (right to left)
                    animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
                    break; */
                default:
                    //desde la esquina superior izquierda a la inferior izquierda (up-down)
                    animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f);
            }
        }
        //duración en milisegundos
        animation.setDuration(500);
        set.addAnimation(animation);
        LayoutAnimationController controller = new LayoutAnimationController(set, 0.25f);

        layoutAnimado.setLayoutAnimation(controller);
        layoutAnimado.startAnimation(animation);
    }

    private int randomNumber() {
        int min = 2;
        int max = 4;

        Random r = new Random();
        int i1 = r.nextInt(max - min + 1) + min;
        return i1;
    }

    private void updateUIFromPreferences() {
        // update user interface with user preferences
        int userExposure = sharedPreferences.getInt(PREF_SKBEXPOSURE, 0);

    }


    private void toggle() {
        if (mShowBars) {
            hide();
        } else {
            if (mShowWidgets) {
                hideWidgets();
            } else {
                // ShowIntersticialAdv(); offline till win
                show();
                showWidgets();
            }
        }
    }

    private void ShowIntersticialAdv() {
        int showAd = randomNumber();
        if (mPublisherInterstitialAd.isLoaded() && showAd == 1) {
            mPublisherInterstitialAd.show();
        }
    }

    private void hide() {
        // Hide UI first
    /*    ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        if (mControlsView != null) {
            if (mControlsView.getVisibility() == View.VISIBLE) {
                animationAdvertising(false);
                mControlsView.setVisibility(View.GONE);
            }
        }
       */

        mShowBars = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }


    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        // mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        //                                 | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
/*
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
*/
        mShowBars = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedShow(int delayMillis) {
        mHideHandler.removeCallbacks(mShowRunnable);
        mHideHandler.postDelayed(mShowRunnable, delayMillis);
    }

    private void hideWidgets() {
        if ( mLevelHideWidgets == -1  ) {
            hideWidgets_Parcial(); mLevelHideWidgets = 0;
        } else {
            hideWidgets_all(); mLevelHideWidgets = 1;
        }
    }

    private void hideWidgets_Parcial() {
        MyLog.d(TAG, "hidewidgets");

        FloatingActionButton fab_gallery = (FloatingActionButton) findViewById(R.id.flb_action_gallery);
        FloatingActionButton fab_share = (FloatingActionButton) findViewById(R.id.flb_action_share);
        FloatingActionButton fab_camera = (FloatingActionButton) findViewById(R.id.flb_action_camera);
        FloatingActionButton fab_color_effect = (FloatingActionButton) findViewById(R.id.flb_action_color_effect);
        SeekBar skb_action_exposure = (SeekBar) findViewById(R.id.skb_action_exposure);
        SeekBar skb_action_zoom = (SeekBar) findViewById(R.id.skb_action_zoom);
        ImageView imgV_lastPhoto = (ImageView) findViewById(R.id.imgV_lastPhoto);
        TextView txtV_availableMemory = (TextView) findViewById(R.id.txtV_availableMemory);

        /* All widgets is gone but i dont change control array because only change for photo */
        fab_gallery.setVisibility(View.GONE);
        fab_share.setVisibility(View.GONE);
      // fab_camera.setVisibility(View.GONE);
        fab_camera.setSize(FloatingActionButton.SIZE_NORMAL);
        fab_color_effect.setVisibility(View.GONE);
        skb_action_exposure.setVisibility(View.GONE);
        skb_action_zoom.setVisibility(View.GONE);
      //  imgV_lastPhoto.setVisibility(View.GONE);
        txtV_availableMemory.setVisibility(View.GONE);

        //mShowWidgets = false;
        mLevelHideWidgets=0;
    }

    private void hideWidgets_all() {
        MyLog.d(TAG, "hidewidgets_all");

        FloatingActionButton fab_gallery = (FloatingActionButton) findViewById(R.id.flb_action_gallery);
        FloatingActionButton fab_share = (FloatingActionButton) findViewById(R.id.flb_action_share);
        FloatingActionButton fab_camera = (FloatingActionButton) findViewById(R.id.flb_action_camera);
        FloatingActionButton fab_color_effect = (FloatingActionButton) findViewById(R.id.flb_action_color_effect);
        SeekBar skb_action_exposure = (SeekBar) findViewById(R.id.skb_action_exposure);
        SeekBar skb_action_zoom = (SeekBar) findViewById(R.id.skb_action_zoom);
        ImageView imgV_lastPhoto = (ImageView) findViewById(R.id.imgV_lastPhoto);
        TextView txtV_availableMemory = (TextView) findViewById(R.id.txtV_availableMemory);

        /* All widgets is gone but i dont change control array because only change for photo */
        fab_gallery.setVisibility(View.GONE);
        fab_share.setVisibility(View.GONE);
        fab_camera.setVisibility(View.GONE);
        fab_color_effect.setVisibility(View.GONE);
        skb_action_exposure.setVisibility(View.GONE);
        skb_action_zoom.setVisibility(View.GONE);
        imgV_lastPhoto.setVisibility(View.GONE);
        txtV_availableMemory.setVisibility(View.GONE);

        mShowWidgets = false;  mLevelHideWidgets=1;
    }

    private void showWidgets() {
        MyLog.d(TAG, "showwidgets");
        // Show only widgets visible before hide them
        FloatingActionButton fab_gallery = (FloatingActionButton) findViewById(R.id.flb_action_gallery);
        FloatingActionButton fab_share = (FloatingActionButton) findViewById(R.id.flb_action_share);
        FloatingActionButton fab_camera = (FloatingActionButton) findViewById(R.id.flb_action_camera);
        FloatingActionButton fab_color_effect = (FloatingActionButton) findViewById(R.id.flb_action_color_effect);

        SeekBar skb_action_exposure = (SeekBar) findViewById(R.id.skb_action_exposure);
        SeekBar skb_action_zoom = (SeekBar) findViewById(R.id.skb_action_zoom);

        ImageView imgV_lastPhoto = (ImageView) findViewById(R.id.imgV_lastPhoto);
        TextView txtV_availableMemory = (TextView) findViewById(R.id.txtV_availableMemory);

        fab_gallery.setVisibility(widgetShowScreen[IDXGALLERY]);
        txtV_availableMemory.setVisibility(widgetShowScreen[IDXMEMORY]);
        fab_share.setVisibility(widgetShowScreen[IDXSHARE]);
        fab_camera.setVisibility(View.VISIBLE);
     //   fab_camera.setSize(FloatingActionButton.SIZE_MINI);
        fab_color_effect.setVisibility(widgetShowScreen[IDXCOLOREFFECT]);
        skb_action_exposure.setVisibility(View.VISIBLE);
        skb_action_zoom.setVisibility(widgetShowScreen[IDXSKBZOOM]);
        imgV_lastPhoto.setVisibility(widgetShowScreen[IDXPHOTO]);
        shareItem.setVisible(widgetShowScreen[IDXSHARETB] == View.VISIBLE);

        mShowWidgets = true; mLevelHideWidgets=-1;
    }

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("Main Page") // TODO: Define a title for the content shown.
                // TODO: Make sure this auto-generated URL is correct.
                .setUrl(Uri.parse("http://[ENTER-YOUR-HTTP-HOST-HERE]/main"))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
    //    client.connect();
      //  AppIndex.AppIndexApi.start(client, getIndexApiAction());
        MyLog.d(TAG, Thread.currentThread().getStackTrace()[2].getMethodName());

    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
       // AppIndex.AppIndexApi.end(client, getIndexApiAction());
       // client.disconnect();

        MyLog.d(TAG, Thread.currentThread().getStackTrace()[2].getMethodName());

        MyLog.i(TAG,"Saliendo de la aplicacion...");
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


