/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rocsoftware.mymirror;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioAttributes;
import android.media.Image;
import android.media.ImageReader;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2BasicFragment
                extends     Fragment
                implements  FragmentCompat.OnRequestPermissionsResultCallback {

    /**
     *  this is the hierarchy:
     *  more information in:
     *  http://jylee-world.blogspot.com/2014/12/a-tutorial-of-androidhardwarecamera2.html
     *
     *
     *
     *   CameraManager
     *       Select Camera
     *       Create CameraDevice
     *   CameraDevice
     *       Create CaptureRequest
     *       Create CameraCaptureSession
     *   CaptureRequest, CaptureRequest.CameraBuilder
     *       Link Surface for Viewing
     *       Make CaptureRequest
     *   CameraCaptureSession
     *       Capture Camera Image and put the result on the Surface registered in CaptureRequest.
     */


    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "Camera2BasicFragment";

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 0;
    private static final int REQUEST_STORAGE_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;



    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

   // private AutoFitTextureView mTextureView2; /* For Image Reflexion */

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;


    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

   // private ImageReader mImageReader2; // for reflexion
    /**
     * This is the output file for our picture.
     */
    private File mFile;

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    private static int orientationImage;

    /** To control of brightness of image
     *
     * */
    /** @ROC ADD */
    private Range mAeCompensationRange=null; // example:[ -12 , 12 ] ==>[-12*mAeCompensationStep, +12*mAeCompensationStep ] = [-2,+2 ]

    private Rational mAeCompensationStep=null; // example: 166667/1000000 = 0.166667

    private Boolean mAeLockAvailable=false;      // must be false to change brightness. in CaptureRequest.builder

    /** this value control the brightness of image. it is in the CaptureRequest.builder */

    private int mAeCompensation=0; // steps -1,+1 in range mAeCompensationRange[]

    private int ae_exposure_compensation;

    public Context ApplicationContext;

    public static int openCameraCount=0;
    public static int closeCameraCount=0;

    private SoundPool soundPool;
    private int soundId;
    private Boolean  hasCameraSound= false;

    private SharedPreferences sharedPreferences;

    public static final String PREF_AECOMPENSATION = "PREF_AECOMPENSATION"; // value for camera


    /* start this fragment lifecycle and interface lifecycle  */

    /*@ROC:: Constructor must be of this type because is a fragment and
    **       is a restriction of java...
    **/

    public static Camera2BasicFragment newInstance() {return new Camera2BasicFragment();}


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_camera2_basic, container, false);

        return rootView;
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {

        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);

        ApplicationContext = getActivity().getApplicationContext();


    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        //File directory  = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        //              "RocSoftware");
        //File directory = Environment.getExternalStoragePublicDirectory(
        //       Environment.DIRECTORY_PICTURES);
        //directory.mkdir();

        //  File path = new File("/sdcard","RocSoftware");
        //  path.mkdir();
        //  mFile = new File(path.getAbsolutePath(),"pic8.jpg");

        //MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"directorio: "+directory.toString());
        // mFile = new File(directory.getAbsolutePath(),"pic8.jpg");
        // mFile= new File(getActivity().getApplication().getApplicationContext().getExternalFilesDir(null),"pic6.jpg");
        //mFile.mkdir();

        //** Este codigo funciona: guarda en memory externa:
        // Saved File Picture: /storage/emulated/0/Android/data/com.rocsoftware.mymirror/files/pic4.jpg
        //mFile = new File(getActivity().getExternalFilesDir(null), "pic4.jpg");


        // mFile = new File(getActivity().getFilesDir(), "pic3.jpg");
        // mFile = new File(Environment.getExternalStorageDirectory(), "pic5.jpg");
        // mFile = new File(Environment.getExternalStorageDirectory(Environment.DIRECTORY_PICTURES), "pic5.jpg");
        //MyLog.d("onActivityCreated","creando archivo pic.jpg");
        // File imagePath = new File(getActivity().getApplicationContext().getFilesDir(), "images/");
        // imagePath.mkdir();
        // File mFile = new File(imagePath, "default_image.jpg");

        //** Este codigo funciona pero crea archivos en: (inaccesible)
        // Saved File Picture: /data/user/0/com.rocsoftware.mymirror/files/external_files/test.jpg
        //  File imagePath = new File(getActivity().getApplicationContext().getFilesDir(), "external_files");
        // imagePath.mkdir();
        // mFile = new File(imagePath.getPath(), "test.jpg");

        findCameraCharacteristics();
