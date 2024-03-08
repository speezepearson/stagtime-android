package com.example.stagtime

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.time.Instant


class PingActivity : Activity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ping)

        val pingSec = intent.getLongExtra("PING_EPOCHSEC", 0)
        val ping = Instant.ofEpochSecond(pingSec)
        val textView = findViewById<TextView>(R.id.text_view_ping_time)
        textView.text = ping.toString()

        val backButton = findViewById<Button>(R.id.button_back)
        backButton.setOnClickListener {
            finish()  // Closes the current activity and goes back to the previous one
        }

        val userInput = findViewById<EditText>(R.id.edit_text_user_input)
        userInput.setText(loadUserInput(ping))
    }


    private fun saveUserInput(ping: Instant) {
        val editText = findViewById<EditText>(R.id.edit_text_user_input)
        val userInput = editText.text.toString()

        val sharedPreferences = getSharedPreferences("PingData", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString(ping.toString(), userInput)
            apply()
        }
    }

    private fun loadUserInput(ping: Instant): String {
        val sharedPreferences = getSharedPreferences("PingData", Context.MODE_PRIVATE)
        return sharedPreferences.getString(ping.toString(), "") ?: ""
    }

    override fun onPause() {
        super.onPause()
        val ping = Instant.ofEpochSecond(intent.getLongExtra("PING_EPOCHSEC", 0))
        saveUserInput(ping)
    }
}
