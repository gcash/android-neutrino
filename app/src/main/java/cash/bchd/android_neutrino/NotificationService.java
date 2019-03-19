package cash.bchd.android_neutrino;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class NotificationService extends Service {
    public static final long CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(1);

    public static final long NOTIFICATION_INTERVAL = TimeUnit.DAYS.toMillis(45);

    // run on another Thread to avoid crash
    private Handler mHandler = new Handler();
    // timer handling
    private Timer mTimer = null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        // cancel if already existed
        if (mTimer != null) {
            mTimer.cancel();
        } else {
            // recreate new
            mTimer = new Timer();
        }
        // schedule task
        mTimer.scheduleAtFixedRate(new TimeDisplayTimerTask(), 0, CHECK_INTERVAL);
    }

    class TimeDisplayTimerTask extends TimerTask {

        @Override
        public void run() {
            // run on another thread
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    // display toast

                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    Settings settings = new Settings(prefs);

                    if (settings.getLastNotification() == 0) {
                        settings.setLastNotification(System.currentTimeMillis());
                        return;
                    }

                    if (settings.getLastNotification() + NOTIFICATION_INTERVAL < System.currentTimeMillis()) {

                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        intent.putExtra("launchLoveActivity", true);
                        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

                        Notification notification = new NotificationCompat.Builder(getApplicationContext(), "default")
                                .setSmallIcon(R.drawable.neutrino_small)
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .setContentTitle("Show Some Love To BCHD")
                                .setContentIntent(pendingIntent)
                                .setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_LIGHTS|Notification.DEFAULT_VIBRATE)
                                .setStyle(new NotificationCompat.BigTextStyle()
                                        .bigText("The Neutrino wallet is part of a larger suite of open source software which helps power the Bitcoin Cash network. The developers donate their time and expertise to bring awesome software to you for free."))
                                .build();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            CharSequence name = "default";
                            String description = "default channel";
                            int importance = NotificationManager.IMPORTANCE_DEFAULT;
                            NotificationChannel channel = new NotificationChannel("default", name, importance);
                            channel.setDescription(description);
                            // Register the channel with the system; you can't change the importance
                            // or other notification behaviors after this
                            NotificationManager notificationManager = getSystemService(NotificationManager.class);
                            notificationManager.createNotificationChannel(channel);
                            notificationManager.notify(1234, notification);
                        }

                        settings.setLastNotification(System.currentTimeMillis());
                    }
                }

            });
        }
    }
}