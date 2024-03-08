package com.example.stagtime

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText

fun getTags(context: Context): Set<String> {
    val sharedPreferences = context.getSharedPreferences("TagList", Context.MODE_PRIVATE)
    return sharedPreferences.getStringSet("TagList", setOf()) ?: setOf()
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

        val sharedPreferences = getSharedPreferences("TagList", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putStringSet(
                "TagList",
                userInput.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.toSet()
            )
            apply()
        }
    }

    private fun loadUserInput(): String {
        return getTags(this).sorted().joinToString(", ")
    }

    override fun onPause() {
        super.onPause()
        saveUserInput()
    }
}
