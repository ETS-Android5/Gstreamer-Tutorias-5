package com.e.gt5;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

//import org.freedesktop.gstreamer.GStreamer;
import com.lamerman.FileDialog;

import org.freedesktop.gstreamer.GStreamer;
import org.freedesktop.gstreamer.tutorials.tutorial_5.GStreamerSurfaceView;

/**Problem 1: No URI handler implemented for "https"  gstreamer
 *Android Gstreamer not support https URI. Maybe can change playbin to souphttpsrc.
 *
 * The probable solution https://gstreamer.freedesktop.org/documentation/soup/souphttpsrc.html?gi-language=c#souphttpsrc-page
 *
 *
**/
/** Problem 2: Play Video is very lag.
*
*
*
* */
public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, OnSeekBarChangeListener{
    private native void nativeInit();     // Initialize native code, build pipeline, etc
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativeSetUri(String uri); // Set the URI of the media to play
    private native void nativePlay();     // Set pipeline to PLAYING
    private native void nativeSetPosition(int milliseconds); // Seek to the indicated position, in milliseconds
    private native void nativePause();    // Set pipeline to PAUSED
    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeSurfaceInit(Object surface); // A new surface is available
    private native void nativeSurfaceFinalize(); // Surface about to be destroyed
    private long native_custom_data;      // Native code will use this to keep private data

    private boolean is_playing_desired;   // Whether the user asked to go to PLAYING
    private int position;                 // Current position, reported by native code
    private int duration;                 // Current clip duration, reported by native code
    private boolean is_local_media;       // Whether this clip is stored locally or is being streamed
    private int desired_position;         // Position where the users wants to seek to
    private String mediaUri;              // URI of the clip being played

    private final String defaultMediaUri = "https://www.freedesktop.org/software/gstreamer-sdk/data/media/sintel_trailer-480p.ogv";

    static private final int PICK_FILE_CODE = 1;
    private String last_folder;

    private PowerManager.WakeLock wake_lock;
    public static   final  String PMW_LOCK_TAG   = "G5:GStreamer tutorial 5";
    // Called when the activity is first created.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize GStreamer and warn if it fails
        try {
            GStreamer.init(this);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        wake_lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, PMW_LOCK_TAG);
        wake_lock.setReferenceCounted(false);

        ImageButton play = (ImageButton) this.findViewById(R.id.button_play);
        play.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                is_playing_desired = true;
                wake_lock.acquire();
                nativePlay();
            }
        });

        ImageButton pause = (ImageButton) this.findViewById(R.id.button_stop);
        pause.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                is_playing_desired = false;
                wake_lock.release();
                nativePause();
            }
        });

        ImageButton select = (ImageButton) this.findViewById(R.id.button_select);
        select.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
