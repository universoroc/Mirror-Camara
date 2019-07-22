package com.rocsoftware.mymirror;

/**
 * Created by Rogelio on 25/10/2016.
 */

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
//import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
//import android.location.Location;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video.VideoColumns;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

/** Provides access to the filesystem. Supports both standard and Storage
 *  Access Framework.
 */
public class StorageUtils {
    private static final String TAG = "StorageUtils";

    public static final int MEDIA_TYPE_IMAGE = 1;

    private Context context = null;
    private Uri last_media_scanned = null;

    // for testing:
    private boolean failed_to_scan = false;


    StorageUtils(Context context) {
        this.context = context;
    }



    public static File getBaseFolder() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    }

    /** Sends the intents to announce the new file to other Android applications. E.g., cloud storage applications like
     *  OwnCloud use this to listen for new photos/videos to automatically upload.
     */
    private void announceUri(Uri uri, boolean is_new_picture, boolean is_new_video) {
        
        MyLog.d(TAG, "announceUri: " + uri);
        if( is_new_picture ) {
            // note, we reference the string directly rather than via Camera.ACTION_NEW_PICTURE, as the latter class is now deprecated - but we still need to broadcast the string for other apps
            context.sendBroadcast(new Intent( "android.hardware.action.NEW_PICTURE" , uri));
            // for compatibility with some apps - apparently this is what used to be broadcast on Android?
            context.sendBroadcast(new Intent("com.android.camera.NEW_PICTURE", uri));

            if(MyLog.DEBUG ) // this code only used for debugging/logging
            {
                String[] CONTENT_PROJECTION = { Images.Media.DATA, Images.Media.DISPLAY_NAME, Images.Media.MIME_TYPE, Images.Media.SIZE, Images.Media.DATE_TAKEN, Images.Media.DATE_ADDED };
                Cursor c = context.getContentResolver().query(uri, CONTENT_PROJECTION, null, null, null);
                if( c == null ) {
                    if(MyLog.DEBUG )
                       MyLog.e(TAG, " <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Couldn't resolve given uri [1]: " + uri);
                }
                else if( !c.moveToFirst() ) {
                    if(MyLog.DEBUG )
                       MyLog.e(TAG, " <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+"Couldn't resolve given uri [2]: " + uri);
                }
                else {
                    String file_path = c.getString(c.getColumnIndex(Images.Media.DATA));
                    String file_name = c.getString(c.getColumnIndex(Images.Media.DISPLAY_NAME));
                    String mime_type = c.getString(c.getColumnIndex(Images.Media.MIME_TYPE));
                    long date_taken = c.getLong(c.getColumnIndex(Images.Media.DATE_TAKEN));
                    long date_added = c.getLong(c.getColumnIndex(Images.Media.DATE_ADDED));
                   MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "file_path: " + file_path);
                   MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "file_name: " + file_name);
                   MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "mime_type: " + mime_type);
                   MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "date_taken: " + date_taken);
                   MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "date_added: " + date_added);
                    c.close();
                }
            }
 			 
        }
        else if( is_new_video ) {
            context.sendBroadcast(new Intent("android.hardware.action.NEW_VIDEO", uri));
            
        }
    }
	 
    /** Sends a "broadcast" for the new file. This is necessary so that Android recognises the new file without needing a reboot:
     *  - So that they show up when connected to a PC using MTP.
     *  - For JPEGs, so that they show up in gallery applications.
     *  - This also calls announceUri() on the resultant Uri for the new file.
     *  - Note this should also be called after deleting a file.
     *  - Note that for DNG files, MediaScannerConnection.scanFile() doesn't result in the files being shown in gallery applications.
     *    This may well be intentional, since most gallery applications won't read DNG files anyway. But it's still important to
     *    call this function for DNGs, so that they show up on MTP.
     */
    public void broadcastFile(final File file, final boolean is_new_picture, final boolean is_new_video, final boolean set_last_scanned) {
        if(MyLog.DEBUG )
           MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "broadcastFile: " + file.getAbsolutePath());
        // note that the new method means that the new folder shows up as a file when connected to a PC via MTP (at least tested on Windows 8)
        if( file.isDirectory() ) {
            //this.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(file)));
            // ACTION_MEDIA_MOUNTED no longer allowed on Android 4.4! Gives: SecurityException: Permission Denial: not allowed to send broadcast android.intent.action.MEDIA_MOUNTED
            // note that we don't actually need to broadcast anything, the folder and contents appear straight away (both in Gallery on device, and on a PC when connecting via MTP)
            // also note that we definitely don't want to broadcast ACTION_MEDIA_SCANNER_SCAN_FILE or use scanFile() for folders, as this means the folder shows up as a file on a PC via MTP (and isn't fixed by rebooting!)
        }
        else {
            // both of these work fine, but using MediaScannerConnection.scanFile() seems to be preferred over sending an intent
            //context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
            failed_to_scan = true; // set to true until scanned okay
            if(MyLog.DEBUG )
               MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "failed_to_scan set to true");
            MediaScannerConnection.scanFile(context, new String[] { file.getAbsolutePath() }, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            failed_to_scan = false;
                            if(MyLog.DEBUG ) {
                               MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "Scanned " + path + ":");
                               MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "-> uri=" + uri);
                            }
                            if( set_last_scanned ) {
                                last_media_scanned = uri;
                                if(MyLog.DEBUG )
                                   MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "set last_media_scanned to " + last_media_scanned);
                            }
                            announceUri(uri, is_new_picture, is_new_video);

                            // it seems caller apps seem to prefer the content:// Uri rather than one based on a File
                            Activity activity = (Activity)context;
                            String action = activity.getIntent().getAction();
                            if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
                                if(MyLog.DEBUG )
                                   MyLog.d(TAG," <LN: "+Thread.currentThread().getStackTrace()[2].getLineNumber()+" > "+ "from video capture intent");
                                Intent output = new Intent();
                                output.setData(uri);
                                activity.setResult(Activity.RESULT_OK, output);
                                activity.finish();
                            }
                        }
                    }
            );
        }
    }

    // Storage Permissions
    private static final int REQUEST_CAMERA = 0;
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    public static void verifyCameraPermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA
            );
        }
    }
}
