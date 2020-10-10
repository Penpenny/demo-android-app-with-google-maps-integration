package com.example.gpsproject

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.example.gpsproject.databinding.FragmentHomeBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions
import java.util.*

/**
 * Entry start_location of Cruze 4 Cash.
 */
@RuntimePermissions
class HomeFragment : Fragment(), OnMapReadyCallback {

    private val viewModel: HomeViewModel by viewModel()
    private val activityViewModel: ActivityViewModel by sharedViewModel()
    private lateinit var binding: FragmentHomeBinding

    private var dialog: AlertDialog? = null

    private var locationAndActivityPermissionDialog: AlertDialog? = null

    private var drawerMarketingTextView: TextView? = null

    private var avatarImage: ImageView? = null

    var flagRecenter = false

    private var mMap: GoogleMap? = null

    private var beaconMarker: Marker? = null

    private var currentPolyLine: Polyline? = null

    private var isAnimating = false

    private var cancelableCallback: GoogleMap.CancelableCallback? = null

    private var lastUserCircle: Circle? = null
    private val pulseDuration: Long = 3000
    private var lastPulseAnimator: ValueAnimator? = null

    private lateinit var geoCoder: Geocoder

    private var viewDestroyed = false

    private var bearing = 0f
    private var accuracy = 0f


    private var destinationMarker: Marker? = null

    private var isFragmentCreated = false

    private var previousTrackingLineExist = false

    private val markers: MutableList<Marker> = mutableListOf()

    private val TRACKING_NOTIFICATION_ID = 2121
    private val notificationManager by lazy {
        NotificationManagerCompat.from(requireContext())
    }

    private lateinit var tempUri: Uri

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val sessionPolyLines: MutableList<Polyline> = mutableListOf()

    private val foregroundServiceIntent by lazy {
        Intent(requireActivity(), TrackingService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
//        viewModel.getMileageSessionsAndProperties()

        startLocationServiceWithPermissionCheck()

        isAnimating = false
        cancelableCallback = object : GoogleMap.CancelableCallback {
            override fun onFinish() {
                isAnimating = false
            }

            override fun onCancel() {
                isAnimating = false
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_home,
            container,
            false
        )
        isFragmentCreated = true
        binding.lifecycleOwner = this
        binding.mapView.onCreate(savedInstanceState)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mapView.getMapAsync(this)

        TrackingData.sessionCompleteCallback = {
//            viewModel.getMileageSessionsAndProperties()

        }

        //viewModel.loadCountProperties()

        bindViews()
        setupViewModelObservers()
    }

    private fun showDialog(text: String, shouldDisableTracking: Boolean) {

        if (shouldDisableTracking) {
            viewModel.setToggleNavigationButton(isSelected = false)
            stopTracking()
        }

        dialog?.dismiss()
        val builder = MaterialAlertDialogBuilder(requireContext()).apply {
            setMessage(text)

            if (!shouldDisableTracking) {
                setPositiveButton("Yes") { dialog, _ ->
                    TrackingData.trackingNotiYesClicked?.invoke()
                    dialog.dismiss()
                }
                setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                    viewModel.setToggleNavigationButton(isSelected = false)
                    stopTracking()
                }
            } else {
                setPositiveButton("Ok") { dialog, _ ->
                    dialog.dismiss()
                }
            }

        }

        dialog = builder.create()
        dialog?.setCancelable(false)
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.show()
        dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isAllCaps = false
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isAllCaps = false
    }

    override fun onMapReady(map: GoogleMap) {

        currentPolyLine?.remove()

        currentPolyLine = null

        lastUserCircle = null

        viewModel.place.value?.let { place ->
            val latLng = LatLng(place.latitude, place.longitude)
            val cameraPosition =
                CameraPosition.Builder()
                    .target(latLng)
                    .zoom(19f)
                    .build()
            val cameraUpdateFactory = CameraUpdateFactory.newCameraPosition(cameraPosition)
            map.moveCamera(cameraUpdateFactory)
//            destinationMarker = markers.find { it.position == latLng }

        } ?: run {
            map.moveCamera(CameraUpdateFactory.zoomTo(19f))
        }
        mMap = map

        mMap?.setMaxZoomPreference(19.37f)
        mMap?.setMinZoomPreference(5.03f)

        beaconMarker = null

        mMap?.setMapStyle(
            MapStyleOptions.loadRawResourceStyle(
                requireContext(),
                R.raw.map_style_json
            )
        )
        mMap?.uiSettings?.isCompassEnabled = false

        mMap?.setOnMarkerClickListener { marker ->
            false
        }
        //To move google icon a little up from bottom
        mMap?.setPadding(0, convertDpToPx(52), 0, convertDpToPx(52))

        geoCoder = Geocoder(requireContext(), Locale.getDefault())

//        mMap?.isMyLocationEnabled = true
        mMap?.uiSettings?.isMyLocationButtonEnabled = false

        setCurrentLocation()


        if (viewModel.toggleSatellite.value == true) {
            mMap?.mapType = GoogleMap.MAP_TYPE_SATELLITE
        }

        // For Customize marker popup window so we add our custom layout to do so
        mMap?.setInfoWindowAdapter(MapMarkerInfoAdapter(requireContext()))


        mMap?.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE ||
                reason == GoogleMap.OnCameraMoveStartedListener.REASON_API_ANIMATION
            ) {
                viewModel.setMovingAlongPath(false)
            }

        }


