package com.example.backgroundtask.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PushData(
    val a: Int? = null,
    val b: String? = null
)

@Serializable
data class NonBiddingRequest(
    val rideRequest: RideRequest? = null,
    val waitingTime : Int? = null,
) {

    val getRideRequest : RideRequest
        get() = rideRequest ?: RideRequest()

    val getWaitingTime : Int
        get() = waitingTime ?: 0
}

@Serializable
data class RideRequest(
    val _id: String? = null,
    val carType: String? = null,
    val isRoundTrip: Boolean? = null,
    val additionalNotes: String? = null,
)