//                Intent i = new Intent(getBaseContext(), FileDialog.class);
//                i.putExtra(FileDialog.START_PATH, last_folder);
//                startActivityForResult(i, PICK_FILE_CODE);
                //the sowFiles() is specify local file
                File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
                showFiles(file);
            }
        });
        Button bt1 =(Button)findViewById(R.id.bt1);
        bt1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                //specify http video
                //the issue is not fixed.
                mediaUri = defaultMediaUri;
                is_local_media = false;
                is_playing_desired = false;
                Log.i ("GStreamer", "  playing:" + is_playing_desired + " position:" + position +
                        " duration: " + duration + " uri: " + mediaUri);

                // Start with disabled buttons, until native code is initialized

                nativeSetUri (mediaUri);
            }
        });
        SurfaceView sv = (SurfaceView) this.findViewById(R.id.surface_video);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);

        SeekBar sb = (SeekBar) this.findViewById(R.id.seek_bar);
        sb.setOnSeekBarChangeListener(this);

        // Retrieve our previous state, or initialize it to default values
        if (savedInstanceState != null) {
            is_playing_desired = savedInstanceState.getBoolean("playing");
            position = savedInstanceState.getInt("position");
            duration = savedInstanceState.getInt("duration");
            mediaUri = savedInstanceState.getString("mediaUri");
            last_folder = savedInstanceState.getString("last_folder");
            Log.i ("GStreamer", "Activity created with saved state:");
        } else {
            is_playing_desired = false;
            position = duration = 0;
            last_folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath();
            Intent intent = getIntent();
            android.net.Uri uri = intent.getData();
            if (uri == null) {
                Log.i("GStreamer", "uri is null ");
                mediaUri = defaultMediaUri;
            }else {
                Log.i ("GStreamer", "Received URI: " + uri);
                if (uri.getScheme().equals("content")) {
                    android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                    cursor.moveToFirst();
                    int chooseIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA);
                    mediaUri = "file://" + cursor.getString(chooseIndex);
                   //cursor.getString(cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DATA));
                    //cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DATA));
                    cursor.close();
                } else
                    mediaUri = uri.toString();
            }
            Log.i ("GStreamer", "Activity created with no saved state:");
        }
        is_local_media = false;
        Log.i ("GStreamer", "  playing:" + is_playing_desired + " position:" + position +
                " duration: " + duration + " uri: " + mediaUri);

        // Start with disabled buttons, until native code is initialized
        this.findViewById(R.id.button_play).setEnabled(false);
        this.findViewById(R.id.button_stop).setEnabled(false);

        nativeInit();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {//沒有授權權限
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                        1);
            } else {//授權了權限
//                File file = new File("/storage/sdcard1");
//                showFiles(file);
            }
        } else {//6.0以下系統
//            File file = new File("/storage/sdcard1");
//            showFiles(file);
        }
    }

    protected void onSaveInstanceState (Bundle outState) {

        Log.d("GStreamer", "Saving state, playing:" + is_playing_desired + " position:" + position +
                " duration: " + duration + " uri: " + mediaUri);
        outState.putBoolean("playing", is_playing_desired);
        outState.putInt("position", position);
        outState.putInt("duration", duration);
        outState.putString("mediaUri", mediaUri);
        outState.putString("last_folder", last_folder);
        super.onSaveInstanceState(outState);
    }

    protected void onDestroy() {
        nativeFinalize();
        if (wake_lock.isHeld())
            wake_lock.release();
        super.onDestroy();
    }

    // Called from native code. This sets the content of the TextView from the UI thread.
    private void setMessage(final String message) {
        final TextView tv = (TextView) this.findViewById(R.id.textview_message);
        runOnUiThread (new Runnable() {
            public void run() {
                tv.setText(message);
            }
        });
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

//        switch (requestCode) {
//            case 0: {//手機內置外部存貯
//                // If request is cancelled, the result arrays are empty.
//                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {//授權同意
//                    File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
//                    showFiles(file);
//                } else {//授權被拒絕
//                }
//            }
//            break;
//            case 1://sd卡
//                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {//授權同意
//                    File file = new File("/storage/sdcard1");
//                    showFiles(file);
//                } else {//授權被拒絕
//
//                }
//                break;
//        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    // Set the URI to play, and record whether it is a local or remote file
    private void setMediaUri() {
        nativeSetUri (mediaUri);
        is_local_media = mediaUri.startsWith("file://");
    }

    // Called from native code. Native code calls this once it has created its pipeline and
    // the main loop is running, so it is ready to accept commands.
    private void onGStreamerInitialized () {
        Log.i ("GStreamer", "GStreamer initialized:");
        Log.i ("GStreamer", "  playing:" + is_playing_desired + " position:" + position + " uri: " + mediaUri);

        // Restore previous playing state
        setMediaUri ();
        nativeSetPosition (position);
        if (is_playing_desired) {
            nativePlay();
            wake_lock.acquire();
        } else {
            nativePause();
            wake_lock.release();
        }

        // Re-enable buttons, now that GStreamer is initialized
        final Activity activity = this;
        runOnUiThread(new Runnable() {
            public void run() {
                activity.findViewById(R.id.button_play).setEnabled(true);
                activity.findViewById(R.id.button_stop).setEnabled(true);
            }
        });
    }

    // The text widget acts as an slave for the seek bar, so it reflects what the seek bar shows, whether
    // it is an actual pipeline position or the position the user is currently dragging to.
    private void updateTimeWidget () {
        TextView tv = (TextView) this.findViewById(R.id.textview_time);
        SeekBar sb = (SeekBar) this.findViewById(R.id.seek_bar);
        int pos = sb.getProgress();

        SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        String message = df.format(new Date (pos)) + " / " + df.format(new Date (duration));
        tv.setText(message);
    }

    // Called from native code
    private void setCurrentPosition(final int position, final int duration) {
        final SeekBar sb = (SeekBar) this.findViewById(R.id.seek_bar);

        // Ignore position messages from the pipeline if the seek bar is being dragged
        if (sb.isPressed()) return;

        runOnUiThread (new Runnable() {
            public void run() {
                sb.setMax(duration);
                sb.setProgress(position);
                updateTimeWidget();
                sb.setEnabled(duration != 0);
            }
        });
        this.position = position;
        this.duration = duration;
    }

    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("tutorial-5");
        nativeClassInit();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        Log.d("GStreamer", "Surface changed to format " + format + " width "
                + width + " height " + height);
        nativeSurfaceInit (holder.getSurface());
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface created: " + holder.getSurface());
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface destroyed");
        nativeSurfaceFinalize ();
    }

    // Called from native code when the size of the media changes or is first detected.
    // Inform the video surface about the new size and recalculate the layout.
    private void onMediaSizeChanged (int width, int height) {
        Log.i ("GStreamer", "Media size changed to " + width + "x" + height);
        final GStreamerSurfaceView gsv = (GStreamerSurfaceView) this.findViewById(R.id.surface_video);
        gsv.media_width = width;
        gsv.media_height = height;
        runOnUiThread(new Runnable() {
            public void run() {
                gsv.requestLayout();
            }
        });
    }

    // The Seek Bar thumb has moved, either because the user dragged it or we have called setProgress()
    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
        if (fromUser == false) return;
        desired_position = progress;
        // If this is a local file, allow scrub seeking, this is, seek as soon as the slider is moved.
        if (is_local_media) nativeSetPosition(desired_position);
        updateTimeWidget();
    }

    // The user started dragging the Seek Bar thumb
    public void onStartTrackingTouch(SeekBar sb) {
        nativePause();
    }

    // The user released the Seek Bar thumb
    public void onStopTrackingTouch(SeekBar sb) {
        // If this is a remote file, scrub seeking is probably not going to work smoothly enough.
        // Therefore, perform only the seek when the slider is released.
        if (!is_local_media) nativeSetPosition(desired_position);
        if (is_playing_desired) nativePlay();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == RESULT_OK && requestCode == PICK_FILE_CODE) {
            mediaUri = "file://" + data.getStringExtra(FileDialog.RESULT_PATH);
            position = 0;
            last_folder = new File(data.getStringExtra(FileDialog.RESULT_PATH)).getParent();
            Log.i("GStreamer", "Setting last_folder to " + last_folder);
            setMediaUri();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    private void showFiles(File file) {
        if (file == null) return;
        File[] files = file.listFiles();
        if (files == null) return;
        for (File file1 : files) {
            if (file1.isDirectory()) {
                showFiles(file1);
            } else {
                String name = file1.getPath();
                int i = name.indexOf('.');
                if (i != -1) {
                    name = name.substring(i);
                    if (name.equalsIgnoreCase(".mp4")
                            || name.equalsIgnoreCase(".3gp")
                            || name.equalsIgnoreCase(".wmv")
                            || name.equalsIgnoreCase(".ts")
                            || name.equalsIgnoreCase(".rmvb")
                            || name.equalsIgnoreCase(".mov")
                            || name.equalsIgnoreCase(".m4v")
                            || name.equalsIgnoreCase(".avi")
                            || name.equalsIgnoreCase(".m3u8")
                            || name.equalsIgnoreCase(".3gpp")
                            || name.equalsIgnoreCase(".3gpp2")
                            || name.equalsIgnoreCase(".mkv")
                            || name.equalsIgnoreCase(".flv")
                            || name.equalsIgnoreCase(".divx")
                            || name.equalsIgnoreCase(".f4v")
                            || name.equalsIgnoreCase(".rm")
                            || name.equalsIgnoreCase(".asf")
                            || name.equalsIgnoreCase(".ram")
                            || name.equalsIgnoreCase(".mpg")
                            || name.equalsIgnoreCase(".v8")
                            || name.equalsIgnoreCase(".swf")
                            || name.equalsIgnoreCase(".m2v")
                            || name.equalsIgnoreCase(".asx")
                            || name.equalsIgnoreCase(".ra")
                            || name.equalsIgnoreCase(".ndivx")
                            || name.equalsIgnoreCase(".xvid")
                    ) {
                       // data.add(file1.getPath());
                        Log.i("GStreamer", "file1: " + file1.getPath());
                        mediaUri = "file://" + file1.getPath();
                        position = 0;
                        last_folder = new File(file1.getPath()).getParent();
                        Log.i("GStreamer", "Setting last_folder to " + last_folder);
                        setMediaUri();
                    }
                }
            }
        }
    }
}