/*
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        MyLog.d(TAG,"path: "+ path.toString());
       // mFile = new File(getActivity().getExternalFilesDir(null), "pic.jpg");
        mFile = new File(path, "RocSoftware");
        if (! mFile.exists()) {
            MyLog.d(TAG,"Voy a crear el directorio");
            if ( mFile.mkdir() ) MyLog.d(TAG,"Pude crearlo");
            else  MyLog.d(TAG,"no pude crearlo");

        }

        mFile = new File(mFile, "pic.jpg");

        try {
            mFile.createNewFile();
            MyLog.d("onActivityCreated","creando archivo pic.jpg");
            MyLog.d(TAG,"File: "+ mFile.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
*/
    }



    @Override
    public void onResume() { /* running and resume */
        super.onResume();
        startBackgroundThread();

        /** When the screen is turned off and turned back on, the SurfaceTexture is already
         * available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
         * a camera and start preview from here (otherwise, we wait until the surface is ready in
         * the SurfaceTextureListener).
         *
         *
         */

        if (mTextureView.isAvailable()) {
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"onResume: Voy a abrir la camara.");
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"onResume: No abri la camara. mTextureView no disponible");
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

    }

    @Override
    public void onPause() {
        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"onPause: invocando closecamera...");
        closeCamera();
        stopBackgroundThread();

        super.onPause();
    }



    /** Called before the activity is destroyed */
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /** Implements interface for check and request of camera permissions to execute fragment.
     *  Exit on negative answer.
     * */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera(mTextureView.getWidth(), mTextureView.getHeight());
               /* ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG); */
            }
        } else  if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera(mTextureView.getWidth(), mTextureView.getHeight());
              /*  ErrorDialog.newInstance(getString(R.string.permission_rationale_storage))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG); */
            }
        }
        else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

// End of fragment lifecycle and others implements of interface methods

// Begin Listeners and Other Methods.

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"onSurfaceTextureAvilable: voy a openCamera: "+"width: "+ Integer.toString(width)+ " height: "+ Integer.toString(height));
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "onSurfaceTextureDestroyed");
           // closeCamera();
            //stopBackgroundThread();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };


    public Range getAeCompensationRange() { return mAeCompensationRange; }

    public Rational getAeCompensationStep() {
        return  mAeCompensationStep;
    }

    public int getAeCompensation() { return  mAeCompensation; }

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;

        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };



    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
           // mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
            ImageSaver p = new ImageSaver(reader.acquireNextImage(), mFile);
            new Thread(p).start();
        }

    };


    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };


    private void findCameraCharacteristics() {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We  use a front facing camera in this app.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }


                //@ROC
                mAeCompensationRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);

                mAeCompensationStep = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);

                //mAeLockAvailable =characteristics.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE);

                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    public void setAeCompensation(Integer newExposure) {
        if ( newExposure != null && newExposure != mAeCompensation) {
            mAeCompensation = newExposure;
            createCameraPreviewSession();
        }
    }

    private boolean setExposureCompensation(CaptureRequest.Builder builder) {
      /*  if( !has_ae_exposure_compensation )
            return false;
        if( has_iso ) {
            if( MyDebug.MyLog )
                MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "don't set exposure compensation in manual iso mode");
            return false;
        } */
        if( builder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) == null || ae_exposure_compensation != builder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) ) {
         //   if( MyDebug.MyLog )
           //     MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "change exposure to " + ae_exposure_compensation);
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ae_exposure_compensation);
            return true;
        }
        return false;
    }


    /**
     * Opens the camera specified by {@link Camera2BasicFragment#mCameraId}.
     */
    private void openCamera(int width, int height) {

        openCameraCount++;
        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"0penCamera: "+Integer.toString(openCameraCount));

        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }

        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestStoragePermission();
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        initCameraSound();
        Activity activity = getActivity();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
        mAeCompensation = sharedPreferences.getInt(PREF_AECOMPENSATION, 0);

        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"acquiriendo camara: "+mCameraOpenCloseLock.toString());
            if ( mCameraOpenCloseLock.availablePermits() != 0 ) {
                // this previous check is very important because callback methods:
                // onSurfaceTextureAvailable and onResume run at different moments and asynchronous way.
                // we have the camera open previously for this fragment.
                if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                    MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "No pude adquirir la camara: Permiso en valor: " + Integer.toString(mCameraOpenCloseLock.availablePermits()));
                    throw new RuntimeException("Time out waiting to lock camera opening.");
                }
                manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            } //else this fragment open previously the camera.
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Interrupcion: No pude adquirir la camara: Permiso en valor: "+Integer.toString(mCameraOpenCloseLock.availablePermits()));
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }


    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {

            closeCameraCount++;
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"closeCamera entrando...: "+Integer.toString(closeCameraCount));
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"semaforo camara antes de adquirir en close camara: "+mCameraOpenCloseLock.toString());
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"closeCamera saliendo....");
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {

            mCameraOpenCloseLock.release();
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"semaforo camara despues de liberar en close camara: "+mCameraOpenCloseLock.toString());
        }
    }



    private void requestCameraPermission() {
        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"requestCameraPermission: ... ");

        if (FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            new ConfirmationDialogCamera().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }
    private void requestStoragePermission() {
        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"requestStoragePermission: ... ");

        if (FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            new ConfirmationDialogStorage().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {

                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We use a front facing camera in this app.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    continue; // continue lookig for...
                }
              /* if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                   continue;
                }
                */
                StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) { continue; }  // continue looking for ...

                // For still image captures, we use the largest available size.
                Size largest = Collections.max( Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                                                new CompareSizesByArea()
                                                );
                MyLog.d(TAG,"largest: "+largest.toString());
