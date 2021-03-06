package ch.japan_impact.japanimpactpos.views.scan;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import ch.japan_impact.japanimpactpos.R;

import java.io.Closeable;
import java.io.IOException;

/**
 * https://github.com/zxing/zxing/blob/master/android/src/com/google/zxing/client/android/BeepManager.java
 *
 * @author Valentin Quelquejay
 */
public class SoundAlertManager implements MediaPlayer.OnErrorListener, Closeable {

    private static final String TAG = SoundAlertManager.class.getSimpleName();

    private static final float BEEP_VOLUME = 0.10f;
    private static final long VIBRATE_DURATION_ONSUCCESS = 100;
    private static final long[] VIBRATE_DURATION_ONFAILURE = {0, 100, 50, 100};
    private final Context context;
    private final Vibrator vibrator;
    private MediaPlayer successMediaPlayer;
    private MediaPlayer failureMediaPlayer;

    public SoundAlertManager(Activity activity) {
        activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        this.context = activity.getApplicationContext();
        initMediaPlayer(activity);
        this.vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    private void initMediaPlayer(Context context) {
        successMediaPlayer = buildMediaPlayer(context, R.raw.scan_ok);
        failureMediaPlayer = buildMediaPlayer(context, R.raw.scan_error);
    }

    private MediaPlayer buildMediaPlayer(Context activity, int sound) {
        MediaPlayer mediaPlayer = new MediaPlayer();
        try (AssetFileDescriptor file = activity.getResources().openRawResourceFd(sound)) {
            mediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
            mediaPlayer.setOnErrorListener(this);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setLooping(false);
            mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
            mediaPlayer.prepare();
            return mediaPlayer;
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            mediaPlayer.release();
            return null;
        }
    }

    @Override
    public synchronized boolean onError(MediaPlayer mp, int what, int extra) {
        if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED && context instanceof Activity) {
            ((Activity) context).finish();
        } else {
            // possibly media player error, so release and recreate
            close();
            initMediaPlayer(context);
        }
        return true;
    }

    @Override
    public synchronized void close() {
        if (successMediaPlayer != null) {
            successMediaPlayer.release();
            successMediaPlayer = null;
        }
        if (failureMediaPlayer != null) {
            failureMediaPlayer.release();
            failureMediaPlayer = null;
        }
    }

    public synchronized void success() {
        successMediaPlayer.start();
        vibrator.vibrate(VIBRATE_DURATION_ONSUCCESS);
    }

    public synchronized void failure() {
        failureMediaPlayer.start();
        vibrator.vibrate(VIBRATE_DURATION_ONFAILURE, -1);
    }
}
