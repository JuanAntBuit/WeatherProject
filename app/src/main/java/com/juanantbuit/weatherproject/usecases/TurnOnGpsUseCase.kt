package com.juanantbuit.weatherproject.usecases

import android.content.IntentSender
import android.widget.Toast
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.tasks.Task
import com.juanantbuit.weatherproject.framework.ui.main.MainActivity


class TurnOnGpsUseCase(private val activity: MainActivity) {

    fun turnOnGPS(locationRequest: LocationRequest) {

        val builder: LocationSettingsRequest.Builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        builder.setAlwaysShow(true)

        val result: Task<LocationSettingsResponse> = LocationServices.getSettingsClient(activity.applicationContext)
                .checkLocationSettings(builder.build())

        result.addOnCompleteListener { task ->
            try {
                //IDE marks "response" as not used, but it is used to trigger the exception.
                val response: LocationSettingsResponse? = task.getResult(ApiException::class.java)
                Toast.makeText(activity.baseContext, "GPS is already turned on", Toast.LENGTH_SHORT)
                    .show()

            } catch (e: ApiException) {

                when (e.statusCode) {

                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED ->
                        try {

                            val resolvableApiException: ResolvableApiException =
                                e as ResolvableApiException
                            resolvableApiException.startResolutionForResult(activity, 2)

                        } catch (ex: IntentSender.SendIntentException) {
                            ex.printStackTrace()
                        }

                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {/* Device doesn't have GPS */
                    }

                }
            }
        }
    }
}