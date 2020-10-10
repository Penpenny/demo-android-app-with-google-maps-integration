package com.example.gpsproject

import java.util.*

/**
 * Model class for AutoComplete place suggestions.
 */
data class PlaceUiModel(
    val id: Long = UUID.randomUUID().mostSignificantBits,
    val placeId: String, // This is actually used as propertyId.
    val address: String,
    val state: String,
    val city: String,
    val zipCode: String,
    val longitude: Double,
    val latitude: Double
)
