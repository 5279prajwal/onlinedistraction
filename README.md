val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
val endTime = System.currentTimeMillis()
val startTime = endTime - 1000 * 60 * 60 // last 1 hour
val stats = usageStatsManager.queryUsageStats(
    UsageStatsManager.INTERVAL_DAILY,
    startTime,
    endTime
)

class AppUsageTracker(context: Context) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun getUsageForApp(packageName: String): Long {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 * 60 * 24
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
        val appStats = stats.find { it.packageName == packageName }
        return appStats?.totalTimeInForeground ?: 0
    }
}

// UsagePermissionUtil.java
package com.example.app.monitor;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.os.Process;

public class UsagePermissionUtil {

    // returns true if user granted Usage Access for this app
    public static boolean hasUsageStatsPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    // open settings page where user can grant Usage Access manually
    public static Intent getUsageAccessSettingsIntent() {
        return new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
    }
}

if (!UsagePermissionUtil.hasUsageStatsPermission(this)) {
    startActivity(UsagePermissionUtil.getUsageAccessSettingsIntent());
}

// AppUsageTracker.java
package com.example.app.monitor;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.util.Log;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class AppUsageTracker {
    private static final String TAG = "AppUsageTracker";
    private final Context context;
    private final UsageStatsManager usageStatsManager;

    public AppUsageTracker(Context context) {
        this.context = context.getApplicationContext();
        usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    // start of today in millis
    public long getStartOfDayMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    // Get usage (milliseconds) for a single package between startTime and endTime
    public long getUsageForPackage(String packageName, long startTime, long endTime) {
        if (usageStatsManager == null) return 0L;
        try {
            Map<String, UsageStats> aggregated = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime);
            if (aggregated == null || aggregated.isEmpty()) return 0L;
            UsageStats us = aggregated.get(packageName);
            if (us == null) return 0L;
            // totalTimeInForeground is in milliseconds
            return us.getTotalTimeInForeground();
        } catch (Exception e) {
            Log.e(TAG, "getUsageForPackage error", e);
            return 0L;
        }
    }

    // helper: get usage since start of day
    public long getUsageSinceStartOfDay(String packageName) {
        long now = System.currentTimeMillis();
        return getUsageForPackage(packageName, getStartOfDayMillis(), now);
    }
}


// LimitStore.java
package com.example.app.monitor;

import android.content.Context;
import android.content.SharedPreferences;

public class LimitStore {
    private static final String PREFS = "app_limits_prefs";
    private final SharedPreferences prefs;

    public LimitStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // store limit in milliseconds; key format: limit_<packageName>
    public void setLimitForPackage(String packageName, long limitMillis) {
        prefs.edit().putLong("limit_" + packageName, limitMillis).apply();
    }

    // returns 0 if no limit set
    public long getLimitForPackage(String packageName) {
        return prefs.getLong("limit_" + packageName, 0L);
    }

    public void removeLimitForPackage(String packageName) {
        prefs.edit().remove("limit_" + packageName).apply();
    }
}

// UsageMonitorService.java
package com.example.app.monitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class UsageMonitorService extends Service {
    private static final String TAG = "UsageMonitorService";
    public static final String ACTION_APP_LIMIT_EXCEEDED = "com.example.app.ACTION_APP_LIMIT_EXCEEDED";
    public static final String EXTRA_PACKAGE = "packageName";
    public static final String EXTRA_USAGE = "usageMillis";

    private static final String CHANNEL_ID = "usage_monitor_channel";
    private static final int NOTIF_ID = 1001;

    private Handler handler;
    private Runnable pollRunnable;
    private AppUsageTracker usageTracker;
    private LimitStore limitStore;

    // Poll every 15 seconds (adjust during testing)
    private static final long POLL_INTERVAL_MS = 15_000L;

    // To avoid spamming, remember which packages already triggered for today
    private final Set<String> triggeredToday = new HashSet<>();
    private int lastDayOfMonth = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        usageTracker = new AppUsageTracker(this);
        limitStore = new LimitStore(this);
        handler = new Handler(getMainLooper());
        createNotificationChannelIfNeeded();
        startForeground(NOTIF_ID, buildNotification("Monitoring app usage"));

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    checkAllTrackedApps();
                } catch (Exception e) {
                    Log.e(TAG, "poll error", e);
                } finally {
                    handler.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        };
        handler.post(pollRunnable);
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Usage Monitor";
            String description = "Monitoring apps to detect limits";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(description);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FocusGuard") // rename to your app name
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setOngoing(true)
                .build();
    }

    private void checkAllTrackedApps() {
        // reset triggeredToday on day change
        int day = android.icu.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH);
        if (lastDayOfMonth != day) {
            triggeredToday.clear();
            lastDayOfMonth = day;
        }

        // Read tracked apps from SharedPreferences (simple CSV)
        Set<String> tracked = getTrackedApps();
        long now = System.currentTimeMillis();
        for (String pkg : tracked) {
            long usage = usageTracker.getUsageSinceStartOfDay(pkg);
            long limit = limitStore.getLimitForPackage(pkg);

            if (limit > 0 && usage >= limit && !triggeredToday.contains(pkg)) {
                triggeredToday.add(pkg);
                broadcastLimitExceeded(pkg, usage);
                Log.i(TAG, "Limit exceeded for " + pkg + ": " + usage + " >= " + limit);
            }
        }
    }

    private Set<String> getTrackedApps() {
        // The tracked apps CSV is stored at key "tracked_apps" in default prefs.
        // Member 1 should write this value (comma separated package names).
        // Replace with Room or proper storage later.
        String csv = getSharedPreferences("app_config", MODE_PRIVATE).getString("tracked_apps", "");
        if (csv.isEmpty()) return new HashSet<>();
        String[] arr = csv.split(",");
        Set<String> s = new HashSet<>();
        for (String a : arr) {
            String t = a.trim();
            if (!t.isEmpty()) s.add(t);
        }
        return s;
    }

    private void broadcastLimitExceeded(String packageName, long usageMillis) {
        Intent i = new Intent(ACTION_APP_LIMIT_EXCEEDED);
        i.putExtra(EXTRA_PACKAGE, packageName);
        i.putExtra(EXTRA_USAGE, usageMillis);
        sendBroadcast(i); // Member 4 registers a receiver to show the question overlay
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && pollRunnable != null) handler.removeCallbacks(pollRunnable);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

