package com.example.stagtime

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import com.google.gson.Gson
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow


object RequestCodes {
    const val SCHEDULE_EXACT_ALARM = 493457
}

object NotificationIds {
    const val PING = 5824598
    const val TEST = 5824599
}

object PendingIntentIds {
    const val SCHEDULE_NEXT_PING = 32593953
    const val OPEN_PING = 32593954
}


fun formatInstant(t: Instant): String {
    return DateTimeFormatter
        .ofPattern("uuuu-MM-dd HH:mm:ss")
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(t)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        clearAllPingData(this)
        Log.d("SRP", "in onCreate")

        val nearbyPingTimes =
            Schedule.before(Instant.now(), 24).toMutableList().apply { sortDescending() }

        setContentView(R.layout.activity_main)

        val listView = findViewById<ListView>(R.id.list_view_pings)
        val adapter = PingAdapter(this, nearbyPingTimes)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, PingActivity::class.java)
            intent.putExtra("PING_EPOCHSEC", nearbyPingTimes[position].epochSecond)
            startActivity(intent)
        }

        val exportButton = findViewById<Button>(R.id.button_export)
        exportButton.setOnClickListener {
            val jsonBlob = buildJsonExport()
            val filename = "stagtime-${Instant.now()}.json"
            saveToFile(filename, jsonBlob)
            Log.d("SRP", jsonBlob)
        }

        val testNotificationButton = findViewById<Button>(R.id.button_test_notification)
        testNotificationButton.setOnClickListener {
            if (NotificationScheduler.scheduleExact(
                    this,
                    Instant.now().plusSeconds(1),
                    Intent(this, NotificationPublisher::class.java).apply {
                        putExtra(NotificationPublisher.IS_TEST, true)
                    }
                )
            ) {
                Toast.makeText(
                    this,
                    "Test notification scheduled for 1s from now",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val editTagsButton = findViewById<Button>(R.id.button_edit_tags)
        editTagsButton.setOnClickListener {
            val intent = Intent(this, EditTagsActivity::class.java)
            startActivity(intent)
        }

        val loadPrevButton = findViewById<Button>(R.id.button_load_more)
        loadPrevButton.setOnClickListener {
            nearbyPingTimes.addAll(Schedule.before(nearbyPingTimes.last(), 24).sortedDescending())
            adapter.notifyDataSetChanged()
        }

        val refreshButton = findViewById<Button>(R.id.button_refresh)
        refreshButton.setOnClickListener {
            var nextPing = Schedule.firstAfter(nearbyPingTimes.first())
            while (nextPing.isBefore(Instant.now())) {
                nearbyPingTimes.add(0, nextPing)
                nextPing = Schedule.firstAfter(nearbyPingTimes.first())
            }
            adapter.notifyDataSetChanged()
        }

        val channel = NotificationChannel(
            "NOTIFICATION_CHANNEL_ID",
            "Notification Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notification Channel Description"
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        NotificationScheduler.ensureNextPingScheduled(this)

    }

    private fun saveToFile(filename: String, content: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            resolver.openOutputStream(it).use { outputStream ->
                outputStream?.write(content.toByteArray())
            }
            Toast.makeText(this, "File saved to Downloads!", Toast.LENGTH_SHORT).show()

        }
    }

    private fun buildJsonExport(): String {
        val res = Gson().toJson(
            mapOf(
                "pings" to loadAllPingData(this),
                "tags" to getTags(this),
            )
        )
        Log.d("SRP", "exporting: " + res.toString())
        return res
    }

}


class WeakPRNG(private var seed: Long) {
    init {
        seed *= 1664525
        seed += 1013904223
        seed = seed xor (seed ushr 12)
        seed = seed xor (seed shl 25)
        seed = seed xor (seed ushr 27)
        seed *= 1103515245
        seed += 12345
    }

    fun random(): Double {
        val a = 1664525L
        val c = 1013904223L
        val m = 2.0.pow(32).toLong()
        seed = ((a * seed + c) % m)
        if (seed < 0) seed += m
        return seed.toDouble() / m
    }

    fun poisson(mean: Double): Int {
        val l = exp(-mean)
        var k = 0
        var p = 1.0
        while (p > l) {
            k++
            p *= random()
        }
//        Log.d("SRP", "poisson($mean) = ${k - 1}; L=$l; p=${p}")
        return k - 1
    }
}


object Schedule {
    private fun pingsOnDayStartingAt(midnightSec: Long): List<Instant> {
        val prng = WeakPRNG(midnightSec)
        val numPings = prng.poisson(24.0)
        val offsetSecs = mutableSetOf<Long>()
        while (offsetSecs.size < numPings) {
            offsetSecs.add((86_400 * prng.random()).toLong())
        }
        return offsetSecs.sorted().map { Instant.ofEpochSecond(midnightSec + it) }
    }

    fun lastBefore(t: Instant): Instant {
        return before(t, 1).first()
    }

    fun firstAfter(t: Instant): Instant {
        return after(t, 1).first()
    }

    fun before(t: Instant, n: Int): List<Instant> {
        var midnightSec = t.epochSecond / 86400 * 86400
        val result = pingsOnDayStartingAt(midnightSec).filter { it.isBefore(t) }.toMutableList()
        while (result.size < n) {
            midnightSec -= 86400
            result.addAll(pingsOnDayStartingAt(midnightSec))
        }
        return result.sortedDescending().take(n).reversed()
    }

    fun after(t: Instant, n: Int): List<Instant> {
        var midnightSec = t.epochSecond / 86400 * 86400
        val result = pingsOnDayStartingAt(midnightSec).filter { it.isAfter(t) }.toMutableList()
        while (result.size < n) {
            midnightSec += 86400
            result.addAll(pingsOnDayStartingAt(midnightSec))
        }
        return result.sorted().take(n)
    }
}

object NotificationScheduler {

    fun ensureNextPingScheduled(context: Context) {
        val t = Schedule.firstAfter(Instant.now())
        Log.d("SRP", "scheduling for $t (currently ${Instant.now()})")
        scheduleNotification(context, t)
    }

    @SuppressLint("BatteryLife")
    fun scheduleExact(context: Context, t: Instant, intent: Intent): Boolean {

        val pendingIntent = PendingIntent.getBroadcast(
            context, PendingIntentIds.SCHEDULE_NEXT_PING, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(context, AlarmManager::class.java)
        if (!alarmManager!!.canScheduleExactAlarms()) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SCHEDULE_EXACT_ALARM
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("SRP", "requesting SCHEDULE_EXACT_ALARM")
                ActivityCompat.requestPermissions(
                    context as MainActivity,
                    arrayOf(Manifest.permission.SCHEDULE_EXACT_ALARM),
                    RequestCodes.SCHEDULE_EXACT_ALARM
                )
            }

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    context,
                    "Turn off battery optimization in app settings!",
                    Toast.LENGTH_SHORT
                ).show()
            }

            return false
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            max(t.toEpochMilli(), SystemClock.elapsedRealtime() + 1000),
            pendingIntent
        )
        return true
    }

    private fun scheduleNotification(context: Context, t: Instant) {
        val notificationIntent = Intent(context, NotificationPublisher::class.java).apply {
            putExtra(NotificationPublisher.PING_EPOCHSEC, t.epochSecond)
        }

        scheduleExact(context, t, notificationIntent)
        Log.d("SRP", "scheduled notif")
    }

}


