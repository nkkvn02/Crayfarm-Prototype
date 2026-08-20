package com.example.crayfarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class AnalyticsUiState(
    val selectedRange: AnalyticsRange = AnalyticsRange.DAY,
    val selectedSensor: AnalyticsSensor = AnalyticsSensor.TEMPERATURE,
    val historyRecords: List<PondHistoryRecord> = emptyList(),
    val availableRecords: List<PondHistoryRecord> = emptyList(),
    val chartValues: List<Double> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String = ""
)

internal class AnalyticsViewModel(
    private val repository: AnalyticsRepository
) {
    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()
    private var subscription: ReportSubscription? = null

    fun start(farmId: String, pondId: String) {
        subscription?.close()
        subscription = repository.observeHistory(
            farmId = farmId,
            pondId = pondId,
            onData = { records ->
                update(_uiState.value.copy(
                    historyRecords = records,
                    isLoading = false,
                    errorMessage = ""
                ))
            },
            onError = { message ->
                update(_uiState.value.copy(
                    isLoading = false,
                    errorMessage = message
                ))
            }
        )
    }

    fun selectRange(range: AnalyticsRange) {
        update(_uiState.value.copy(selectedRange = range))
    }

    fun selectSensor(sensor: AnalyticsSensor) {
        update(_uiState.value.copy(selectedSensor = sensor))
    }

    fun stop() {
        subscription?.close()
        subscription = null
    }

    private fun update(next: AnalyticsUiState) {
        val rangeStart = System.currentTimeMillis() -
                next.selectedRange.durationMillis
        val availableRecords = next.historyRecords.filter { record ->
            record.recordedAt >= rangeStart &&
                    reportValue(record, next.selectedSensor) != null
        }

        _uiState.value = next.copy(
            availableRecords = availableRecords,
            chartValues = availableRecords.mapNotNull { record ->
                reportValue(record, next.selectedSensor)
            }
        )
    }
}
