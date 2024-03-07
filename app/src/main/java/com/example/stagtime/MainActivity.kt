package com.example.stagtime

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat.getSystemService
import com.example.stagtime.ui.theme.StagTimeTheme
import kotlin.math.ln

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("SRP", "in onCreate")
        setContent {
            StagTimeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")
                }
            }
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

}

object NotificationScheduler {
    fun scheduleNextNotification(context: Context) {
        val lambda = 1.0 / (60 * 60 * 1000) // Mean of 1 hour converted to milliseconds
        val delay = (-Math.log(1 - Math.random()) / lambda).toLong()
        scheduleNotification(context, 300)
    }

    val KEY_TEXT_REPLY = "key_text_reply"

    private fun getNotification(context: Context, content: String): Notification {
        val remoteInput: RemoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).setLabel("Type your reply here").build()
        val replyIntent = Intent(context, ReplyReceiver::class.java)
        val replyPendingIntent: PendingIntent =
            PendingIntent.getBroadcast(context, 0, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val replyAction: Notification.Action = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_launcher_background), // Use an appropriate reply icon here
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        return Notification.Builder(context, "NOTIFICATION_CHANNEL_ID")
            .setContentTitle("Random Notification")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setChannelId("NOTIFICATION_CHANNEL_ID")
            .addAction(replyAction)
            .build()
    }

    private fun scheduleNotification(context: Context, delay: Long) {
        val notificationIntent = Intent(context, NotificationPublisher::class.java).apply {
            putExtra(NotificationPublisher.NOTIFICATION_ID, REPLY_NOTIFICATION_ID)
            putExtra(
                NotificationPublisher.NOTIFICATION,
                getNotification(context, "This is a random notification.")
            )
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(context, 0, notificationIntent, flags)

        val futureInMillis = SystemClock.elapsedRealtime() + delay
        val alarmManager = getSystemService(context, AlarmManager::class.java)
        Log.d("SRP", "scheduling notif")
        alarmManager?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, futureInMillis, pendingIntent)
        Log.d("SRP", "scheduled notif")
    }

}

const val REPLY_NOTIFICATION_ID = 100


class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SRP", "in onReceive")
        val bundle = RemoteInput.getResultsFromIntent(intent)
        if (bundle != null) {
            val replyText = bundle.getCharSequence(NotificationScheduler.KEY_TEXT_REPLY)
            // Use the reply text in your app
            Log.d("SRP", "got text: $replyText")

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(REPLY_NOTIFICATION_ID)
        }
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
//        NotificationScheduler.scheduleNextNotification(context)
    }

    companion object {
        const val NOTIFICATION_ID = "notification-id"
        const val NOTIFICATION = "notification"
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