/*
                mImageReader = ImageReader.newInstance( largest.getWidth(),
                                                        largest.getHeight(),
                                                        ImageFormat.JPEG, 2);

                mImageReader.setOnImageAvailableListener(   mOnImageAvailableListener,
                                                            mBackgroundHandler
                                                          );
*/
                //@ROC
                mAeCompensationRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);

                mAeCompensationStep = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);

                //mAeLockAvailable =characteristics.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE);

                setPreviewSize( activity, characteristics, map, width,  height);

                mImageReader = ImageReader.newInstance( mPreviewSize.getWidth(),
                                                        mPreviewSize.getHeight(),
                                                        ImageFormat.JPEG, /*maxImages*/2);

                mImageReader.setOnImageAvailableListener(   mOnImageAvailableListener,
                                                            mBackgroundHandler
                                                         );

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
  /*ROC
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        MyLog.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth  = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth      = displaySize.x;
                int maxPreviewHeight     = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth  = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth      = displaySize.y;
                    maxPreviewHeight     = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                                                 rotatedPreviewWidth, rotatedPreviewHeight,
                                                 maxPreviewWidth, maxPreviewHeight,
                                                 largest);


                MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Tamano escogido preview: "+mPreviewSize.toString());
                MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Tamano escogido preview width: "+Integer.toString(mPreviewSize.getWidth()));
                MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Tamano escogido preview height: "+Integer.toString(mPreviewSize.getHeight()));

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Orientacion landscape");
                    mTextureView.setAspectRatio( mPreviewSize.getWidth(),mPreviewSize.getHeight());

                } else {
                    MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Orientacion portrait");
                    mTextureView.setAspectRatio(mPreviewSize.getHeight(),mPreviewSize.getWidth());
                }

                MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Textureview width: "+Integer.toString(mTextureView.getWidth()));
                MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Textureview height: "+Integer.toString(mTextureView.getHeight()));
*/
                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    private void setPreviewSize( Activity activity,
                                 CameraCharacteristics characteristics,
                                 StreamConfigurationMap map,
                                 int width, int height) {

        // For still image captures, we use the largest available size.
        Size largest = Collections.max( Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                                        new CompareSizesByArea()
                                        );
        MyLog.d(TAG,"largest: "+largest.toString());

        // Find out if we need to swap dimension to get the preview size relative to sensor
        // coordinate.
        int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        //noinspection ConstantConditions
        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        boolean swappedDimensions = false;
        switch (displayRotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                    swappedDimensions = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                    swappedDimensions = true;
                }
                break;
            default:
                MyLog.e(TAG, "Display rotation is invalid: " + displayRotation);
        }

        Point displaySize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
        int rotatedPreviewWidth  = width;
        int rotatedPreviewHeight = height;
        int maxPreviewWidth      = displaySize.x;
        int maxPreviewHeight     = displaySize.y;

        if (swappedDimensions) {
            rotatedPreviewWidth  = height;
            rotatedPreviewHeight = width;
            maxPreviewWidth      = displaySize.y;
            maxPreviewHeight     = displaySize.x;
        }

        if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
            maxPreviewWidth = MAX_PREVIEW_WIDTH;
        }

        if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
            maxPreviewHeight = MAX_PREVIEW_HEIGHT;
        }

        // Danger, W.R.! Attempting to use too large a preview  size could  exceed the camera
        // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
        // garbage capture data.
      /*  mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                                         rotatedPreviewWidth, rotatedPreviewHeight,
                                         maxPreviewWidth, maxPreviewHeight,
                                         largest);
        */
        mPreviewSize = getOptimalPreviewSize(activity, characteristics, map.getOutputSizes(SurfaceTexture.class), largest);

        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Tamano escogido preview: "+mPreviewSize.toString());
        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Tamano escogido preview width: "+Integer.toString(mPreviewSize.getWidth()));
        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Tamano escogido preview height: "+Integer.toString(mPreviewSize.getHeight()));

        // We fit the aspect ratio of TextureView to the size of preview we picked.
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Orientacion landscape");
            mTextureView.setAspectRatio( mPreviewSize.getWidth(),mPreviewSize.getHeight());

        } else {
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Orientacion portrait");
            mTextureView.setAspectRatio(mPreviewSize.getHeight(),mPreviewSize.getWidth());
        }

        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Textureview width: "+Integer.toString(mTextureView.getWidth()));
        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Textureview height: "+Integer.toString(mTextureView.getHeight()));

    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices,
                                          int textureViewWidth,
                                          int textureViewHeight,
                                          int maxWidth,
                                          int maxHeight,
                                          Size aspectRatio) {

        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"textureViewWidth: "+Integer.toString(textureViewWidth));
        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"textureViewHeight: "+Integer.toString(textureViewHeight));
        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"maxWidth: "+Integer.toString(maxWidth));
        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"maxHeight: "+Integer.toString(maxHeight));
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {

            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Lista de tamanos de la camara: "+option.toString() );
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+" option w: "+Integer.toString(option.getWidth())+
                    " option h: "+Integer.toString(option.getHeight()));

            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight
                    && option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }
        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Collections bigEnough: "+bigEnough.toString());
        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Collections notbigEnough: "+notBigEnough.toString());
        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "preview size escogido 1: "+Collections.min(bigEnough, new CompareSizesByArea()).toString());
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "preview size escogido.2: "+Collections.max(notBigEnough, new CompareSizesByArea()).toString());
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            MyLog.e(TAG, "Couldn't find any suitable preview size");
            MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "preview size escogido.3: "+choices[0].toString());
            return choices[0];
        }
    }

    public Size getOptimalPreviewSize(Activity activity,
                                      CameraCharacteristics characteristics,
                                      Size[] sizesSupportedSurface,
                                      Size largestJpegCamera) {

        MyLog.d(TAG, "getOptimalPreviewSize()");
        final double ASPECT_TOLERANCE = 0.05;
        if( sizesSupportedSurface == null )
            return null;
        Size optimalSize = null;

        MyLog.d(TAG,"sizes camera: " + sizesSupportedSurface.toString());
        double minDiff = Double.MAX_VALUE;

        int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        //noinspection ConstantConditions
        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        boolean swappedDimensions = false;
        switch (displayRotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                    swappedDimensions = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                    swappedDimensions = true;
                }
                break;
            default:
                MyLog.e(TAG, "Display rotation is invalid: " + displayRotation);
        }
        Point display_size = new Point();
       // Activity activity = (Activity)this.getContext();
        {
            Display display = activity.getWindowManager().getDefaultDisplay();
            display.getSize(display_size);

            MyLog.d(TAG, "display_size antes: " + display_size.x + " x " + display_size.y);
        }

        if ( displayRotation ==  Surface.ROTATION_0 || displayRotation == Surface.ROTATION_180 ) { //portrait
            int z          = display_size.y;
            display_size.y = display_size.x;
            display_size.x = z;

            MyLog.d(TAG, "display_size despues: " + display_size.x + " x " + display_size.y);
        }


        double targetRatio = calculateTargetRatioForPreview(display_size);
       // int targetHeight = Math.min(display_size.y, display_size.x);
      //  if( targetHeight <= 0 ) {
            int targetHeight = display_size.y;
       // }
        // Try to find the size which matches the aspect ratio, and is closest match to display height
        for(Size size : sizesSupportedSurface) {

            MyLog.d(TAG, "supported preview size: " + size.getWidth() + ", " + size.getHeight());
            double ratio = (double)size.getWidth() / size.getHeight();
            if( Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE )
                continue;
            if( Math.abs(size.getHeight() - targetHeight) < minDiff ) {
                optimalSize = size;
                minDiff = Math.abs(size.getHeight() - targetHeight);
            }
        }
        if( optimalSize == null ) {
            // can't find match for aspect ratio, so find closest one

            MyLog.d(TAG, "no preview size matches the aspect ratio");
            optimalSize = getClosestSize(sizesSupportedSurface, targetRatio);
        }
            MyLog.d(TAG, "chose optimalSize: " + optimalSize.getWidth() + " x " + optimalSize.getHeight());
            MyLog.d(TAG, "optimalSize ratio: " + ((double)optimalSize.getWidth() / optimalSize.getHeight()));

        if ( optimalSize != null ) {
            if (optimalSize.getWidth() > largestJpegCamera.getWidth() ||
                    optimalSize.getHeight() > largestJpegCamera.getHeight() ) {
                optimalSize =  largestJpegCamera;
            }
        }
        return optimalSize;
    }

    private double calculateTargetRatioForPreview(Point display_size) {
        double targetRatio = 0.0f;
        MyLog.d(TAG, "set preview aspect ratio from display size");
            // base target ratio from display size - means preview will fill the device's display as much as possible
            // but if the preview's aspect ratio differs from the actual photo/video size, the preview will show a cropped version of what is actually taken
            targetRatio = ((double)display_size.x) / (double)display_size.y;

        MyLog.d(TAG, "targetRatio: " + targetRatio);
        return targetRatio;
    }

    public Size getClosestSize(Size[] sizes, double targetRatio) {
        MyLog.d(TAG, "getClosestSize()");
        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        for(Size size : sizes) {
            double ratio = (double)size.getWidth() / size.getHeight();
            if( Math.abs(ratio - targetRatio) < minDiff ) {
                optimalSize = size;
                minDiff = Math.abs(ratio - targetRatio);
            }
        }
        return optimalSize;
    }

    /**
     * Configures the necessary {@link Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }

        mTextureView.setTransform(matrix);
     }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            mPreviewRequestBuilder.addTarget(surface);

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK,false);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,mAeCompensation);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                setAutoFlash(mPreviewRequestBuilder);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * Initiate a still image capture.
     */
    public void takePicture() {
        final  String TAG="takePicture";
        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Take a Picture..." );

        playCameraSound();
        mFile =  createImageFile();
        lockFocus();
        galleryAddPic(mFile);

    }

    public void takePhoto() {
        takePhotoBackground ph = new takePhotoBackground();
        new Thread(ph).start();
    }

    private  class  takePhotoBackground implements Runnable {
        public Handler mHandler;
        public takePhotoBackground() {

        }

        @Override
        public void run() {
            Looper.prepare();
            mHandler = new Handler() {
                public void handleMessage(Message msg) {
                    // process incoming messages here
                }
            };
            takePicture();
            Looper.loop();
        }
    }


    // This method is called on the main GUI thread.
    public void takePhoto2() {
        // This moves the time consuming operation to a child thread.
        Thread thread = new Thread(null,
                                    doBackgroundThreadProcessing,
                                    "Background");
        thread.start();
    }
    // Runnable that executes the background processing method.
    private Runnable doBackgroundThreadProcessing = new Runnable() {
        public void run() {
            takePicture();
        }
    };

    private void camera_sound_v1() {
        final  String TAG="CAMERA";

        SoundPool.Builder sp;
        SoundPool sp2;
        AudioAttributes.Builder aa;
        AudioAttributes aa2;

        final int soundId;

        Activity activity = getActivity();


        sp = new SoundPool.Builder();
        aa = new AudioAttributes.Builder();
        aa.setUsage(AudioAttributes.USAGE_MEDIA);
        aa.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION);

        aa2 = aa.build();

        sp.setAudioAttributes( aa2 );

        sp2 = sp.build();
        soundId = sp2.load(activity, R.raw.camera_sound, 1);

        sp2.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {

            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Voy a play sound");
                soundPool.play(soundId, 1f, 1f, 1, 0, 1f); //.play nos pide el int de nuestra canción, un //int float de volumen tanto derecho como izquierdo que van de 0.0=0% a 1.0=100%, el int de loop //donde -1 es reproducción en loop, 0 reproduce una vez y 1 repite sólo una vez, y por último tenemos //un int de Rate que va de 0.5 a 2 y modifica la velocidad de pitch donde 1.0 seria la velicidad normal
            }
        });



    }

    // simple version.
    private void camera_sound() {
        final  String TAG="CAMERA";
        final int soundId;

        Activity activity = getActivity();

        // first object attributes. construct and set.
        AudioAttributes aa = new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build();

        SoundPool sp = new SoundPool.Builder()
                            .setAudioAttributes( aa )
                            .build();

        soundId = sp.load(activity, R.raw.camera_sound, 1);

        sp.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {

            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Voy a play sound" );
                soundPool.play(soundId, 1f, 1f, 1, 0, 1f); //.play nos pide el int de nuestra canción, un //int float de volumen tanto derecho como izquierdo que van de 0.0=0% a 1.0=100%, el int de loop //donde -1 es reproducción en loop, 0 reproduce una vez y 1 repite sólo una vez, y por último tenemos //un int de Rate que va de 0.5 a 2 y modifica la velocidad de pitch donde 1.0 seria la velicidad normal
            }
        });



    }


    private void initCameraSound() {
        final  String TAG="initCameraSound";

        hasCameraSound = false;
        Activity activity = getActivity();

        // first object attributes. construct and set.
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setAudioAttributes( aa )
                .build();

        soundId = soundPool.load(activity, R.raw.camera_sound, 1);

        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {

            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"loaded camera sound" );
                hasCameraSound = true;
            }
        });

    }

    private void playCameraSound() {
        final  String TAG="playCameraSound";

            if (hasCameraSound) {
                MyLog.d(TAG, " <LN: " + Thread.currentThread().getStackTrace()[2].getLineNumber() + " > " + "Voy a play sound");
                soundPool.play(soundId, 1f, 1f, 1, 0, 1f); //.play nos pide el int de nuestra canción, un //int float de volumen tanto derecho como izquierdo que van de 0.0=0% a 1.0=100%, el int de loop //donde -1 es reproducción en loop, 0 reproduce una vez y 1 repite sólo una vez, y por último tenemos //un int de Rate que va de 0.5 a 2 y modifica la velocidad de pitch donde 1.0 seria la velicidad normal
            }


    }



    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                       CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(),
                                    mCaptureCallback,
                                    mBackgroundHandler);
            captureStillPicture();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }


            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            orientationImage = getOrientation(rotation);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    showToast("Saved: " + mFile);
                    MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Saved File Picture: "+ mFile.toString());
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        int r = (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
        MyLog.d (" <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"orientation: ", Integer.toString(r)+ " grados ");
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set( CaptureRequest.CONTROL_AF_TRIGGER,
                                        CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(),
                                    mCaptureCallback,
                                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                                mCaptureCallback,
                                                mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }



    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }


    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        public ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {


            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();

            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                // Pre-procesamos la imagen para crear la imagen reflejada de espejo
                Bitmap bitmap_img = reflexionImage(bytes,mImage.getWidth(),mImage.getHeight());
                byte[] bytes2 = convertBitmapToByteArray(bitmap_img);
                output.write(bytes2);
               // output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally{
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }


    }



    private File createImageFile() {
        // Create an image file name
        String storagedir = getActivity().getApplication().getApplicationContext().getResources().getString(R.string.cia_developer);
        String prefix = getActivity().getApplication().getApplicationContext().getResources().getString(R.string.app_name);

        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String imageFileName = prefix+"_" + timeStamp +".jpg";

            File path =Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);

            MyLog.d(TAG,"path: "+ path.toString());

            mFile = new File(path,storagedir );
            if (! mFile.exists()) {
                MyLog.d(TAG,"Voy a crear el directorio");
                if ( mFile.mkdir() ) MyLog.d(TAG,"Pude crearlo");
                else  MyLog.d(TAG,"no pude crearlo");

            }

            mFile = new File(mFile, imageFileName);


                mFile.createNewFile();
                MyLog.d("onActivityCreated","creando archivo: " + imageFileName);
                MyLog.d(TAG,"File: "+ mFile.toString());

                return mFile;

         } catch (IOException e) {
           e.printStackTrace();
         }



        return null;
    }

    private void galleryAddPic(File image ) {

        MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Agregando  la foto a la galleria...");
        String mCurrentPhotoPath = "file:" + image.getAbsolutePath();
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        //File f = new File(mCurrentPhotoPath);
        //Uri contentUri = Uri.fromFile(f);
        Uri contentUri = Uri.fromFile(image);
        mediaScanIntent.setData(contentUri);
        final Activity activity = getActivity();
        activity.getApplication().getApplicationContext().sendBroadcast(mediaScanIntent);
    }


    public static Bitmap reflexionImage(byte[] bytes_imagen , int w, int h) {


        // cargamos la imagen de origen

        Bitmap BitmapOrg = BitmapFactory.decodeByteArray(bytes_imagen,0,bytes_imagen.length);

        int width = BitmapOrg.getWidth();
        int height = BitmapOrg.getHeight();
        int newWidth = w;
        int newHeight = h;


        // para poder manipular la imagen
        // debemos crear una matriz de reflexion

        Matrix matrix = new Matrix();
        float[] reflexion_y_portrait  = {-1.0f,0.0f,0.0f,0.0f,1.0f,0.0f,0.0f,0.0f,1.0f};
        float[] reflexion_x_landscape = {1.0f,0.0f,0.0f,0.0f,-1.0f,0.0f,0.0f,0.0f,1.0f};
        matrix.setValues(reflexion_y_portrait);

        if (orientationImage == 0 || orientationImage == 180) {
            matrix.setValues(reflexion_x_landscape);
        }
            // volvemos a crear la imagen con los nuevos valores
        Bitmap reflexionBitmap = Bitmap.createBitmap(BitmapOrg, 0, 0,
                width, height, matrix, true);


        return (reflexionBitmap);

    }

    /**
     * @param bitmap
     * Bitmap object from which you want to get bytes
     * @return byte[] array of bytes by compressing the bitmap to PNG format <br/>
     * null if bitmap passed is null (or) failed to get bytes from the
     * bitmap
     */
    public static byte[] convertBitmapToByteArray(Bitmap bitmap) {
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


    public static Bitmap resizeImage(byte[] bytes_imagen , int resId, int w, int h) {

        // cargamos la imagen de origen
        //Bitmap BitmapOrg = BitmapFactory.decodeResource(ctx.getResources(), resId);
        Bitmap BitmapOrg = BitmapFactory.decodeByteArray(bytes_imagen,0,bytes_imagen.length);

        int width = BitmapOrg.getWidth();
        int height = BitmapOrg.getHeight();
        int newWidth = w;
        int newHeight = h;

        // calculamos el escalado de la imagen destino
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;

        // para poder manipular la imagen
        // debemos crear una matriz

        Matrix matrix = new Matrix();
        // resize the Bitmap
        matrix.postScale(scaleWidth, scaleHeight);

        // volvemos a crear la imagen con los nuevos valores
        Bitmap resizedBitmap = Bitmap.createBitmap(BitmapOrg, 0, 0,
                width, height, matrix, true);

        // si queremos poder mostrar nuestra imagen tenemos que crear un
        // objeto drawable y así asignarlo a un botón, imageview...
        //return new BitmapDrawable(resizedBitmap);
        return (resizedBitmap);

    }
    /**
     * Compares two {@code Size}s based on their areas.
     */
    public static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialogCamera extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent,
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

    public static class ConfirmationDialogStorage extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.permission_rationale_storage)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent,
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    REQUEST_STORAGE_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }
} /* Camera2BasicFragment */
