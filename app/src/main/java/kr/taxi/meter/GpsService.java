package kr.taxi.meter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.Locale;

/** 화면이 꺼지거나 다른 앱(내비게이션 등)을 켜도 위치를 계속 받아 두는 서비스 */
public class GpsService extends Service implements LocationListener {
    private static final ArrayList<double[]> buf = new ArrayList<>();
    static volatile long lastFixMs = 0;
    static volatile boolean running = false;
    private LocationManager lm;
    private PowerManager.WakeLock wl;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_NOT_STICKY;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("meter", "미터기 운행", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, "meter");
        } else {
            b = new Notification.Builder(this);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = b.setContentTitle("택시 미터기")
                .setContentText("위치를 받아 요금을 계산하고 있어요")
                .setSmallIcon(R.drawable.ic_stat)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(1, n);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "taxi:meter");
        wl.acquire();
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException ignored) { }
        running = true;
        return START_NOT_STICKY;
    }

    @Override
    public void onLocationChanged(Location l) {
        lastFixMs = System.currentTimeMillis();
        synchronized (buf) {
            if (buf.size() > 20000) buf.remove(0);
            buf.add(new double[]{l.getLatitude(), l.getLongitude(),
                    l.hasAccuracy() ? l.getAccuracy() : 999,
                    l.hasSpeed() ? l.getSpeed() : -1,
                    lastFixMs});
        }
    }

    static String drain() {
        StringBuilder sb = new StringBuilder("[");
        synchronized (buf) {
            for (int i = 0; i < buf.size(); i++) {
                double[] a = buf.get(i);
                if (i > 0) sb.append(',');
                sb.append(String.format(Locale.US, "[%.7f,%.7f,%.1f,%.2f,%d]", a[0], a[1], a[2], a[3], (long) a[4]));
            }
            buf.clear();
        }
        return sb.append(']').toString();
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }

    @Override
    public void onDestroy() {
        running = false;
        try { if (lm != null) lm.removeUpdates(this); } catch (Exception ignored) { }
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
