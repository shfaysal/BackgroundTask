package com.example.backgroundtask.ui

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.backgroundtask.MainActivity
import com.example.backgroundtask.data.viewModel.TripAlertEffect
import com.example.backgroundtask.data.viewModel.TripAlertViewModel
import com.example.backgroundtask.ui.theme.BackgroundTaskTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TripAlertActivity : ComponentActivity() {

    private val viewModel : TripAlertViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wakeAndUnlockScreen()
        observeEffects()

        setContent {
            val state by viewModel.uiState.collectAsState()

            BackgroundTaskTheme {
                Surface(modifier = Modifier.background(Color.Green)) {

                    TripAlertScreen(
                        state = state,
                        onAccept = viewModel::acceptTrip,
                        onDecline = viewModel::declineTrip
                    )
                }
            }
        }
    }

    private fun observeEffects() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    when (effect) {
                        is TripAlertEffect.NavigateToAcceptedRide -> {
                            notificationManager?.cancel(effect.tripId)
                            dismissKeyguardAndNavigateToMain(effect.tripId)
                        }
                        is TripAlertEffect.DismissAlert -> {
                            notificationManager?.cancel(viewModel.tripId)
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun wakeAndUnlockScreen() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        // Ensures the display stays lit while the user interacts with the alert
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun dismissKeyguardAndNavigateToMain(acceptedTripId: Int) {
        val launchAppAction = {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("ACCEPTED_TRIP_ID", acceptedTripId)
            }
            startActivity(intent)
            finish()
        }

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguardManager.isKeyguardLocked) {
            keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = launchAppAction()
                override fun onDismissCancelled() = launchAppAction()
            })
        } else {
            launchAppAction()
        }
    }
}