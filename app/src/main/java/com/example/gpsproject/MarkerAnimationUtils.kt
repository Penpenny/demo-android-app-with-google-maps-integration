package com.example.gpsproject

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.util.Property
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import java.lang.Math.*


object MarkerAnimation {

    var locationDataCallback: ((LatLng?) -> Unit)? = null
    private var animator: Animator? = null
    private var circleAnimator: Animator? = null


    fun animateMarkerToICS(
        marker: Marker?,
        finalPosition: LatLng?,
        latLngInterpolator: LatLngInterpolator
    ) {
        val typeEvaluator: TypeEvaluator<LatLng> =
            TypeEvaluator { fraction, startValue, endValue ->
                startValue?.let {
                    val newLatLng = latLngInterpolator.interpolate(
                        fraction,
                        startValue!!,
                        endValue!!
                    )
                    locationDataCallback?.invoke(newLatLng)
                    newLatLng
                }

            }
        val property: Property<Marker, LatLng> = Property.of(
            Marker::class.java,
            LatLng::class.java,
            "position"
        )
        // ADD THIS TO STOP ANIMATION IF ALREADY ANIMATING TO AN OBSOLETE LOCATION

        animator?.let { animator ->
            if (animator.isRunning) {
                animator.cancel();
                this.animator = null;
            }
        }


        animator =
            ObjectAnimator.ofObject(marker, property, typeEvaluator, finalPosition)
        animator?.duration = 1000
        animator?.start()
    }


    fun animateCircleToICS(
        circle: Circle?,
        finalPosition: LatLng?,
        latLngInterpolator: LatLngInterpolator
    ) {
        val typeEvaluator: TypeEvaluator<LatLng> =
            TypeEvaluator { fraction, startValue, endValue ->
                startValue?.let {
                    val newLatLng = latLngInterpolator.interpolate(
                        fraction,
                        startValue!!,
                        endValue!!
                    )
                    newLatLng
                }

            }
        val property: Property<Circle, LatLng> = Property.of(
            Circle::class.java,
            LatLng::class.java,
            "center"
        )
        // ADD THIS TO STOP ANIMATION IF ALREADY ANIMATING TO AN OBSOLETE LOCATION

        circleAnimator?.let { animator ->
            if (animator.isRunning) {
                animator.cancel();
                this.circleAnimator = null;
            }
        }


        circleAnimator =
            ObjectAnimator.ofObject(circle, property, typeEvaluator, finalPosition)
        animator?.duration = 1000
        circleAnimator?.start()
    }
}


interface LatLngInterpolator {
    fun interpolate(fraction: Float, a: LatLng, b: LatLng): LatLng?
    class Linear : LatLngInterpolator {


        override fun interpolate(fraction: Float, a: LatLng, b: LatLng): LatLng {
            val lat = (b.latitude - a.latitude) * fraction + a.latitude
            val lng = (b.longitude - a.longitude) * fraction + a.longitude
            return LatLng(lat, lng)
        }
    }

    class LinearFixed : LatLngInterpolator {
        override fun interpolate(fraction: Float, a: LatLng, b: LatLng): LatLng {
            val lat = (b.latitude - a.latitude) * fraction + a.latitude
            var lngDelta = b.longitude - a.longitude

            // Take the shortest path across the 180th meridian.
            if (Math.abs(lngDelta) > 180) {
                lngDelta -= Math.signum(lngDelta) * 360
            }
            val lng = lngDelta * fraction + a.longitude
            return LatLng(lat, lng)
        }
    }

    class Spherical : LatLngInterpolator {
        /* From github.com/googlemaps/android-maps-utils */
        override fun interpolate(fraction: Float, from: LatLng, to: LatLng): LatLng {
            // http://en.wikipedia.org/wiki/Slerp
            val fromLat: Double = toRadians(from.latitude)
            val fromLng: Double = toRadians(from.longitude)
            val toLat: Double = toRadians(to.latitude)
            val toLng: Double = toRadians(to.longitude)
            val cosFromLat: Double = cos(fromLat)
            val cosToLat: Double = cos(toLat)

            // Computes Spherical interpolation coefficients.
            val angle = computeAngleBetween(fromLat, fromLng, toLat, toLng)
            val sinAngle: Double = sin(angle)
            if (sinAngle < 1E-6) {
                return from
            }
            val a: Double = sin((1 - fraction) * angle) / sinAngle
            val b: Double = sin(fraction * angle) / sinAngle

            // Converts from polar to vector and interpolate.
            val x: Double = a * cosFromLat * cos(fromLng) + b * cosToLat * cos(toLng)
            val y: Double = a * cosFromLat * sin(fromLng) + b * cosToLat * sin(toLng)
            val z: Double = a * sin(fromLat) + b * sin(toLat)

            // Converts interpolated vector back to polar.
            val lat: Double = atan2(z, sqrt(x * x + y * y))
            val lng: Double = atan2(y, x)
            return LatLng(toDegrees(lat), toDegrees(lng))
        }

        private fun computeAngleBetween(
            fromLat: Double,
            fromLng: Double,
            toLat: Double,
            toLng: Double
        ): Double {
            // Haversine's formula
            val dLat = fromLat - toLat
            val dLng = fromLng - toLng
            return 2 * asin(
                sqrt(
                    pow(sin(dLat / 2), 2.0) +
                            cos(fromLat) * cos(toLat) * pow(sin(dLng / 2), 2.0)
                )
            )
        }
    }
}