class NotificationPublisher : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SRP", "in NotificationPublisher.onReceive")
        NotificationScheduler.ensureNextPingScheduled(context)
        if (intent.getBooleanExtra(IS_TEST, false)) {
            showNotification(
                context, NotificationIds.TEST,
                Notification.Builder(context, "NOTIFICATION_CHANNEL_ID")
                    .setContentTitle("Test Notification")
                    .setContentText("Hello from StagTime!")
                    .setSmallIcon(R.drawable.baseline_punch_clock_24)
                    .setChannelId("NOTIFICATION_CHANNEL_ID")
                    .setAutoCancel(true)
                    .build()
            )
            return
        }

        NotificationScheduler.ensureNextPingScheduled(context)
        val ping = Instant.ofEpochSecond(intent.getLongExtra(PING_EPOCHSEC, 0))
        showNotification(context, NotificationIds.PING, buildNotification(context, ping))
    }

    private fun showNotification(context: Context, id: Int, notification: Notification) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!notificationManager.areNotificationsEnabled()) {
            Log.d("SRP", "no notification permissions")
            val askIntent = Intent().apply {
                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                putExtra("app_package", context.packageName)
                putExtra("app_uid", context.applicationInfo.uid)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(askIntent)
            return
        }

        notificationManager.notify(id, notification)
    }

    private fun buildNotification(context: Context, t: Instant): Notification {
        val pingIntent = Intent(context, PingActivity::class.java).apply {
            putExtra("PING_EPOCHSEC", t.epochSecond)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return Notification.Builder(context, "NOTIFICATION_CHANNEL_ID")
            .setContentTitle("Random Notification")
            .setContentText("Ping for ${formatInstant(t)}")
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    PendingIntentIds.OPEN_PING,
                    pingIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setSmallIcon(R.drawable.baseline_punch_clock_24)
            .setChannelId("NOTIFICATION_CHANNEL_ID")
            .setAutoCancel(true)
            .build()
    }


    companion object {
        const val PING_EPOCHSEC = "ping-epochsec"
        const val IS_TEST = "is-test"
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            Log.d("SRP", "boot completed")
            NotificationScheduler.ensureNextPingScheduled(context)
        }
    }
}