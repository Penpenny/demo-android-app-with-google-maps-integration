package com.example.gpsproject

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    var recenterJob: Job? = null

    var isLocationTracking: Boolean = false

    private val _place = MutableLiveData<PlaceUiModel>()
    val place: LiveData<PlaceUiModel> = _place

    fun closeCurrentSession() {
        currentSessionPoint = SessionUiModel(locationPoints = listOf())
    }

    private val _recenter = MutableLiveData<Event<Boolean>>()
    val recenter: LiveData<Event<Boolean>> = _recenter

    private val _messageEvent: MutableLiveData<Event<String>> = MutableLiveData()
    val messageEvent: LiveData<Event<String>> = _messageEvent

    private val _toggleNavigation: MutableLiveData<Boolean> = MutableLiveData(false)
    val toggleNavigation: LiveData<Boolean> = _toggleNavigation

    private val _toggleSatellite: MutableLiveData<Boolean> = MutableLiveData()
    val toggleSatellite: LiveData<Boolean> = _toggleSatellite

    private val _isMovingAlongPath: MutableLiveData<Boolean> = MutableLiveData()
    val isMovingAlongPath: LiveData<Boolean> = _isMovingAlongPath

    private val _lastLocation: MutableLiveData<SessionUiModel.LocationPointUiModel> =
        MutableLiveData()
    val lastLocation: LiveData<SessionUiModel.LocationPointUiModel> = _lastLocation

    fun setToggleSatelliteButton(isSelected: Boolean) {
        _toggleSatellite.value = isSelected
    }

    fun setMovingAlongPath(req: Boolean) {
        _isMovingAlongPath.value = req
    }

    fun setLastLocation(latitude: Double, longitude: Double) {
        _lastLocation.value = SessionUiModel.LocationPointUiModel(
            latitude = latitude,
            longitude = longitude
        )
    }

    var currentSessionPoint: SessionUiModel =
        SessionUiModel(locationPoints = listOf())

    fun addTrackingLocationPoint(latitude: Double, longitude: Double) {
        val listOfLocationPoint = currentSessionPoint.locationPoints.toMutableList()
        listOfLocationPoint.add(
            SessionUiModel.LocationPointUiModel(
                latitude = latitude,
                longitude = longitude
            )
        )
        currentSessionPoint = currentSessionPoint.copy(locationPoints = listOfLocationPoint)
    }

    fun setToggleNavigationButton(isSelected: Boolean, showToast: Boolean = true) {
        if (isSelected) {
            if (showToast)
                _messageEvent.value = Event(TRACKING_ENABLED_TEXT)
        } else {
            if (showToast)
                _messageEvent.value = Event(TRACKING_DISABLED_TEXT)

            if (_isMovingAlongPath.value == false) {
                recenterOnMap()
            }
        }
        _toggleNavigation.value = isSelected
    }

    fun recenterOnMap() {
        recenterJob?.cancel()
        recenterJob = viewModelScope.launchSafely(
            block = {
                delay(250)
                _recenter.value = Event(true)
            }
        )
    }

    companion object {
        const val TRACKING_DISABLED_TEXT = "Tracking Disabled"
        const val TRACKING_ENABLED_TEXT = "Tracking Enabled"
    }
}


/**
 * Convenient method to launch Coroutines safely by wrapping in try/catch.
 */
fun CoroutineScope.launchSafely(
    block: suspend () -> Unit,
    error: (e: Exception) -> Unit = {}
): Job {
    return launch {
        try {
            block()
        } catch (jobCancelException: CancellationException) {

        } catch (exp: Exception) {
            error(exp)
        }
    }
}