        viewModel.place.observe(this, Observer { place ->
            place?.let {

                binding.buttonReCenter.visibility = View.VISIBLE


                val position = LatLng(place.latitude, place.longitude)

                val cameraPosition =
                    CameraPosition.Builder()
                        .target(position)
                        .zoom(20f)
                        .build()

                val cameraUpdateFactory = CameraUpdateFactory.newCameraPosition(cameraPosition)
                mMap?.moveCamera(cameraUpdateFactory)
                viewModel.setMovingAlongPath(false)

                markers.find {
                    it.position == LatLng(place.latitude, place.longitude)
                }?.showInfoWindow()
            }
        })

        viewModel.recenter.observe(this, Observer {
            it.consume()?.let { flag ->
                if (flag) {
                    recenter()
                }
            }
        })


        mMap?.setOnCameraIdleListener {

        }

    }

    private fun redrawPreviousPolyLine() {
        viewModel.currentSessionPoint.let { session ->
            // Remove polyline sessions on map
//            sessionPolyLines.forEach { polyline -> polyline.remove() }
//            sessionPolyLines.clear()

            currentPolyLine?.remove()

            // Draw polyline sessions on map

            val polylineOption = PolylineOptions()
                .color(ContextCompat.getColor(requireContext(), R.color.iconAccent))
            session.locationPoints.forEach { locationPoint ->
                polylineOption.add(LatLng(locationPoint.latitude, locationPoint.longitude))
            }
            currentPolyLine = mMap?.addPolyline(polylineOption)

//                    val bitmapDraw = ContextCompat.getDrawable(
//                        requireContext(),
//                        R.drawable.start_location
//                    ) as BitmapDrawable
//                    val capBitmap =
//                        Bitmap.createScaledBitmap(bitmapDraw.bitmap, 40, 40, false)
//
//                    polyline?.startCap = CustomCap(BitmapDescriptorFactory.fromBitmap(capBitmap))
//                    polyline?.endCap = CustomCap(BitmapDescriptorFactory.fromBitmap(capBitmap))

//                polyline?.let {
//                    sessionPolyLines.add(polyline)
//                }

        }
        previousTrackingLineExist = false

    }

    private fun setCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val latLng = LatLng(location.latitude, location.longitude)
                bearing = it.bearing
                accuracy = it.accuracy
                val cameraPosition =
                    CameraPosition.Builder()
                        .bearing(location.bearing)
                        .target(latLng)
                        .zoom(19f)
                        .build()
                val cameraUpdateFactory = CameraUpdateFactory.newCameraPosition(cameraPosition)
                mMap?.moveCamera(cameraUpdateFactory)
                viewModel.setLastLocation(it.latitude, it.longitude)
                recenter()
            }
        }
    }

    override fun onResume() {
        binding.mapView.onResume()
        if (!viewDestroyed && previousTrackingLineExist) {
            beaconMarker?.remove()
            beaconMarker = null
        }
        super.onResume()


        locationAndActivityPermissionDialog?.dismiss()
        locationAndActivityPermissionDialog = null
        if (!isGpsOn()) {
            val builder = MaterialAlertDialogBuilder(requireContext())
            builder.setMessage(
                "Turn On Location Services to Allow \"Cruze4Cash\" to Determine Your Location"
            )
            builder.setPositiveButton("OK") { dialog, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            locationAndActivityPermissionDialog = builder.create()
            locationAndActivityPermissionDialog?.setCancelable(false)
            locationAndActivityPermissionDialog?.setCanceledOnTouchOutside(false)
            locationAndActivityPermissionDialog?.show()
            locationAndActivityPermissionDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isAllCaps =
                false
            locationAndActivityPermissionDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isAllCaps =
                false
            return
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_DENIED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_DENIED
        ) {

            val builder = MaterialAlertDialogBuilder(requireContext())
            builder.setTitle("Location Disabled")
            builder.setMessage(
                "To get the best Cruze4Cash experience turn on Location"
            )
            builder.setPositiveButton("OK") { dialog, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().packageName, null)
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            locationAndActivityPermissionDialog = builder.create()
            locationAndActivityPermissionDialog?.setCancelable(false)
            locationAndActivityPermissionDialog?.setCanceledOnTouchOutside(false)
            locationAndActivityPermissionDialog?.show()
            locationAndActivityPermissionDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isAllCaps =
                false
            locationAndActivityPermissionDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isAllCaps =
                false
            return
        }

//        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
//            if (ContextCompat.checkSelfPermission(
//                    requireContext(),
//                    Manifest.permission.ACTIVITY_RECOGNITION
//                ) == PackageManager.PERMISSION_DENIED
//            ) {
//                val builder = MaterialAlertDialogBuilder(requireContext())
//                builder.setTitle("Physical Activity Disabled")
//                builder.setMessage(
//                    "To get the best Cruze4Cash experience turn on Physical Activity"
//                )
//                builder.setPositiveButton("OK") { dialog, _ ->
//                    val intent = Intent(
//                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
//                        Uri.fromParts("package", requireContext().packageName, null)
//                    )
//                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                    startActivity(intent)
//                }
//                locationAndActivityPermissionDialog = builder.create()
//                locationAndActivityPermissionDialog?.setCancelable(false)
//                locationAndActivityPermissionDialog?.setCanceledOnTouchOutside(false)
//                locationAndActivityPermissionDialog?.show()
//                locationAndActivityPermissionDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isAllCaps =
//                    false
//                locationAndActivityPermissionDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isAllCaps =
//                    false
//                return
//            }
//        }
        if (viewModel.lastLocation.value == null || !TrackingService.isServiceRunning) {
            startLocationServiceWithPermissionCheck()
        }

    }

    override fun onPause() {
        binding.mapView.onPause()
        if (viewModel.currentSessionPoint.locationPoints.size > 0) {
            previousTrackingLineExist = true
            currentPolyLine?.remove()
            currentPolyLine = null

            beaconMarker?.remove()
            beaconMarker = null

            lastUserCircle?.remove()
            lastUserCircle = null

            viewDestroyed = false
        }
        super.onPause()
    }

    override fun onDestroyView() {
        viewModel.setMovingAlongPath(true)
        currentPolyLine?.remove()
        MarkerAnimation.locationDataCallback = null
        currentPolyLine = null
        isAnimating = false
        viewDestroyed = true
        if (viewModel.currentSessionPoint.locationPoints.size > 0) {
            previousTrackingLineExist = true
        }
        super.onDestroyView()
    }

    override fun onDestroy() {
        binding.mapView.onDestroy()
        if (viewModel.isLocationTracking) {
            stopTracking()
        }
        requireActivity().stopService(foregroundServiceIntent)
        MarkerAnimation.locationDataCallback = null
        TrackingData.locationCallback = null
        TrackingData.disableTrackingFromService = null
//        TrackingData.notTracking = null
        //removeDetectActivity()
        TrackingData.sessionCompleteCallback = null
        super.onDestroy()
    }

    override fun onLowMemory() {
        binding.mapView.onLowMemory()
        super.onLowMemory()
    }


    private fun bindViews() {


        binding.mapTint.setOnClickListener {
            binding.mapTint.visibility = View.GONE
            binding.mapView.requestFocus()
        }

        binding.buttonNavigation.setOnClickListener {
            val isNavigationSelected = viewModel.toggleNavigation.value ?: false
            if (!isNavigationSelected) {
                if (isGpsOn()) {
//                    createTrackingNotification()
                    startTracking()
                    restrictScreenTimeout()
                    viewModel.setToggleNavigationButton(!isNavigationSelected)
                } else {
                    // GPS provider is not enabled
                    val builder = MaterialAlertDialogBuilder(requireContext())
                    builder.setTitle("Info")
                    builder.setMessage(
                        "Looks like you have not given GPS permissions. Please give GPS" +
                                " permissions or activate your GPS location" +
                                "and return back to the app."
                    )
                    builder.setPositiveButton("OK") { dialog, _ ->
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        dialog.dismiss()
                    }
                    builder.show()
                }
            } else {
                // stop location updates
                if (isNetworkDataOn()) {
                    viewModel.setToggleNavigationButton(!isNavigationSelected)
                    if (viewModel.isLocationTracking) {
                        stopTracking()
//                        stopTrackingNotification()
                    }
                } else {
                    // GPS provider is not enabled
                    val builder = MaterialAlertDialogBuilder(requireContext())
                    builder.setTitle("Info")
                    builder.setMessage(
                        "Looks like your wifi is off. Please turn on Wifi from Setting" +
                                "and return back to the app."
                    )
                    builder.setPositiveButton("OK") { dialog, _ ->
                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        dialog.dismiss()
                    }
                    builder.show()
                }
            }
        }

        binding.buttonSatellite.setOnClickListener {
            val isSatelliteSelected = viewModel.toggleSatellite.value ?: false
            if (!isSatelliteSelected) {
                mMap?.let {
                    it.mapType = GoogleMap.MAP_TYPE_SATELLITE
                }
            } else {
                mMap?.let {
                    it.mapType = GoogleMap.MAP_TYPE_NORMAL
                }
            }
            viewModel.setToggleSatelliteButton(!isSatelliteSelected)
        }

        binding.buttonReCenter.setOnClickListener {
            recenter()
        }
    }

    fun recenter() {
        isAnimating = false
        viewModel.setMovingAlongPath(true)
        flagRecenter = true
        viewModel.lastLocation.value?.let {
            viewModel.setLastLocation(latitude = it.latitude, longitude = it.longitude)
        } ?: run {
            setCurrentLocation()
        }

        binding.buttonReCenter.visibility = View.INVISIBLE

    }

    private fun stopTrackingNotification() {
        notificationManager.cancel(TRACKING_NOTIFICATION_ID)
    }

    private fun isNetworkDataOn(): Boolean {
        val locationManager =
            requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun isGpsOn(): Boolean {
        val locationManager =
            requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun selectedButton(button: MaterialButton) {
        button.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.iconAccent))

        button.iconTint =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
    }

    private fun unSelectedButton(button: MaterialButton) {
        button.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))

        button.iconTint =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.iconAccent))
    }

    @NeedsPermission(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    fun startLocationService() {
        viewModel.setMovingAlongPath(true)
        setCurrentLocation()

        ContextCompat.startForegroundService(
            requireContext(),
            foregroundServiceIntent
        )

        TrackingData.disableTrackingFromService = {
            if (it) {
                requireActivity().runOnUiThread {
//                    activityViewModel.stopTracking(showToast = false)
                    viewModel.setToggleNavigationButton(isSelected = false, showToast = false)
                    stopTracking()
                }
            }
        }

        TrackingData.locationCallback = { lastLocation, bearing, accuracy ->
            this.bearing = bearing
            this.accuracy = accuracy
            viewModel.setLastLocation(
                latitude = lastLocation.latitude,
                longitude = lastLocation.longitude
            )

            if (viewModel.isLocationTracking) {
                //TODO (Uncomment)

//                if (DetectedActivityData.activityData?.type == DetectedActivity.IN_VEHICLE ||
//                    DetectedActivityData.activityData?.type == DetectedActivity.ON_BICYCLE
//                ) {


                viewModel.addTrackingLocationPoint(
                    latitude = lastLocation.latitude,
                    longitude = lastLocation.longitude
                )


//                }
            }
        }

//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
//            requestDetectActivityWithPermissionCheck()
//        } else {
//            requestDetectActivity()
//        }
    }

    private fun startTracking() {
        recenter()
        viewModel.isLocationTracking = true
        TrackingData.trackingRunningCallback?.invoke(true)
    }

    private fun restrictScreenTimeout() {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun allowScreenTimeout() {
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun stopTracking() {
//        startMarker?.remove()
//        viewModel.clearLastLocation()
//        removeDetectActivity()
//        viewModel.clearLastTrackingPoints()
        sessionPolyLines.forEach { polyline -> polyline.remove() }
        sessionPolyLines.clear()
        currentPolyLine?.remove()
        currentPolyLine = null
        TrackingData.trackingRunningCallback?.invoke(false)
        viewModel.isLocationTracking = false
        viewModel.closeCurrentSession()
        allowScreenTimeout()
    }

//    @NeedsPermission(
//        Manifest.permission.ACTIVITY_RECOGNITION
//    )
//    fun requestDetectActivity() {
//        val task = mActivityRecognitionClient.requestActivityUpdates(
//            ACTIVITY_DETECTION_INTERVAL_IN_MILLISECONDS,
//            mPendingIntent
//        )
//        task?.addOnSuccessListener {
//            Timber.d("Successfully requested activity updates for activity recognition")
//        }
//        task?.addOnFailureListener {
//            Timber.e(it)
//        }
//    }
//
//    private fun removeDetectActivity() {
//        val task = mActivityRecognitionClient.removeActivityUpdates(
//            mPendingIntent
//        )
//        task?.addOnSuccessListener {
//            Timber.d("Removed activity updates Successfully for activity recognition")
//        }
//
//        task?.addOnFailureListener {
//            Timber.e(it)
//        }
//    }

    private fun setupViewModelObservers() {

        viewModel.toggleNavigation.observe(this, Observer { isSelected ->
            if (isSelected == true) {
                selectedButton(binding.buttonNavigation)
            } else {
                unSelectedButton(binding.buttonNavigation)
            }
        })

        viewModel.toggleSatellite.observe(this, Observer { isSelected ->
            if (isSelected == true) {
                selectedButton(binding.buttonSatellite)
            } else {
                unSelectedButton(binding.buttonSatellite)
            }
        })

        viewModel.messageEvent.observe(this, Observer {
            it.consume()?.let { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        })

        viewModel.isMovingAlongPath.observe(this, Observer {
            it?.let {
                if (it) {
                    binding.buttonReCenter.visibility = View.INVISIBLE
                } else {

//                    binding.buttonReCenter.visibility = View.VISIBLE
                }
            }
        })

        viewModel.lastLocation.observe(this, Observer {
            it?.let { location ->

                if (lastUserCircle == null) {
                    addPulsatingEffect(LatLng(location.latitude, location.longitude), accuracy)
                }
//                lastUserCircle?.let {
//                    MarkerAnimation.animateCircleToICS(lastUserCircle,LatLng(location.latitude, location.longitude),LatLngInterpolator.LinearFixed())
//                }

                if (beaconMarker == null) {
                    val bitmapDraw = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.current_location
                    ) as BitmapDrawable
                    val smallMarker = Bitmap
                        .createScaledBitmap(
                            bitmapDraw.bitmap,
                            convertDpToPx(30),
                            convertDpToPx(30),
                            false
                        )

                    val position = LatLng(location.latitude, location.longitude)
                    val markerOption = MarkerOptions()
                        .position(position)
                        .flat(true)
                        .anchor(0.5f, 0.5f)
                        .icon(BitmapDescriptorFactory.fromBitmap(smallMarker))

                    beaconMarker = mMap?.addMarker(markerOption)
                }
                beaconMarker?.let {
                    MarkerAnimation.animateMarkerToICS(
                        beaconMarker,
                        LatLng(location.latitude, location.longitude),
                        LatLngInterpolator.LinearFixed()
                    )
                }


//                beaconMarker?.position = LatLng(location.latitude,location.longitude)

                if (mMap?.projection?.visibleRegion?.latLngBounds?.contains(
                        LatLng(
                            location.latitude,
                            location.longitude
                        )
                    ) == false
                ) {
                    binding.buttonReCenter.visibility = View.VISIBLE
                }
                if (viewModel.isMovingAlongPath.value == true) {

                    if (!isAnimating) {
                        binding.buttonReCenter.visibility = View.INVISIBLE

                        var animationDuration = 3000
                        if (flagRecenter) {
                            animationDuration = 1000
                            flagRecenter = false
                        }


                        val latLng = LatLng(location.latitude, location.longitude)
                        val cameraPosition =
                            CameraPosition.Builder()
                                .bearing(bearing)
                                .target(latLng)
                                .zoom(19f)
                                .build()
                        val cameraUpdateFactory =
                            CameraUpdateFactory.newCameraPosition(cameraPosition)
                        isAnimating = true
                        mMap?.animateCamera(
                            cameraUpdateFactory,
                            animationDuration,
                            cancelableCallback
                        )
                    }
                }
            }
        })

        MarkerAnimation.locationDataCallback = { it ->

            lastUserCircle?.center = it

            if (!previousTrackingLineExist)
                if (beaconMarker != null) {

                    it?.let { latLng ->
                        if (viewModel.isLocationTracking) {
                            //TODO (Uncomment)

//                            if (DetectedActivityData.activityData?.type == DetectedActivity.IN_VEHICLE ||
//                                DetectedActivityData.activityData?.type == DetectedActivity.ON_BICYCLE
//                            ) {

//                        viewModel.addTrackingLocationPoint(
//                            latitude = it.latitude,
//                            longitude = it.longitude
//                        )

                            if (currentPolyLine == null) {
                                val polylineOption = PolylineOptions()
                                    .color(
                                        ContextCompat.getColor(
                                            requireContext(),
                                            R.color.iconAccent
                                        )
                                    )
                                polylineOption.add(it)
                                currentPolyLine = mMap?.addPolyline(polylineOption)

                            } else {
                                val points = currentPolyLine?.points
                                val pointAlreadyAdded = points?.any {
                                    it?.latitude == latLng.latitude && it.longitude == latLng.longitude
                                }

                                if (pointAlreadyAdded == false) {
                                    points.add(latLng)
                                    currentPolyLine?.points = points
                                }

                            }


                            //}
                        }
                    }
                }

        }


    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        onRequestPermissionsResult(requestCode, grantResults)
    }

    private fun convertDpToPx(dp: Int): Int {
        return (dp.toFloat() * requireContext().resources.displayMetrics.density).toInt()
    }

    companion object {
        const val ACTIVITY_DETECTION_INTERVAL_IN_MILLISECONDS = 0L
        const val POSITION_CAPTURE_FROM_CAMERA = 0
        const val POSITION_CAPTURE_FROM_GALLERY = 1

        const val OPEN_CAMERA_FOR_RESULT = 900
        const val OPEN_GALLERY_FOR_RESULT = 901
    }

    override fun onDetach() {
        super.onDetach()
        notificationManager.cancel(TRACKING_NOTIFICATION_ID)
    }


    private fun addPulsatingEffect(userLatlng: LatLng, accuracy: Float) {
        if (lastPulseAnimator != null) {
            lastPulseAnimator!!.cancel()
        }
        if (lastUserCircle != null)
            lastUserCircle!!.center = userLatlng
        lastPulseAnimator = valueAnimate(
            getDisplayPulseRadius(accuracy),
            pulseDuration,
            ValueAnimator.AnimatorUpdateListener { animation ->
                if (lastUserCircle != null) {
                    lastUserCircle!!.radius =
                        (getDisplayPulseRadius(animation.animatedValue as Float)).toDouble()
                    lastUserCircle!!.fillColor = adjustAlpha(
                        lastUserCircle!!.fillColor,
                        1 - animation.animatedFraction
                    )

                } else {
                    lastUserCircle = mMap?.addCircle(
                        CircleOptions()
                            .center(userLatlng)
                            .radius((getDisplayPulseRadius(animation.animatedValue as Float)).toDouble())
                            .strokeWidth(0f)
                            .fillColor(Color.argb(50, 0, 144, 255))
                    )
                }
            })
    }

    private fun valueAnimate(
        accuracy: Float,
        duration: Long,
        updateListener: ValueAnimator.AnimatorUpdateListener?
    ): ValueAnimator? {
        val va = ValueAnimator.ofFloat(0f, accuracy)
        va.duration = duration
        va.addUpdateListener(updateListener)
        va.repeatCount = ValueAnimator.INFINITE
        va.repeatMode = ValueAnimator.RESTART
        va.start()
        return va
    }

    private fun getDisplayPulseRadius(radius: Float): Float {
        return radius

        mMap?.let {
            val diff: Float = it.maxZoomLevel - it.cameraPosition.zoom
            if (diff < 3) return radius
            if (diff < 3.7) return radius * (diff / 2)
            if (diff < 4.5) return radius * diff
            if (diff < 5.5) return radius * diff * 1.5f
            if (diff < 7) return radius * diff * 2f
            if (diff < 7.8) return radius * diff * 3.5f
            if (diff < 8.5) return (radius * diff) * 5
            if (diff < 10) return radius * diff * 10f
            if (diff < 12) return radius * diff * 18f
            if (diff < 13) return radius * diff * 28f
            if (diff < 16) return radius * diff * 40f
            return if (diff < 18) radius * diff * 60 else radius * diff * 80
        } ?: run {
            return radius
        }

    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (50 * factor).toInt()
        val red = 0
        val green = 114
        val blue = 255
        return Color.argb(alpha, red, green, blue)
    }


}
