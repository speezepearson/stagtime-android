package com.example.stagtime

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.FileProvider
import com.example.stagtime.ui.theme.StagTimeTheme
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.max

const val applicationId = "com.example.stagtime"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("SRP", "in onCreate")

        val nearbyPingTimes = mutableListOf(Schedule.lastBefore(Instant.now()))
        for (x in 1..5) {
            nearbyPingTimes.add(Schedule.lastBefore(nearbyPingTimes.last()))
        }

        setContentView(R.layout.activity_main)

        val listView = findViewById<ListView>(R.id.list_view_pings)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, nearbyPingTimes)
        listView.adapter = adapter


        listView.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, PingActivity::class.java)
            intent.putExtra("PING_EPOCHSEC", nearbyPingTimes[position].epochSecond)
            startActivity(intent)
        }

        val exportButton = findViewById<Button>(R.id.button_export)
        exportButton.setOnClickListener {
            val jsonBlob = exportPingNotesAsJson()
            val filename = "ping_notes.${Instant.now()}.json"
            saveToFile(filename, jsonBlob)
            Log.d("SRP", jsonBlob)
        }

        val loadPrevButton = findViewById<Button>(R.id.button_load_more)
        loadPrevButton.setOnClickListener {
            for (x in 1..5) {
                nearbyPingTimes.add(Schedule.lastBefore(nearbyPingTimes.last()))
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

        NotificationScheduler.scheduleNextNotification(this)

    }

    private fun saveToFile(filename: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
        } else {
            // For Android 9 and below, use the method described in the next section
        }
    }

    private fun exportPingNotesAsJson(): String {
        val sharedPreferences = getSharedPreferences("PingData", MODE_PRIVATE)
        val jsonArray = JSONArray()

        sharedPreferences.all.forEach { pingData ->
            Log.d("SRP", "key ${pingData.key} has val ${pingData.value}")
            val jsonObject = JSONObject()
            jsonObject.put("ping", pingData.key.toString())
            jsonObject.put("notes", pingData.value)
            jsonArray.put(jsonObject)
        }

        return jsonArray.toString()
    }

}

object Schedule {
    private const val RARITY = 3600L
    private fun pseudorandomPredicate(input: Long): Boolean {
        val bytes = input.toString().toByteArray()
        val sha256 = MessageDigest.getInstance("SHA-256")
        val hash = sha256.digest(bytes)

        val hashPartAsLong =
            hash.copyOfRange(0, 8).fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }
        return hashPartAsLong % RARITY == 0L
    }

    fun lastBefore(t: Instant): Instant {
        val nowSec = t.epochSecond
        var sec = nowSec - 1
        while (!pseudorandomPredicate(sec)) {
            sec -= 1
        }
        return Instant.ofEpochSecond(sec)
    }

    fun firstAfter(t: Instant): Instant {
        val nowSec = t.epochSecond
        var sec = nowSec + 1
        while (!pseudorandomPredicate(sec)) {
            sec += 1
        }
        return Instant.ofEpochSecond(sec)
    }
}

object NotificationScheduler {

    fun scheduleNextNotification(context: Context) {
        val t = Schedule.firstAfter(Instant.now())
        Log.d("SRP", "scheduling for $t (currently ${Instant.now()})")
        scheduleNotification(context, t)
    }

    private fun getNotification(context: Context, t: Instant): Notification {
        val pingIntent = Intent(context, PingActivity::class.java).apply {
            putExtra(PING_EPOCHSEC_EXTRANAME, t.epochSecond)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return Notification.Builder(context, "NOTIFICATION_CHANNEL_ID")
            .setContentTitle("Random Notification")
            .setContentText("Ping for $t")
            .setContentIntent(PendingIntent.getActivity(context, 0, pingIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setChannelId("NOTIFICATION_CHANNEL_ID")
            .build()
    }

    private fun scheduleNotification(context: Context, t: Instant) {
        val notificationIntent = Intent(context, NotificationPublisher::class.java).apply {
            putExtra(NotificationPublisher.NOTIFICATION_ID, 1)
            putExtra(NotificationPublisher.PING_EPOCHSEC, t.epochSecond)
            putExtra(
                NotificationPublisher.NOTIFICATION,
                getNotification(context, t)
            )
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(context, 0, notificationIntent, flags)

        val millisUntilPing = max(1, t.toEpochMilli() - Instant.now().toEpochMilli())
        val futureInMillis = SystemClock.elapsedRealtime() + millisUntilPing
        val alarmManager = getSystemService(context, AlarmManager::class.java)
        Log.d(
            "SRP",
            "scheduling notif: cur boot millis ${SystemClock.elapsedRealtime()}, waiting $millisUntilPing ms"
        )
        alarmManager?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, futureInMillis, pendingIntent)
        Log.d("SRP", "scheduled notif")
    }

}


class NotificationPublisher : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SRP", "in onReceive")
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification: Notification = intent.getParcelableExtra(NOTIFICATION)!!
        val id = intent.getIntExtra(NOTIFICATION_ID, 0)
        notificationManager.notify(id, notification)

        // Schedule the next notification
        NotificationScheduler.scheduleNextNotification(context)
    }

    companion object {
        const val NOTIFICATION_ID = "notification-id"
        const val NOTIFICATION = "notification"
        const val PING_EPOCHSEC = "ping-epochsec"
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello! $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    StagTimeTheme {
        Greeting("Android")
    }
}