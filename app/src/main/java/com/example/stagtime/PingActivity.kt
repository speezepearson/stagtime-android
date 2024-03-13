package com.example.stagtime

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.widget.addTextChangedListener
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.Gson
import java.time.Instant

data class PingInfo(
    var notes: String = "",
    var tags: Set<String> = setOf(),
)

fun loadPingDataForTime(context: Context, ping: Instant): PingInfo? {
    val prefs = context.getSharedPreferences("PingData", Context.MODE_PRIVATE)
    val pingJson = prefs.getString(ping.toString(), null)
    return if (pingJson == null) {
        null
    } else {
        Gson().fromJson(pingJson, PingInfo::class.java)
    }
}

fun savePingDataForTime(context: Context, ping: Instant, pingInfo: PingInfo) {
    with(context.getSharedPreferences("PingData", Context.MODE_PRIVATE).edit()) {
        putString(ping.toString(), Gson().toJson(pingInfo))
        apply()
    }
}

fun loadAllPingData(context: Context): Map<Instant, PingInfo> {
    val prefs = context.getSharedPreferences("PingData", Context.MODE_PRIVATE)
    val pingData = mutableMapOf<Instant, PingInfo>()
    prefs.all.forEach { (k, v) ->
        val ping = Instant.parse(k)
        val pingInfo = Gson().fromJson(v as String, PingInfo::class.java)
        pingData[ping] = pingInfo
    }
    return pingData
}

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

        pingInfo = loadPingDataForTime(this, ping) ?: PingInfo()

        redraw()

        val backButton = findViewById<Button>(R.id.button_back)
        backButton.setOnClickListener {
            val startMain = Intent(this, MainActivity::class.java)
            startActivity(startMain)
            finish()
        }

        val prevPingButton = findViewById<Button>(R.id.button_prev_ping)
        prevPingButton.setOnClickListener {
            val startNextPing = Intent(this, PingActivity::class.java)
            startNextPing.putExtra("PING_EPOCHSEC", Schedule.lastBefore(ping).epochSecond)
            startActivity(startNextPing)
            finish()
        }

        val nextPingButton = findViewById<Button>(R.id.button_next_ping)
        if (Schedule.firstAfter(ping) > Instant.now()) {
            nextPingButton.isEnabled = false
        }
        nextPingButton.setOnClickListener {
            val startNextPing = Intent(this, PingActivity::class.java)
            startNextPing.putExtra("PING_EPOCHSEC", Schedule.firstAfter(ping).epochSecond)
            startActivity(startNextPing)
            finish()
        }

        val newTagsField = findViewById<EditText>(R.id.edit_text_new_tag)
        newTagsField.addTextChangedListener { redraw() }
        newTagsField.requestFocus()

        val addTagsButton = findViewById<Button>(R.id.button_add_tag)
        addTagsButton.setOnClickListener {
            val newTags = newTagsField.text.split(",").map { it.trim() }.filter { it.isNotBlank() }
            for (newTag in newTags) {
                ensureTagExists(this, newTag)
            }
            pingInfo.tags += newTags
            newTagsField.text.clear()
            redraw()
        }

        val userInput = findViewById<EditText>(R.id.edit_text_user_input)
        userInput.setText(pingInfo.notes)
    }

    private fun redraw() {
        val buttonContainer = findViewById<FlexboxLayout>(R.id.tags_container)
        createFlagButtons(buttonContainer, getTags(this).keys.toSet().union(pingInfo.tags))

        val newTagsField = findViewById<EditText>(R.id.edit_text_new_tag)
        val searchResultsContainer = findViewById<FlexboxLayout>(R.id.tags_search_results_container)
        val queries =
            newTagsField.text.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        createFlagButtons(searchResultsContainer,
            getTags(this).keys.toSet().union(pingInfo.tags)
                .filter { tag -> queries.any { tag.lowercase().startsWith(it.lowercase()) } }
                .toSet()
        )
    }

    private fun createFlagButtons(container: FlexboxLayout, tags: Set<String>) {
        container.removeAllViews() // Clear existing views if any

        tags.sorted().forEach { tag ->
            val button = Button(this)
            button.text = tag
            button.textSize = 18f
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

            button.setBackgroundColor(if (pingInfo.tags.contains(tag)) Color.Green.toArgb() else Color.White.toArgb())
            button.setOnClickListener { toggleTag(tag) }
            container.addView(button)
        }
    }

    private fun toggleTag(tag: String) {
        if (pingInfo.tags.contains(tag)) {
            pingInfo.tags -= tag
        } else {
            pingInfo.tags += tag
        }
        redraw()
    }

    private fun savePingInfo() {
        pingInfo.notes = findViewById<EditText>(R.id.edit_text_user_input).text.toString()
        savePingDataForTime(this, ping, pingInfo)
    }

    override fun onPause() {
        super.onPause()
        savePingInfo()
    }
}
