// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/screens/progress/ProgressScreen.kt
package com.typosbro.multilevel.ui.screens.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.R
import com.typosbro.multilevel.ui.viewmodels.DurationFilter
import com.typosbro.multilevel.ui.viewmodels.ExamStatistics
import com.typosbro.multilevel.ui.viewmodels.ExamType
import com.typosbro.multilevel.ui.viewmodels.GenericExamResultSummary
import com.typosbro.multilevel.ui.viewmodels.ProgressViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val multilevelPartOrder = listOf("FULL", "P1_1", "P1_2", "P2", "P3")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onNavigateToIeltsResult: (resultId: String) -> Unit,
    onNavigateToMultilevelResult: (resultId: String) -> Unit,
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentHistory = viewModel.getCurrentHistory()
    val availableMultilevelParts = viewModel.getAvailableMultilevelParts()
    val statistics = viewModel.getStatistics()

    val multilevelMaxScores =
        mapOf("FULL" to 72.0, "P1_1" to 12.0, "P1_2" to 12.0, "P2" to 24.0, "P3" to 24.0)
    val yMaxForChart = when (uiState.selectedTab) {
        ExamType.IELTS -> 9.0
        ExamType.MULTILEVEL -> multilevelMaxScores[uiState.selectedMultilevelPart] ?: 72.0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.progress_title)) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Exam Type Switcher
            ExamTypeSwitcher(
                selectedType = uiState.selectedTab,
                onTypeSelected = { viewModel.selectTab(it) },
                modifier = Modifier.fillMaxWidth()
            )

            // Duration Filter
            DurationFilterRow(
                selectedDuration = uiState.selectedDuration,
                onDurationSelected = { viewModel.selectDuration(it) },
                modifier = Modifier.fillMaxWidth()
            )

            // Multilevel Part Filter (only show for multilevel tab)
            if (uiState.selectedTab == ExamType.MULTILEVEL && availableMultilevelParts.isNotEmpty()) {
                MultilevelPartSwitcher(
                    availableParts = availableMultilevelParts,
                    selectedPart = uiState.selectedMultilevelPart,
                    onPartSelected = { viewModel.selectMultilevelPart(it) }
                )
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (currentHistory.isEmpty()) {
                EmptyStateCard(
                    selectedTab = uiState.selectedTab,
                    selectedDuration = uiState.selectedDuration,
                    selectedPart = uiState.selectedMultilevelPart
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // Statistics Card
                    item {
                        StatisticsCard(
                            statistics = statistics,
                            examType = uiState.selectedTab,
                            selectedPart = uiState.selectedMultilevelPart,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // Score Chart
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.progress_score),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${currentHistory.size} ${if (currentHistory.size == 1) "exam" else "exams"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                ScoreHistoryChart(
                                    scores = currentHistory.map { it.score },
                                    yMax = yMaxForChart,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                )
                            }
                        }
                    }

                    // History Header
                    item {
                        Text(
                            text = stringResource(id = R.string.progress_history),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 8.dp
                            )
                        )
                    }

                    // History Items
                    items(currentHistory) { result ->
                        ExamHistoryItem(
                            result = result,
                            onClick = {
                                when (result.type) {
                                    ExamType.IELTS -> onNavigateToIeltsResult(result.id)
                                    ExamType.MULTILEVEL -> onNavigateToMultilevelResult(result.id)
                                }
                            }
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StatisticsCard(
    statistics: ExamStatistics,
    examType: ExamType,
    selectedPart: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (examType == ExamType.MULTILEVEL && selectedPart != "FULL") {
                Text(
                    text = "Part ${selectedPart.replace("P", "").replace("_", ".")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Avg Score",
                    value = if (examType == ExamType.IELTS) {
                        String.format("%.1f", statistics.averageScore)
                    } else {
                        statistics.averageScore.roundToInt().toString()
                    }
                )

                StatItem(
                    label = "Best Score",
                    value = if (examType == ExamType.IELTS) {
                        String.format("%.1f", statistics.bestScore)
                    } else {
                        statistics.bestScore.roundToInt().toString()
                    }
                )

                StatItem(
                    label = "Latest",
                    value = if (examType == ExamType.IELTS) {
                        String.format("%.1f", statistics.latestScore)
                    } else {
                        statistics.latestScore.roundToInt().toString()
                    }
                )

                StatItem(
                    label = "Change",
                    value = if (statistics.improvement > 0) {
                        "+${
                            if (examType == ExamType.IELTS) String.format(
                                "%.1f",
                                statistics.improvement
                            ) else statistics.improvement.roundToInt()
                        }"
                    } else if (statistics.improvement < 0) {
                        if (examType == ExamType.IELTS) String.format(
                            "%.1f",
                            statistics.improvement
                        ) else statistics.improvement.roundToInt().toString()
                    } else {
                        "0"
                    },
                    valueColor = when {
                        statistics.improvement > 0 -> MaterialTheme.colorScheme.primary
                        statistics.improvement < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DurationFilterRow(
    selectedDuration: DurationFilter,
    onDurationSelected: (DurationFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(DurationFilter.values()) { duration ->
            FilterChip(
                selected = duration == selectedDuration,
                onClick = { onDurationSelected(duration) },
                label = { Text(duration.displayName) },
                leadingIcon = if (duration == selectedDuration) {
                    { Icon(Icons.Default.FilterList, contentDescription = null) }
                } else null
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    selectedTab: ExamType,
    selectedDuration: DurationFilter,
    selectedPart: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No Results Found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            val message = when {
                selectedTab == ExamType.MULTILEVEL && selectedPart != "FULL" -> {
                    "No results for Part ${
                        selectedPart.replace("P", "").replace("_", ".")
                    } in the ${selectedDuration.displayName.lowercase()}"
                }

                selectedDuration != DurationFilter.ALL -> {
                    "No ${selectedTab.name.lowercase()} results in the ${selectedDuration.displayName.lowercase()}"
                }

                else -> {
                    "No ${selectedTab.name.lowercase()} results available"
                }
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultilevelPartSwitcher(
    availableParts: Set<String>,
    selectedPart: String,
    onPartSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val partsToShow = multilevelPartOrder.filter { it in availableParts }

    if (partsToShow.size > 1) {
        SingleChoiceSegmentedButtonRow(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            partsToShow.forEachIndexed { index, part ->
                SegmentedButton(
                    selected = part == selectedPart,
                    onClick = { onPartSelected(part) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = partsToShow.size
                    ),
                ) {
                    val label = when (part) {
                        "FULL" -> "Full Mock"
                        "P1_1" -> "Part 1.1"
                        "P1_2" -> "Part 1.2"
                        "P2" -> "Part 2"
                        "P3" -> "Part 3"
                        else -> part
                    }
                    Text(label)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamTypeSwitcher(
    selectedType: ExamType,
    onTypeSelected: (ExamType) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SegmentedButton(
            selected = selectedType == ExamType.MULTILEVEL,
            onClick = { onTypeSelected(ExamType.MULTILEVEL) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text("Multilevel")
        }
        SegmentedButton(
            selected = selectedType == ExamType.IELTS,
            onClick = { onTypeSelected(ExamType.IELTS) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text("IELTS")
        }
    }
}

@Composable
fun ExamHistoryItem(result: GenericExamResultSummary, onClick: () -> Unit) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    ListItem(
        headlineContent = {
            Text(
                result.scoreLabel,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(dateFormatter.format(Date(result.examDate)))
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(id = R.string.progress_detail)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun ScoreHistoryChart(scores: List<Double>, yMax: Double, modifier: Modifier = Modifier) {
    if (scores.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = if (scores.size == 1) "Complete more exams to see progress chart"
                else stringResource(id = R.string.progress_no_enough_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        val xStep = if (scores.size > 1) size.width / (scores.size - 1) else 0f
        val yMin = 0.0

        // Draw grid lines
        val gridLines = 5
        for (i in 0..gridLines) {
            val y = size.height - (i.toFloat() / gridLines * size.height)
            drawLine(
                color = surfaceVariant,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw the line chart
        val path = Path()
        scores.reversed().forEachIndexed { index, score ->
            val x = index * xStep
            val y = size.height - ((score - yMin) / (yMax - yMin) * size.height).toFloat()
            val clampedY = y.coerceIn(0f, size.height)

            if (index == 0) {
                path.moveTo(x, clampedY)
            } else {
                path.lineTo(x, clampedY)
            }

            // Draw points
            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = Offset(x, clampedY)
            )
        }

        // Draw the line
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}