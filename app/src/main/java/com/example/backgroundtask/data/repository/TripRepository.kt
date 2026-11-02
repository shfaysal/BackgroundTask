package com.example.backgroundtask.data.repository

import com.example.backgroundtask.data.model.PushData
import com.pusher.client.connection.ConnectionState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface TripRepository {
    val connectionState: StateFlow<ConnectionState> // Exposes CONNECTED, CONNECTING, DISCONNECTED, etc.
    val inComingTrips: SharedFlow<PushData>
    fun connectSocket()
    fun disconnectSocket()
    suspend fun acceptTrip(tripId: Int): Result<Unit>
    suspend fun declineTrip(tripId: Int): Result<Unit>
}