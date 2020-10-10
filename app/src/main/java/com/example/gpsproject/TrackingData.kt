package com.example.gpsproject


/**
 * Object for passing data from [TrackingService].
 */
object TrackingData {
    var locationCallback: ((SessionUiModel.LocationPointUiModel, Float, Float) -> Unit)? = null
    var trackingRunningCallback: ((Boolean) -> Unit)? = null
    var sessionCompleteCallback: ((List<SessionUiModel.LocationPointUiModel>) -> Unit)? = null
//    var homeFragmentVisible = false
    var notTracking: ((String, Boolean) -> Unit)? = null
    var disableTrackingFromService: ((Boolean) -> Unit)? = null
    var trackingNotiYesClicked: (() -> Unit)? = null
    var shouldDisplayFirstDialog = false
    var shouldDisplaySecondDialog = false
    var dismissDialog: (() -> Unit)? = null

    // Tells whether the first in TrackingService class timer should be initialzed for notification
    var firstTimerShouldInit = true
}
