package com.example.crayfarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsViewModelTest {
    @Test
    fun viewModelFiltersByRangeSensorAndAvailability() {
        val repository = FakeAnalyticsRepository()
        val viewModel = AnalyticsViewModel(repository)
        val now = System.currentTimeMillis()

        viewModel.start("farm-1", "pond-1")
        repository.emit(
            listOf(
                PondHistoryRecord(
                    temperature = 25.0,
                    temperatureAvailable = true,
                    ph = 7.0,
                    phAvailable = true,
                    recordedAt = now - 1_000L
                ),
                PondHistoryRecord(
                    temperature = 0.0,
                    temperatureAvailable = false,
                    ph = 8.0,
                    phAvailable = true,
                    recordedAt = now - 2_000L
                )
            )
        )

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf(25.0), viewModel.uiState.value.chartValues)

        viewModel.selectSensor(AnalyticsSensor.PH)

        assertEquals(listOf(7.0, 8.0), viewModel.uiState.value.chartValues)
    }

    @Test
    fun stoppingAnalyticsClosesRepositorySubscription() {
        val repository = FakeAnalyticsRepository()
        val viewModel = AnalyticsViewModel(repository)

        viewModel.start("farm-1", "pond-1")
        viewModel.stop()

        assertTrue(repository.closed)
    }
}

private class FakeAnalyticsRepository : AnalyticsRepository {
    private lateinit var callback: (List<PondHistoryRecord>) -> Unit
    var closed: Boolean = false
        private set

    override fun observeHistory(
        farmId: String,
        pondId: String,
        onData: (List<PondHistoryRecord>) -> Unit,
        onError: (String) -> Unit
    ): ReportSubscription {
        callback = onData
        return ReportSubscription { closed = true }
    }

    fun emit(records: List<PondHistoryRecord>) {
        callback(records)
    }
}
