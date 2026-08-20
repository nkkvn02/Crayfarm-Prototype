package com.example.crayfarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportsViewModelTest {
    @Test
    fun viewModelCombinesRepositoryDataUsingSelectedRange() {
        val repository = FakeReportRepository()
        val viewModel = ReportsViewModel(repository)
        val now = System.currentTimeMillis()

        viewModel.start("farm-1", "pond-1")
        repository.emitThresholds(PondThresholds(maxTurbidity = 25.0))
        repository.emitHistory(
            listOf(
                PondHistoryRecord(
                    turbidity = 20.0,
                    turbidityAvailable = true,
                    recordedAt = now - 2L * 24L * 60L * 60L * 1000L
                ),
                PondHistoryRecord(
                    turbidity = 40.0,
                    turbidityAvailable = true,
                    recordedAt = now - 1_000L
                )
            )
        )
        repository.emitAlerts(emptyList())

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.filteredHistory.size)
        assertEquals(
            0.0,
            viewModel.uiState.value.summary.sensorMetrics.single()
                .compliancePercent,
            0.001
        )

        viewModel.selectRange(AnalyticsRange.WEEK)

        assertEquals(2, viewModel.uiState.value.filteredHistory.size)
        assertEquals(
            50.0,
            viewModel.uiState.value.summary.sensorMetrics.single()
                .compliancePercent,
            0.001
        )
    }

    @Test
    fun stoppingViewModelClosesFirebaseSubscriptionBoundary() {
        val repository = FakeReportRepository()
        val viewModel = ReportsViewModel(repository)

        viewModel.start("farm-1", "pond-1")
        viewModel.stop()

        assertTrue(repository.closed)
    }
}

private class FakeReportRepository : ReportRepository {
    private lateinit var historyCallback: (List<PondHistoryRecord>) -> Unit
    private lateinit var alertsCallback: (List<PondAlert>) -> Unit
    private lateinit var thresholdsCallback: (PondThresholds) -> Unit
    var closed: Boolean = false
        private set

    override fun observePondReport(
        farmId: String,
        pondId: String,
        onHistory: (List<PondHistoryRecord>) -> Unit,
        onAlerts: (List<PondAlert>) -> Unit,
        onThresholds: (PondThresholds) -> Unit,
        onError: (ReportLoadPart, String) -> Unit
    ): ReportSubscription {
        historyCallback = onHistory
        alertsCallback = onAlerts
        thresholdsCallback = onThresholds
        return ReportSubscription { closed = true }
    }

    fun emitHistory(records: List<PondHistoryRecord>) {
        historyCallback(records)
    }

    fun emitAlerts(alerts: List<PondAlert>) {
        alertsCallback(alerts)
    }

    fun emitThresholds(thresholds: PondThresholds) {
        thresholdsCallback(thresholds)
    }
}
