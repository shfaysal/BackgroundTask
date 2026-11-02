package com.example.backgroundtask.data.repository

import android.util.Log
import com.example.backgroundtask.data.model.NonBiddingRequest
import com.example.backgroundtask.data.model.PushData
import com.pusher.client.Pusher
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionState
import com.pusher.client.connection.ConnectionStateChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepositoryImpl @Inject constructor(
    private val pusher: Pusher,
    private val json: Json
) : TripRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _incomingTrips = MutableSharedFlow<PushData>(replay = 0)
    override val inComingTrips: SharedFlow<PushData> = _incomingTrips.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override fun connectSocket() {
        // Register the connection listener before or during connect
        pusher.connect(object : ConnectionEventListener {
            override fun onConnectionStateChange(change: ConnectionStateChange) {
                Log.d("PusherDebug", "Connection state changed: ${change.previousState} -> ${change.currentState}")
                _connectionState.value = change.currentState
            }

            override fun onError(message: String?, code: String?, e: Exception?) {
                Log.e("PusherDebug", "Pusher connection error: $message (code: $code)", e)
            }
        }, ConnectionState.ALL)
        val channelName = ""
        val existingChannel = pusher.getChannel(channelName)
        val channel = if (existingChannel != null && existingChannel.isSubscribed) {
            existingChannel
        } else {
            pusher.subscribe(channelName)
        }
        channel.bind("new-ride-request") { event ->
            try {
                val data = json.decodeFromString<NonBiddingRequest>(event.data)
                repositoryScope.launch {
                    _incomingTrips.emit(PushData(a = data.getWaitingTime, b = data.getRideRequest._id))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun disconnectSocket() {
        pusher.disconnect()
    }

    override suspend fun acceptTrip(tripId: Int): Result<Unit> = runCatching {
        delay(600) // Simulated backend API delay
    }

    override suspend fun declineTrip(tripId: Int): Result<Unit> = runCatching {
        delay(200)
    }
}