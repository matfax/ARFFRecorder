package de.uni_passau.fim.arffrecorder;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

public class ArffService extends Service {

    private static final String SERVICE_NAME = "ArffService";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_STOP_SERVICE = "stop";
    private static final int HANDLER_START = 1;
    private static final int HANDLER_STOP = 2;
    private final IBinder arffBinder = new ArffLocalBinder();
    private final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
    private Boolean isNotified = false;
    private ArffServiceHandler arffServiceHandler;
    @Nullable
    private NotificationManager notificationManager;

    private void setupNotifications() {
        if (notificationManager == null) {
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, ArffRecorderActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                0);
        Intent stopSelf = new Intent(this, ArffService.class);
        stopSelf.setAction(ACTION_STOP_SERVICE);
        PendingIntent pStopSelf = PendingIntent.getService(this, 0, stopSelf, PendingIntent.FLAG_CANCEL_CURRENT);
        notificationBuilder
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(getText(R.string.app_name))
                .setWhen(System.currentTimeMillis())
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_notifications_black_24dp,
                        getString(R.string.pref_title_stop_service), pStopSelf)
                .setOngoing(true);
    }

    private void showNotification() {
        isNotified = true;
        notificationBuilder
                .setTicker(getText(R.string.pref_notification_service_connected))
                .setContentText(getText(R.string.pref_notification_service_connected));
        assert notificationManager != null;
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    @Override
    public void onCreate() {
        HandlerThread handlerThread = new HandlerThread("ArffThread", Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        arffServiceHandler = new ArffServiceHandler(looper);
        setupNotifications();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        if (ACTION_STOP_SERVICE.equals(intent.getAction())) {
            Log.d(SERVICE_NAME, "Notification called to stop service.");
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(ArffGlobals.ARFF_ENABLE_SERVICE, false);
            editor.apply();
            stopSelf();
            return START_NOT_STICKY;
        } else {
            Boolean showNotifications = preferences.getBoolean(ArffGlobals.ARFF_SERVICE_NOTIFICATIONS,
                    ArffGlobals.ARFF_DEFAULT_SERVICE_NOTIFICATIONS);
            if (showNotifications) {
                showNotification();
            }
            Message msg = arffServiceHandler.obtainMessage();
            msg.arg1 = startId;
            msg.arg2 = HANDLER_START;
            arffServiceHandler.sendMessage(msg);
            Log.i(SERVICE_NAME, "Service started.");
            return START_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        Message msg = arffServiceHandler.obtainMessage();
        msg.arg2 = HANDLER_STOP;
        arffServiceHandler.sendMessage(msg);
        assert notificationManager != null;
        notificationManager.cancel(NOTIFICATION_ID);
        Log.i(SERVICE_NAME, "Service was stopped.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return arffBinder;
    }

    private class ArffLocalBinder extends Binder {
        ArffService getService() {
            return ArffService.this;
        }
    }

    private final class ArffServiceHandler extends Handler implements SensorEventListener {

        private final SensorManager sensorManager;
        private final Sensor accelerometer;

        private float last_x, last_y, last_z;

        private Boolean isRunning = false;

        ArffServiceHandler(Looper looper) {
            super(looper);
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.arg2 == HANDLER_START) {
                isRunning = true;
                Message emptyMsg = arffServiceHandler.obtainMessage();
                sensorManager.registerListener(this, accelerometer,
                        SensorManager.SENSOR_DELAY_FASTEST);
                sendMessage(emptyMsg);
            } else if (msg.arg2 == HANDLER_STOP) {
                isRunning = false;
                sensorManager.unregisterListener(this);
                Log.i(SERVICE_NAME, "Service thread is to be stopped.");
            } else {
                try {
                    Log.i(SERVICE_NAME, String.format("Last measurement: x = %s, y = %s, z = %s",
                            last_x, last_y, last_z));
                    SharedPreferences preferences = PreferenceManager
                            .getDefaultSharedPreferences(getBaseContext());
                    Boolean showNotifications = preferences.getBoolean(
                            ArffGlobals.ARFF_SERVICE_NOTIFICATIONS,
                            ArffGlobals.ARFF_DEFAULT_SERVICE_NOTIFICATIONS);
                    if (showNotifications && !isNotified) {
                        showNotification();
                    } else if (!showNotifications && isNotified) {
                        isNotified = false;
                        assert notificationManager != null;
                        notificationManager.cancel(NOTIFICATION_ID);
                    }
                } catch (Exception e) {
                    Log.i(SERVICE_NAME, e.getMessage());
                }
                if (isRunning) {
                    Message emptyMsg = arffServiceHandler.obtainMessage();
                    sendMessageDelayed(emptyMsg, 1000L);
                } else {
                    Log.i(SERVICE_NAME, "Service thread stopped.");
                }
            }
        }

        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            Sensor changeSensor = sensorEvent.sensor;

            if (changeSensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                last_x = sensorEvent.values[0];
                last_y = sensorEvent.values[1];
                last_z = sensorEvent.values[2];

                long curTime = System.currentTimeMillis() - System.nanoTime() / 1000000;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }

}
