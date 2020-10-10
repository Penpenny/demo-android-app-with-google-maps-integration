package com.example.gpsproject

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.TextView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker

class MapMarkerInfoAdapter(private val context: Context) : GoogleMap.InfoWindowAdapter {

    override fun getInfoContents(marker: Marker?): View? {
        return null
    }

    override fun getInfoWindow(marker: Marker?): View? {
        marker?.snippet?.let {
            val view = (context as Activity).layoutInflater
                .inflate(R.layout.layout_marker_info, null)
            view.findViewById<TextView>(R.id.text_description).text = marker.snippet
            return view
        } ?: run {
            return null
        }
    }
}
