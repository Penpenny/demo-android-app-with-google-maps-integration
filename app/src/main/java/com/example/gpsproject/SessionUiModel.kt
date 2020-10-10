package com.example.gpsproject

import java.util.*

data class SessionUiModel(
    val id: Long = UUID.randomUUID().mostSignificantBits,
    val locationPoints: List<LocationPointUiModel>
) {
    data class LocationPointUiModel(
        val id: Long = UUID.randomUUID().mostSignificantBits,
        val latitude: Double,
        val longitude: Double
    )
}