// MainActivity.java
package com.example.app.monitor;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    EditText etPackage, etMinutes;
    Button btnSaveLimit, btnStartService, btnOpenUsageSettings;
    LimitStore limitStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // create a simple layout with 2 EditTexts and 3 Buttons

        etPackage = findViewById(R.id.etPackage);
        etMinutes = findViewById(R.id.etMinutes);
        btnSaveLimit = findViewById(R.id.btnSaveLimit);
        btnStartService = findViewById(R.id.btnStartService);
        btnOpenUsageSettings = findViewById(R.id.btnOpenUsageSettings);

        limitStore = new LimitStore(this);

        btnOpenUsageSettings.setOnClickListener(v -> {
            if (!UsagePermissionUtil.hasUsageStatsPermission(this)) {
                startActivity(UsagePermissionUtil.getUsageAccessSettingsIntent());
            } else {
                Toast.makeText(this, "Usage Access already granted", Toast.LENGTH_SHORT).show();
            }
        });

        btnSaveLimit.setOnClickListener(v -> {
            String pkg = etPackage.getText().toString().trim();
            String minStr = etMinutes.getText().toString().trim();
            if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(minStr)) {
                Toast.makeText(this, "Enter package and minutes", Toast.LENGTH_SHORT).show();
                return;
            }
            long minutes = Long.parseLong(minStr);
            long millis = minutes * 60_000L;
            limitStore.setLimitForPackage(pkg, millis);
            addPackageToTrackedList(pkg);
            Toast.makeText(this, "Limit saved for " + pkg, Toast.LENGTH_SHORT).show();
        });

        btnStartService.setOnClickListener(v -> {
            Intent svc = new Intent(this, UsageMonitorService.class);
            // Starting foreground service requires startForegroundService on newer Android
            startForegroundServiceCompat(svc);
            Toast.makeText(this, "Monitor started", Toast.LENGTH_SHORT).show();
        });
    }

    private void startForegroundServiceCompat(Intent svc) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }

    private void addPackageToTrackedList(String pkg) {
        String key = "tracked_apps";
        String current = getSharedPreferences("app_config", MODE_PRIVATE).getString(key, "");
        if (current.contains(pkg)) return;
        String newCsv = current.isEmpty() ? pkg : current + "," + pkg;
        getSharedPreferences("app_config", MODE_PRIVATE).edit().putString(key, newCsv).apply();
    }
}

<!-- inside <manifest> -->
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>

<!-- inside <application> -->
<service
    android:name=".monitor.UsageMonitorService"
    android:exported="false" />

IntentFilter f = new IntentFilter(UsageMonitorService.ACTION_APP_LIMIT_EXCEEDED);
registerReceiver(new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        String pkg = intent.getStringExtra(UsageMonitorService.EXTRA_PACKAGE);
        long usage = intent.getLongExtra(UsageMonitorService.EXTRA_USAGE, 0L);
        Log.i("TEST", "Limit exceeded: " + pkg + " usage=" + usage);
        Toast.makeText(context, "Limit exceeded for " + pkg, Toast.LENGTH_LONG).show();
    }
}, f);
