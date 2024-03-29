package com.example.stagtime

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import java.time.Instant

class PingAdapter(context: Context, private val pings: List<Instant>) :
    ArrayAdapter<Instant>(context, 0, pings) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val ping = pings[position]

        val view =
            convertView ?: LayoutInflater.from(context).inflate(R.layout.item_ping, parent, false)

        view.findViewById<ImageView>(R.id.image_view_check_mark).visibility =
            if (loadPingDataForTime(context, ping) == null) {
                View.GONE
            } else {
                View.VISIBLE
            }
        val textView = view.findViewById<TextView>(R.id.text_view_ping_time)
        textView.text = formatInstant(ping)
        return view
    }
}
