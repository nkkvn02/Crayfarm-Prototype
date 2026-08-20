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

@Composable
fun PondAnalyticsScreen(
    user: CrayFarmUser,
    pond: PondSummary
) {
    val analyticsViewModel = remember(user.farmId, pond.pondId) {
        AnalyticsViewModel(FirebaseAnalyticsRepository())
    }
    val analyticsState by analyticsViewModel.uiState.collectAsState()

    DisposableEffect(analyticsViewModel, user.farmId, pond.pondId) {
        analyticsViewModel.start(user.farmId, pond.pondId)
        onDispose { analyticsViewModel.stop() }
    }

    val selectedRange = analyticsState.selectedRange
    val selectedSensor = analyticsState.selectedSensor
    val availableRecords = analyticsState.availableRecords
    val chartValues = analyticsState.chartValues
    val isLoading = analyticsState.isLoading
    val errorMessage = analyticsState.errorMessage

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LightBlue
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            PageHeader(
                title = "${pond.pondName} Analytics"
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
                        text = "Time range",
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
                                    analyticsViewModel.selectRange(range)
                                }
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Sensor",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.height(116.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = AnalyticsSensor.entries,
                            key = { sensor -> sensor.name }
                        ) { sensor ->
                            AnalyticsChoiceButton(
                                modifier = Modifier.fillMaxWidth(),
                                label = sensor.label,
                                selected = selectedSensor == sensor,
                                onClick = {
                                    analyticsViewModel.selectSensor(sensor)
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
                                    .padding(vertical = 48.dp),
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
                                text = "Unable to load history: $errorMessage",
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFC62828),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    availableRecords.isEmpty() -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No ${selectedSensor.label.lowercase()} data",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(7.dp))

                                    Text(
                                        text = "No connected ${selectedSensor.label.lowercase()} readings are available in this period.",
                                        color = Color.Gray,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        item {
                            AnalyticsSummaryCard(
                                sensor = selectedSensor,
                                values = chartValues,
                                recordCount = availableRecords.size
                            )
                        }

                        item {
                            AnalyticsChartCard(
                                sensor = selectedSensor,
                                records = availableRecords,
                                values = chartValues,
                                selectedRange = selectedRange
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
fun AnalyticsChoiceButton(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .clickable {
                onClick()
            },
        color = if (selected) {
            Blue
        } else {
            Color.White
        },
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) {
                    Color.White
                } else {
                    Color(0xFF333333)
                },
                fontSize = 11.sp,
                fontWeight = if (selected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AnalyticsSummaryCard(
    sensor: AnalyticsSensor,
    values: List<Double>,
    recordCount: Int
) {
    val minimum = values.minOrNull() ?: 0.0
    val maximum = values.maxOrNull() ?: 0.0
    val average = values.average()

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
                text = sensor.label,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Blue
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnalyticsMetric(
                    label = "Minimum",
                    value = formatAnalyticsValue(
                        value = minimum,
                        unit = sensor.unit
                    )
                )

                AnalyticsMetric(
                    label = "Average",
                    value = formatAnalyticsValue(
                        value = average,
                        unit = sensor.unit
                    )
                )

                AnalyticsMetric(
                    label = "Maximum",
                    value = formatAnalyticsValue(
                        value = maximum,
                        unit = sensor.unit
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "$recordCount history records",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun AnalyticsMetric(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AnalyticsChartCard(
    sensor: AnalyticsSensor,
    records: List<PondHistoryRecord>,
    values: List<Double>,
    selectedRange: AnalyticsRange
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
                text = "${sensor.label} Trend",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = selectedRange.label,
                color = Color.Gray,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            SimpleLineChart(
                values = values,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatAnalyticsDate(
                        records.first().recordedAt,
                        selectedRange
                    ),
                    color = Color.Gray,
                    fontSize = 10.sp
                )

                Text(
                    text = formatAnalyticsDate(
                        records.last().recordedAt,
                        selectedRange
                    ),
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun SimpleLineChart(
    values: List<Double>,
    modifier: Modifier = Modifier
) {
    val minimum = values.minOrNull() ?: 0.0
    val maximum = values.maxOrNull() ?: 0.0
    val range = (maximum - minimum).takeIf { difference ->
        difference > 0.0
    } ?: 1.0

    Canvas(
        modifier = modifier
    ) {
        val leftPadding = 8.dp.toPx()
        val rightPadding = 8.dp.toPx()
        val topPadding = 12.dp.toPx()
        val bottomPadding = 12.dp.toPx()

        val chartWidth =
            size.width - leftPadding - rightPadding

        val chartHeight =
            size.height - topPadding - bottomPadding

        for (line in 0..4) {
            val y =
                topPadding +
                        chartHeight * line / 4f

            drawLine(
                color = Color(0xFFE1E5EC),
                start = Offset(leftPadding, y),
                end = Offset(
                    leftPadding + chartWidth,
                    y
                ),
                strokeWidth = 1.dp.toPx()
            )
        }

        if (values.size == 1) {
            val normalized =
                ((values.first() - minimum) / range)
                    .toFloat()

            val y =
                topPadding +
                        chartHeight * (1f - normalized)

            drawCircle(
                color = Blue,
                radius = 5.dp.toPx(),
                center = Offset(
                    leftPadding + chartWidth / 2f,
                    y
                )
            )

            return@Canvas
        }

        val path = Path()

        values.forEachIndexed { index, value ->
            val x =
                leftPadding +
                        chartWidth *
                        index.toFloat() /
                        (values.size - 1).toFloat()

            val normalized =
                ((value - minimum) / range)
                    .toFloat()

            val y =
                topPadding +
                        chartHeight * (1f - normalized)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = Blue,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        val lastIndex = values.lastIndex
        val lastNormalized =
            ((values[lastIndex] - minimum) / range)
                .toFloat()

        val lastY =
            topPadding +
                    chartHeight * (1f - lastNormalized)

        drawCircle(
            color = Blue,
            radius = 4.dp.toPx(),
            center = Offset(
                leftPadding + chartWidth,
                lastY
            )
        )
    }
}

internal fun formatAnalyticsValue(
    value: Double,
    unit: String
): String {
    val number = if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(
            Locale.getDefault(),
            "%.1f",
            value
        )
    }

    return if (unit.isBlank()) {
        number
    } else {
        "$number $unit"
    }
}


private fun formatAnalyticsDate(
    timestamp: Long,
    range: AnalyticsRange
): String {
    val pattern = when (range) {
        AnalyticsRange.DAY -> "h:mm a"
        AnalyticsRange.WEEK -> "EEE, MMM d"
        AnalyticsRange.MONTH -> "MMM d"
    }

    return SimpleDateFormat(
        pattern,
        Locale.getDefault()
    ).format(Date(timestamp))
}


