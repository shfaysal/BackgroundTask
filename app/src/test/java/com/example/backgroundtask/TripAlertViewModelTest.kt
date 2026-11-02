package com.example.backgroundtask

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.backgroundtask.data.repository.TripRepository
import com.example.backgroundtask.data.viewModel.TripAlertEffect
import com.example.backgroundtask.data.viewModel.TripAlertViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripAlertViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: TripRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state resolves arguments from SavedStateHandle`() = runTest {
        val savedStateHandle = SavedStateHandle(
            mapOf("trip_id" to 42, "pickup_location" to "Main Plaza")
        )

        val viewModel = TripAlertViewModel(repository, savedStateHandle)

        assertEquals(42, viewModel.uiState.value.tripId)
        assertEquals("Main Plaza", viewModel.uiState.value.pickupLocation)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `acceptTrip on success updates state and emits NavigateToAcceptedRide effect`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("trip_id" to 101, "pickup_location" to "Dhanmondi 27"))
        coEvery { repository.acceptTrip(101) } returns Result.success(Unit)

        val viewModel = TripAlertViewModel(repository, savedStateHandle)

        viewModel.effects.test {
            viewModel.acceptTrip()
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assert(effect is TripAlertEffect.NavigateToAcceptedRide)
            assertEquals(101, (effect as TripAlertEffect.NavigateToAcceptedRide).tripId)
            assertFalse(viewModel.uiState.value.isLoading)
        }

        coVerify(exactly = 1) { repository.acceptTrip(101) }
    }

    @Test
    fun `acceptTrip on error sets errorMessage in uiState`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("trip_id" to 202))
        coEvery { repository.acceptTrip(202) } returns Result.failure(Exception("Network timeout"))

        val viewModel = TripAlertViewModel(repository, savedStateHandle)

        viewModel.acceptTrip()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Network timeout", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `declineTrip emits DismissAlert effect`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("trip_id" to 303))
        coEvery { repository.declineTrip(303) } returns Result.success(Unit)

        val viewModel = TripAlertViewModel(repository, savedStateHandle)

        viewModel.effects.test {
            viewModel.declineTrip()
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertEquals(TripAlertEffect.DismissAlert, effect)
        }

        coVerify(exactly = 1) { repository.declineTrip(303) }
    }
}