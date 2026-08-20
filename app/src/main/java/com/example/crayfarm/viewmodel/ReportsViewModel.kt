package com.example.crayfarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ReportsUiState(
    val selectedRange: AnalyticsRange = AnalyticsRange.DAY,
    val historyRecords: List<PondHistoryRecord> = emptyList(),
    val alerts: List<PondAlert> = emptyList(),
    val thresholds: PondThresholds = PondThresholds(),
    val historyLoaded: Boolean = false,
    val alertsLoaded: Boolean = false,
    val thresholdsLoaded: Boolean = false,
    val errorMessage: String = "",
    val filteredHistory: List<PondHistoryRecord> = emptyList(),
    val filteredAlerts: List<PondAlert> = emptyList(),
    val summary: PondReportSummary = calculatePondReportSummary(
        historyRecords = emptyList(),
        alerts = emptyList(),
        thresholds = PondThresholds()
    )
) {
    val isLoading: Boolean
        get() = !historyLoaded || !alertsLoaded || !thresholdsLoaded
}

internal class ReportsViewModel(
    private val repository: ReportRepository
) {
    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()
    private var subscription: ReportSubscription? = null

    fun start(farmId: String, pondId: String) {
        subscription?.close()
        subscription = repository.observePondReport(
            farmId = farmId,
            pondId = pondId,
            onHistory = { records ->
                update(_uiState.value.copy(
                    historyRecords = records,
                    historyLoaded = true,
                    errorMessage = ""
                ))
            },
            onAlerts = { alerts ->
                update(_uiState.value.copy(
                    alerts = alerts,
                    alertsLoaded = true,
                    errorMessage = ""
                ))
            },
            onThresholds = { thresholds ->
                update(_uiState.value.copy(
                    thresholds = thresholds,
                    thresholdsLoaded = true,
                    errorMessage = ""
                ))
            },
            onError = { part, message ->
                val current = _uiState.value
                update(current.copy(
                    historyLoaded = current.historyLoaded ||
                            part == ReportLoadPart.HISTORY,
                    alertsLoaded = current.alertsLoaded ||
                            part == ReportLoadPart.ALERTS,
                    thresholdsLoaded = current.thresholdsLoaded ||
                            part == ReportLoadPart.THRESHOLDS,
                    errorMessage = message
                ))
            }
        )
    }

    fun selectRange(range: AnalyticsRange) {
        update(_uiState.value.copy(selectedRange = range))
    }

    fun stop() {
        subscription?.close()
        subscription = null
    }

    private fun update(next: ReportsUiState) {
        val rangeStart = System.currentTimeMillis() -
                next.selectedRange.durationMillis
        val filteredHistory = next.historyRecords.filter { record ->
            record.recordedAt >= rangeStart
        }
        val filteredAlerts = next.alerts.filter { alert ->
            alert.createdAt >= rangeStart ||
                    (alert.resolved && alert.resolvedAt >= rangeStart)
        }

        _uiState.value = next.copy(
            filteredHistory = filteredHistory,
            filteredAlerts = filteredAlerts,
            summary = calculatePondReportSummary(
                historyRecords = filteredHistory,
                alerts = filteredAlerts,
                thresholds = next.thresholds
            )
        )
    }
}
