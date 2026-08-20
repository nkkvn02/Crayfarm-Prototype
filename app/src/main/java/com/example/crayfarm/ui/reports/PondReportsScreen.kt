package com.example.crayfarm

import android.Manifest
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Water
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Blue = Color(0xFF2639A6)
private val LightBlue = Color(0xFFC5CDD9)

private fun formatReportNumber(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
}

private fun formatReportPercent(value: Double?): String {
    return value?.let { percent ->
        "${formatReportNumber(percent)}%"
    } ?: "N/A"
}

private fun formatHistoryReading(
    record: PondHistoryRecord,
    sensor: AnalyticsSensor
): String {
    val value = reportValue(record, sensor) ?: return "--"
    return formatAnalyticsValue(value, sensor.unit)
}

@Composable
fun PondReportsScreen(
    user: CrayFarmUser,
    pond: PondSummary
) {
    val context = LocalContext.current
    val reportsViewModel = remember(user.farmId, pond.pondId) {
        ReportsViewModel(FirebaseReportRepository())
    }
    val reportState by reportsViewModel.uiState.collectAsState()

    DisposableEffect(reportsViewModel, user.farmId, pond.pondId) {
        reportsViewModel.start(user.farmId, pond.pondId)
        onDispose { reportsViewModel.stop() }
    }

    var pendingCsvContent by remember {
        mutableStateOf("")
    }

    var pendingPdfBytes by remember {
        mutableStateOf<ByteArray?>(null)
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "text/csv"
        )
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        try {
            context.contentResolver
                .openOutputStream(uri)
                ?.bufferedWriter()
                ?.use { writer ->
                    writer.write(pendingCsvContent)
                }

            Toast.makeText(
                context,
                "CSV report saved successfully.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (exception: Exception) {
            Toast.makeText(
                context,
                exception.message
                    ?: "Unable to save the CSV report.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/pdf"
        )
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        val pdfBytes = pendingPdfBytes
        if (pdfBytes == null) {
            Toast.makeText(
                context,
                "Unable to prepare the PDF report.",
                Toast.LENGTH_SHORT
            ).show()
            return@rememberLauncherForActivityResult
        }

        try {
            context.contentResolver
                .openOutputStream(uri)
                ?.use { output ->
                    output.write(pdfBytes)
                    output.flush()
                }

            Toast.makeText(
                context,
                "PDF report saved successfully.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (exception: Exception) {
            Toast.makeText(
                context,
                exception.message
                    ?: "Unable to save the PDF report.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val selectedRange = reportState.selectedRange
    val filteredHistory = reportState.filteredHistory
    val filteredAlerts = reportState.filteredAlerts
    val reportSummary = reportState.summary
    val isLoading = reportState.isLoading
    val errorMessage = reportState.errorMessage

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LightBlue
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            PageHeader(
                title = "${pond.pondName} Reports"
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    Text(
                        text = "Report period",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnalyticsRange.entries.forEach { range ->
                            AnalyticsChoiceButton(
                                modifier = Modifier.weight(1f),
                                label = range.label,
                                selected = selectedRange == range,
                                onClick = {
                                    reportsViewModel.selectRange(range)
                                }
                            )
                        }
                    }
                }

                when {
                    isLoading -> {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 50.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = Blue
                                )
                            }
                        }
                    }

                    errorMessage.isNotBlank() -> {
                        item {
                            Text(
                                text = "Unable to load report data: $errorMessage",
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFC62828),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    else -> {
                        item {
                            ReportOverviewCard(
                                selectedRange = selectedRange,
                                summary = reportSummary
                            )
                        }

                        item {
                            Text(
                                text = "Sensor summary",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (reportSummary.sensorMetrics.isEmpty()) {
                            item {
                                ReportEmptyCard(
                                    message =
                                        "No history records are available for this period."
                                )
                            }
                        } else {
                            item {
                                ReportSensorSummaryCard(
                                    metrics = reportSummary.sensorMetrics
                                )
                            }
                        }

                        item {
                            Text(
                                text = "Alert summary",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (filteredAlerts.isEmpty()) {
                            item {
                                ReportEmptyCard(
                                    message =
                                        "No alerts were recorded during this period."
                                )
                            }
                        } else {
                            item {
                                ReportAlertSummaryCard(
                                    alerts = filteredAlerts,
                                    summary = reportSummary
                                )
                            }
                        }

                        item {
                            val dateText = SimpleDateFormat(
                                "yyyy-MM-dd",
                                Locale.getDefault()
                            ).format(Date())

                            val safePondName =
                                pond.pondName
                                    .replace(
                                        Regex("[^A-Za-z0-9_-]"),
                                        "_"
                                    )
                                    .ifBlank {
                                        "pond"
                                    }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (
                                            filteredHistory.isEmpty() &&
                                            filteredAlerts.isEmpty()
                                        ) {
                                            Toast.makeText(
                                                context,
                                                "There is no report data to export.",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            return@Button
                                        }

                                        pendingCsvContent =
                                            buildPondReportCsv(
                                                pond = pond,
                                                selectedRange = selectedRange,
                                                historyRecords = filteredHistory,
                                                alerts = filteredAlerts,
                                                summary = reportSummary
                                            )

                                        csvExportLauncher.launch(
                                            "CrayFarm_${safePondName}_${selectedRange.name}_$dateText.csv"
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Blue,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "Export CSV",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (
                                            filteredHistory.isEmpty() &&
                                            filteredAlerts.isEmpty()
                                        ) {
                                            Toast.makeText(
                                                context,
                                                "There is no report data to export.",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            return@Button
                                        }

                                        try {
                                            pendingPdfBytes =
                                                buildPondReportPdfBytes(
                                                    pond = pond,
                                                    selectedRange = selectedRange,
                                                    historyRecords = filteredHistory,
                                                    alerts = filteredAlerts,
                                                    summary = reportSummary
                                                )

                                            pdfExportLauncher.launch(
                                                "CrayFarm_${safePondName}_${selectedRange.name}_$dateText.pdf"
                                            )
                                        } catch (exception: Exception) {
                                            Toast.makeText(
                                                context,
                                                exception.message
                                                    ?: "Unable to prepare the PDF report.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Blue,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "Export PDF",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                text = "CSV and PDF exports contain the report summary, sensor history, and alerts within the selected period.",
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun ReportOverviewCard(
    selectedRange: AnalyticsRange,
    summary: PondReportSummary
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = selectedRange.label,
                color = Blue,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnalyticsMetric(
                    label = "Records",
                    value = summary.historyRecordCount.toString()
                )

                AnalyticsMetric(
                    label = "Availability",
                    value = formatReportPercent(
                        summary.overallAvailabilityPercent
                    )
                )

                AnalyticsMetric(
                    label = "Alerts",
                    value = summary.alertCount.toString()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnalyticsMetric(
                    label = "Warnings",
                    value = summary.warningAlertCount.toString()
                )

                AnalyticsMetric(
                    label = "Critical",
                    value = summary.criticalAlertCount.toString()
                )

                AnalyticsMetric(
                    label = "Resolved",
                    value = summary.resolvedAlertCount.toString()
                )

                AnalyticsMetric(
                    label = "Acknowledged",
                    value = summary.acknowledgedAlertCount.toString()
                )
            }
        }
    }
}

@Composable
fun ReportSensorSummaryCard(
    metrics: List<SensorReportMetrics>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            metrics.forEach { metric ->
                ReportSensorSummaryRow(metric = metric)
            }
        }
    }
}

@Composable
fun ReportSensorSummaryRow(
    metric: SensorReportMetrics
) {
    Column {
        Text(
            text = metric.sensor.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text =
                "Min ${formatAnalyticsValue(metric.minimum, metric.sensor.unit)}  •  " +
                        "Avg ${formatAnalyticsValue(metric.average, metric.sensor.unit)}  •  " +
                        "Max ${formatAnalyticsValue(metric.maximum, metric.sensor.unit)}",
            color = Color.Gray,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text =
                "Compliance ${formatReportPercent(metric.compliancePercent)}  •  " +
                        "Availability ${formatReportPercent(metric.availabilityPercent)}  •  " +
                        "${metric.sampleCount}/${metric.totalRecordCount} samples",
            color = Blue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ReportAlertSummaryCard(
    alerts: List<PondAlert>,
    summary: PondReportSummary
) {
    val alertGroups = alerts
        .groupingBy { alert ->
            alert.title.ifBlank {
                alert.type.replace("_", " ")
            }
        }
        .eachCount()
        .toList()
        .sortedByDescending { pair ->
            pair.second
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            summary.mostFrequentIssue?.let { issue ->
                Text(
                    text = "Most frequent: $issue (${summary.mostFrequentIssueCount})",
                    color = Blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider(color = Color(0xFFE4E9F0))
            }

            alertGroups.forEach { (title, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp
                    )

                    Text(
                        text = count.toString(),
                        color = Color(0xFFC62828),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ReportEmptyCard(
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun buildPondReportPdfBytes(
    pond: PondSummary,
    selectedRange: AnalyticsRange,
    historyRecords: List<PondHistoryRecord>,
    alerts: List<PondAlert>,
    summary: PondReportSummary
): ByteArray {
    val document = PdfDocument()

    val pageWidth = 595
    val pageHeight = 842
    val leftMargin = 42f
    val rightMargin = 42f
    val topMargin = 48f
    val bottomMargin = 48f
    val contentWidth = pageWidth - leftMargin - rightMargin

    val bodyPaint = Paint().apply {
        color = android.graphics.Color.rgb(35, 35, 35)
        textSize = 9.5f
        isAntiAlias = true
        typeface = Typeface.create(
            Typeface.DEFAULT,
            Typeface.NORMAL
        )
    }

    val headingPaint = Paint().apply {
        color = android.graphics.Color.rgb(40, 57, 168)
        textSize = 15f
        isAntiAlias = true
        typeface = Typeface.create(
            Typeface.DEFAULT,
            Typeface.BOLD
        )
    }

    val sectionPaint = Paint().apply {
        color = android.graphics.Color.rgb(25, 25, 25)
        textSize = 11.5f
        isAntiAlias = true
        typeface = Typeface.create(
            Typeface.DEFAULT,
            Typeface.BOLD
        )
    }

    var pageNumber = 0
    var page: PdfDocument.Page? = null
    var canvas: android.graphics.Canvas? = null
    var y = topMargin

    fun startPage() {
        page?.let { currentPage ->
            document.finishPage(currentPage)
        }

        pageNumber += 1

        val pageInfo = PdfDocument.PageInfo.Builder(
            pageWidth,
            pageHeight,
            pageNumber
        ).create()

        page = document.startPage(pageInfo)
        canvas = page!!.canvas
        y = topMargin
    }

    fun ensureSpace(requiredHeight: Float) {
        if (
            page == null ||
            y + requiredHeight >
            pageHeight - bottomMargin
        ) {
            startPage()
        }
    }

    fun drawSingleLine(
        value: String,
        paint: Paint = bodyPaint,
        extraAfter: Float = 0f
    ) {
        ensureSpace(paint.textSize + 8f)

        canvas?.drawText(
            value,
            leftMargin,
            y,
            paint
        )

        y += paint.textSize + 5f + extraAfter
    }

    fun wrapText(
        value: String,
        paint: Paint,
        maxWidth: Float = contentWidth
    ): List<String> {
        if (value.isBlank()) {
            return listOf("")
        }

        val words = value
            .replace("\n", " ")
            .split(Regex("\\s+"))
            .filter { word -> word.isNotBlank() }

        if (words.isEmpty()) {
            return listOf("")
        }

        val lines = mutableListOf<String>()
        var current = ""

        words.forEach { word ->
            val candidate =
                if (current.isBlank()) {
                    word
                } else {
                    "$current $word"
                }

            if (
                paint.measureText(candidate) <= maxWidth
            ) {
                current = candidate
            } else {
                if (current.isNotBlank()) {
                    lines.add(current)
                }

                // A very long token is kept on one line rather than
                // causing an endless wrap loop.
                current = word
            }
        }

        if (current.isNotBlank()) {
            lines.add(current)
        }

        return lines
    }

    fun drawWrapped(
        value: String,
        paint: Paint = bodyPaint,
        indent: Float = 0f,
        extraAfter: Float = 0f
    ) {
        val maxWidth = contentWidth - indent
        val lines = wrapText(
            value = value,
            paint = paint,
            maxWidth = maxWidth
        )

        lines.forEach { line ->
            ensureSpace(paint.textSize + 7f)

            canvas?.drawText(
                line,
                leftMargin + indent,
                y,
                paint
            )

            y += paint.textSize + 4f
        }

        y += extraAfter
    }

    fun drawSection(title: String) {
        y += 5f
        drawSingleLine(
            value = title,
            paint = sectionPaint,
            extraAfter = 2f
        )
    }

    startPage()

    drawSingleLine(
        value = "CRAYFARM POND REPORT",
        paint = headingPaint,
        extraAfter = 5f
    )

    val generatedAt = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.getDefault()
    ).format(Date())

    drawWrapped("Pond: ${pond.pondName}")
    drawWrapped("Report period: ${selectedRange.label}")
    drawWrapped("Generated at: $generatedAt")
    drawWrapped("History records: ${summary.historyRecordCount}")
    drawWrapped("Data availability: ${formatReportPercent(summary.overallAvailabilityPercent)}")
    drawWrapped("Alerts: ${summary.alertCount}")
    drawWrapped("Warnings: ${summary.warningAlertCount}")
    drawWrapped("Critical: ${summary.criticalAlertCount}")
    drawWrapped("Resolved: ${summary.resolvedAlertCount}")
    drawWrapped("Acknowledged: ${summary.acknowledgedAlertCount}")
    drawWrapped(
        "Most frequent issue: ${summary.mostFrequentIssue ?: "None"}" +
                if (summary.mostFrequentIssueCount > 0) {
                    " (${summary.mostFrequentIssueCount})"
                } else {
                    ""
                },
        extraAfter = 4f
    )

    drawSection("SENSOR SUMMARY")

    if (summary.sensorMetrics.isEmpty()) {
        drawWrapped("No connected sensor data is available for this period.")
    } else {
        summary.sensorMetrics.forEach { metric ->
            drawWrapped(
                "${metric.sensor.label} — " +
                        "Min ${formatAnalyticsValue(metric.minimum, metric.sensor.unit)}, " +
                        "Avg ${formatAnalyticsValue(metric.average, metric.sensor.unit)}, " +
                        "Max ${formatAnalyticsValue(metric.maximum, metric.sensor.unit)} | " +
                        "Compliance ${formatReportPercent(metric.compliancePercent)} | " +
                        "Availability ${formatReportPercent(metric.availabilityPercent)} " +
                        "(${metric.sampleCount}/${metric.totalRecordCount})"
            )
        }
    }

    drawSection("ALERT SUMMARY")

    if (alerts.isEmpty()) {
        drawWrapped("No alerts were recorded during this period.")
    } else {
        val alertGroups = alerts
            .groupingBy { alert ->
                alert.title.ifBlank {
                    alert.type.replace("_", " ")
                }
            }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }

        alertGroups.forEach { (title, count) ->
            drawWrapped("• $title: $count")
        }
    }

    val dateFormatter = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.getDefault()
    )

    drawSection("SENSOR HISTORY")

    if (historyRecords.isEmpty()) {
        drawWrapped("No sensor history is available for this period.")
    } else {
        historyRecords
            .sortedBy { it.recordedAt }
            .forEach { record ->
                val line =
                    "${dateFormatter.format(Date(record.recordedAt))} | " +
                            "Temp ${formatHistoryReading(record, AnalyticsSensor.TEMPERATURE)} | " +
                            "pH ${formatHistoryReading(record, AnalyticsSensor.PH)} | " +
                            "Turb ${formatHistoryReading(record, AnalyticsSensor.TURBIDITY)} | " +
                            "TDS ${formatHistoryReading(record, AnalyticsSensor.TDS)} | " +
                            "Water ${formatHistoryReading(record, AnalyticsSensor.WATER_LEVEL)}"

                drawWrapped(line)
            }
    }

    drawSection("ALERT DETAILS")

    if (alerts.isEmpty()) {
        drawWrapped("No alert details are available for this period.")
    } else {
        alerts
            .sortedBy { it.createdAt }
            .forEach { alert ->
                val status = if (alert.resolved) "Resolved" else "Active"
                val acknowledgement = if (alert.acknowledged) {
                    "Acknowledged"
                } else {
                    "Not acknowledged"
                }

                drawWrapped(
                    "${dateFormatter.format(Date(alert.createdAt))} — ${alert.title}",
                    paint = sectionPaint
                )

                drawWrapped(
                    "Value: ${formatAlertValue(alert.value)} ${alert.unit} | " +
                            "Threshold: ${formatAlertValue(alert.threshold)} ${alert.unit} | " +
                            "Severity: ${alert.severity} | Status: $status | $acknowledgement",
                    indent = 8f
                )

                if (alert.resolvedAt > 0L) {
                    drawWrapped(
                        "Resolved at: ${dateFormatter.format(Date(alert.resolvedAt))}",
                        indent = 8f
                    )
                }

                if (alert.message.isNotBlank()) {
                    drawWrapped(
                        "Message: ${alert.message}",
                        indent = 8f
                    )
                }

                if (alert.suggestedAction.isNotBlank()) {
                    drawWrapped(
                        "Suggested action: ${alert.suggestedAction}",
                        indent = 8f,
                        extraAfter = 4f
                    )
                }
            }
    }

    page?.let { currentPage ->
        document.finishPage(currentPage)
    }

    val output = ByteArrayOutputStream()
    document.writeTo(output)
    document.close()

    return output.toByteArray()
}

private fun buildPondReportCsv(
    pond: PondSummary,
    selectedRange: AnalyticsRange,
    historyRecords: List<PondHistoryRecord>,
    alerts: List<PondAlert>,
    summary: PondReportSummary
): String {
    val builder = StringBuilder()
    val generatedAt = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.getDefault()
    ).format(Date())

    builder.appendLine("CRAYFARM POND REPORT")
    builder.appendLine(
        "Pond,${csvEscape(pond.pondName)}"
    )
    builder.appendLine(
        "Report Period,${csvEscape(selectedRange.label)}"
    )
    builder.appendLine(
        "Generated At,${csvEscape(generatedAt)}"
    )
    builder.appendLine(
        "History Records,${summary.historyRecordCount}"
    )
    builder.appendLine(
        "Data Availability,${csvEscape(formatReportPercent(summary.overallAvailabilityPercent))}"
    )
    builder.appendLine(
        "Alerts,${summary.alertCount}"
    )
    builder.appendLine("Warning Alerts,${summary.warningAlertCount}")
    builder.appendLine("Critical Alerts,${summary.criticalAlertCount}")
    builder.appendLine("Resolved Alerts,${summary.resolvedAlertCount}")
    builder.appendLine("Acknowledged Alerts,${summary.acknowledgedAlertCount}")
    builder.appendLine(
        "Most Frequent Issue,${csvEscape(summary.mostFrequentIssue.orEmpty())},${summary.mostFrequentIssueCount}"
    )
    builder.appendLine()

    builder.appendLine("SENSOR SUMMARY")
    builder.appendLine(
        "Sensor,Unit,Samples,Total Records,Availability %,Compliance %,Minimum,Average,Maximum"
    )

    summary.sensorMetrics.forEach { metric ->
        builder.appendLine(
            listOf(
                metric.sensor.label,
                metric.sensor.unit,
                metric.sampleCount.toString(),
                metric.totalRecordCount.toString(),
                formatReportNumber(metric.availabilityPercent),
                formatReportNumber(metric.compliancePercent),
                metric.minimum.toString(),
                metric.average.toString(),
                metric.maximum.toString()
            ).joinToString(",") { value -> csvEscape(value) }
        )
    }

    builder.appendLine()
    builder.appendLine("SENSOR HISTORY")
    builder.appendLine(
        "Recorded At,Temperature C,pH,Relative Turbidity %,TDS ppm,Water Level cm"
    )

    val dateFormatter = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.getDefault()
    )

    historyRecords.forEach { record ->
        builder.appendLine(
            listOf(
                dateFormatter.format(
                    Date(record.recordedAt)
                ),
                reportValue(record, AnalyticsSensor.TEMPERATURE)?.toString().orEmpty(),
                reportValue(record, AnalyticsSensor.PH)?.toString().orEmpty(),
                reportValue(record, AnalyticsSensor.TURBIDITY)?.toString().orEmpty(),
                reportValue(record, AnalyticsSensor.TDS)?.toString().orEmpty(),
                reportValue(record, AnalyticsSensor.WATER_LEVEL)?.toString().orEmpty()
            ).joinToString(",") { value ->
                csvEscape(value)
            }
        )
    }

    builder.appendLine()
    builder.appendLine("ALERTS")
    builder.appendLine(
        "Created At,Type,Sensor,Title,Severity,Status,Resolved At,Value,Threshold,Unit,Acknowledged,Acknowledged At,Message,Suggested Action"
    )

    alerts.sortedBy { alert ->
        alert.createdAt
    }.forEach { alert ->
        val acknowledgedAtText =
            if (alert.acknowledgedAt > 0L) {
                dateFormatter.format(
                    Date(alert.acknowledgedAt)
                )
            } else {
                ""
            }
        val resolvedAtText = if (alert.resolvedAt > 0L) {
            dateFormatter.format(Date(alert.resolvedAt))
        } else {
            ""
        }

        builder.appendLine(
            listOf(
                dateFormatter.format(
                    Date(alert.createdAt)
                ),
                alert.type,
                alert.sensor,
                alert.title,
                alert.severity,
                if (alert.resolved) "Resolved" else "Active",
                resolvedAtText,
                alert.value.toString(),
                alert.threshold.toString(),
                alert.unit,
                alert.acknowledged.toString(),
                acknowledgedAtText,
                alert.message,
                alert.suggestedAction
            ).joinToString(",") { value ->
                csvEscape(value)
            }
        )
    }

    return builder.toString()
}

private fun csvEscape(value: String): String {
    val escaped = value.replace(
        "\"",
        "\"\""
    )

    return "\"$escaped\""
}
