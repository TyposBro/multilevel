// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/screens/progress/ProgressScreen.kt
package org.milliytechnology.spiko.ui.screens.progress

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.milliytechnology.spiko.R
import org.milliytechnology.spiko.ui.viewmodels.DurationFilter
import org.milliytechnology.spiko.ui.viewmodels.ExamStatistics
import org.milliytechnology.spiko.ui.viewmodels.GenericExamResultSummary
import org.milliytechnology.spiko.ui.viewmodels.ProgressViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val multilevelPartOrder = listOf("FULL", "P1_1", "P1_2", "P2", "P3")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onNavigateToMultilevelResult: (resultId: String) -> Unit,
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentHistory = viewModel.getCurrentHistory()
    val availableMultilevelParts = viewModel.getAvailableMultilevelParts()
    val statistics = viewModel.getStatistics()

    val multilevelMaxScores =
        mapOf("FULL" to 72.0, "P1_1" to 12.0, "P1_2" to 22.0, "P2" to 18.0, "P3" to 20.0)
    val yMaxForChart = multilevelMaxScores[uiState.selectedMultilevelPart] ?: 72.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.progress_title)) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Filters Row
            FiltersRow(
                selectedDuration = uiState.selectedDuration,
                onDurationSelected = { viewModel.selectDuration(it) },
                availableMultilevelParts = availableMultilevelParts,
                selectedMultilevelPart = uiState.selectedMultilevelPart,
                onMultilevelPartSelected = { viewModel.selectMultilevelPart(it) },
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (currentHistory.isEmpty()) {
                EmptyStateCard(
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // Score Chart
                    item {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
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
                    items(currentHistory.size) { index ->
                        ExamHistoryItem(
                            result = currentHistory[index],
                            onClick = {
                                onNavigateToMultilevelResult(currentHistory[index].id)
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
fun FiltersRow(
    selectedDuration: DurationFilter,
    onDurationSelected: (DurationFilter) -> Unit,
    availableMultilevelParts: Set<String>,
    selectedMultilevelPart: String,
    onMultilevelPartSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Duration Filter Dropdown
        DurationFilterDropdown(
            selectedDuration = selectedDuration,
            onDurationSelected = onDurationSelected,
            modifier = Modifier.weight(1f)
        )

        // Multilevel Part Filter Dropdown
        val partsToShow = multilevelPartOrder.filter { it in availableMultilevelParts }
        if (partsToShow.size > 1) {
            MultilevelPartDropdown(
                availableParts = partsToShow,
                selectedPart = selectedMultilevelPart,
                onPartSelected = onMultilevelPartSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DurationFilterDropdown(
    selectedDuration: DurationFilter,
    onDurationSelected: (DurationFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = selectedDuration.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DurationFilter.values().forEach { duration ->
                DropdownMenuItem(
                    text = { Text(duration.displayName) },
                    onClick = {
                        onDurationSelected(duration)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun MultilevelPartDropdown(
    availableParts: List<String>,
    selectedPart: String,
    onPartSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val getPartLabel = @Composable { part: String ->
        when (part) {
            "FULL" -> "Full Mock"
            "P1_1" -> "Part 1.1"
            "P1_2" -> "Part 1.2"
            "P2" -> "Part 2"
            "P3" -> "Part 3"
            else -> part
        }
    }

    Box(modifier = modifier) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = getPartLabel(selectedPart), style = MaterialTheme.typography.bodyMedium)
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            availableParts.forEach { part ->
                DropdownMenuItem(
                    text = { Text(getPartLabel(part)) },
                    onClick = {
                        onPartSelected(part)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun StatisticsCard(statistics: ExamStatistics, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.progress_statistics),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = stringResource(R.string.progress_avg_score),
                    value = statistics.averageScore.roundToInt().toString()
                )
                StatItem(
                    label = stringResource(R.string.progress_best_score),
                    value = statistics.bestScore.roundToInt().toString()
                )
                StatItem(
                    label = stringResource(R.string.progress_latest),
                    value = statistics.latestScore.roundToInt().toString()
                )
                StatItem(
                    label = stringResource(R.string.progress_change),
                    value = if (statistics.improvement > 0) "+${statistics.improvement.roundToInt()}" else statistics.improvement.roundToInt()
                        .toString(),
                    valueColor = when {
                        statistics.improvement > 0 -> MaterialTheme.colorScheme.primary // Consider a dedicated positive color if available
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
            style = MaterialTheme.typography.titleLarge,
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
fun EmptyStateCard(selectedDuration: DurationFilter, selectedPart: String) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.progress_no_results_found),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            val partLabel = when (selectedPart) {
                "FULL" -> "Full Mock exams"
                "P1_1" -> "Part 1.1 exams"
                "P1_2" -> "Part 1.2 exams"
                "P2" -> "Part 2 exams"
                "P3" -> "Part 3 exams"
                else -> ""
            }
            Text(
                text = stringResource(
                    R.string.progress_no_completed_exams,
                    partLabel,
                    selectedDuration.displayName.lowercase()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ExamHistoryItem(result: GenericExamResultSummary, onClick: () -> Unit) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    ListItem(
        headlineContent = { Text(result.scoreLabel, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(dateFormatter.format(Date(result.examDate))) },
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
                text = stringResource(R.string.progress_chart_empty_state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val xStep = if (scores.size > 1) size.width / (scores.size - 1) else 0f
        val yMin = 0.0

        val path = Path()
        // Draw the scores in chronological order (oldest to newest)
        scores.reversed().forEachIndexed { index, score ->
            val x = index * xStep
            val y = size.height - ((score - yMin) / (yMax - yMin) * size.height).toFloat()
            val clampedY = y.coerceIn(0f, size.height)

            if (index == 0) {
                path.moveTo(x, clampedY)
            } else {
                path.lineTo(x, clampedY)
            }
        }
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}