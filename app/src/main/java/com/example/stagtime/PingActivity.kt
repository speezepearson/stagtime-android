package com.example.stagtime

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.Gson
import java.time.Instant

data class PingInfo(
    var notes: String = "",
    var tags: Set<String> = setOf(),
)

class PingActivity : Activity() {

    private lateinit var ping: Instant
    private lateinit var pingInfo: PingInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ping)

        val pingSec = intent.getLongExtra("PING_EPOCHSEC", 0)
        ping = Instant.ofEpochSecond(pingSec)
        val textView = findViewById<TextView>(R.id.text_view_ping_time)
        textView.text = formatInstant(ping)

        val prevJson =
            getSharedPreferences("PingData", Context.MODE_PRIVATE).getString(ping.toString(), null)
        Log.d("SRP", "prevJson: " + prevJson)
        pingInfo = if (prevJson == null) {
            PingInfo()
        } else {
            Gson().fromJson(prevJson, PingInfo::class.java)
        }

        createFlagButtons()

        val backButton = findViewById<Button>(R.id.button_back)
        backButton.setOnClickListener {
            finish()  // Closes the current activity and goes back to the previous one
        }

        val userInput = findViewById<EditText>(R.id.edit_text_user_input)
        userInput.setText(pingInfo.notes)
    }

    private fun createFlagButtons() {
        val layout = findViewById<FlexboxLayout>(R.id.tags_container)
        layout.removeAllViews() // Clear existing views if any

        (getTags(this).union(pingInfo.tags)).sorted().forEach { tag ->
            val button = Button(this)
            button.text = tag
            button.textSize = 12f
            button.setPadding(20, 20, 20, 20)
            button.minWidth = 0
            button.minHeight = 0
            button.minimumHeight = 0
            button.minimumWidth = 0
            button.layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(2, 2, 2, 2)
            }


            updateButtonColor(tag, button)
            button.setOnClickListener { toggleTag(tag, button) }
            layout.addView(button)
        }
    }

    private fun toggleTag(tag: String, button: Button) {
        if (pingInfo.tags.contains(tag)) {
            pingInfo.tags -= tag
        } else {
            pingInfo.tags += tag
        }
        updateButtonColor(tag, button)
    }

    private fun updateButtonColor(tag: String, button: Button) {
        button.setBackgroundColor(if (pingInfo.tags.contains(tag)) Color.Green.toArgb() else Color.White.toArgb())
    }

    private fun savePingInfo() {
        pingInfo.notes = findViewById<EditText>(R.id.edit_text_user_input).text.toString()
        with(getSharedPreferences("PingData", Context.MODE_PRIVATE).edit()) {
            putString(ping.toString(), Gson().toJson(pingInfo))
            apply()
        }
    }

    override fun onPause() {
        super.onPause()
        savePingInfo()
    }
}
