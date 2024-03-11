package com.example.stagtime

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import com.google.gson.Gson

data class TagInfo(
    val createdAtMillis: Long
)

fun ensureTagExists(context: Context, tag: String) {
    val sharedPreferences = context.getSharedPreferences("Tags", Context.MODE_PRIVATE)
    if (sharedPreferences.contains(tag)) {
        return
    }
    with(sharedPreferences.edit()) {
        putString(tag, Gson().toJson(TagInfo(System.currentTimeMillis())))
        apply()
    }
}

fun getTags(context: Context): Map<String, TagInfo> {
    val sharedPreferences = context.getSharedPreferences("Tags", Context.MODE_PRIVATE)
    return sharedPreferences.all.mapValues {
        Gson().fromJson(
            it.value as String,
            TagInfo::class.java
        )
    }
}


class EditTagsActivity : Activity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_tags)

        val backButton = findViewById<Button>(R.id.button_back)
        backButton.setOnClickListener {
            finish()  // Closes the current activity and goes back to the previous one
        }

        val userInput = findViewById<EditText>(R.id.edit_text_user_input)
        userInput.setText(loadUserInput())
    }

    private fun saveUserInput() {
        val editText = findViewById<EditText>(R.id.edit_text_user_input)
        val userInput = editText.text.toString()

        val tags = userInput
            .split(",", "\n")
            .map { s -> s.trim() }
            .filter { s -> s.isNotEmpty() }
            .toSet()
        for (tag in tags) {
            ensureTagExists(this, tag)
        }
    }

    private fun loadUserInput(): String {
        return getTags(this).keys.sorted().joinToString("\n")
    }

    override fun onPause() {
        super.onPause()
        saveUserInput()
    }
}
