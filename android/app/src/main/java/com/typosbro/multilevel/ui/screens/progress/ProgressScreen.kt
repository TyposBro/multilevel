package com.typosbro.multilevel.ui.screens.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.R
import com.typosbro.multilevel.ui.viewmodels.ExamType
import com.typosbro.multilevel.ui.viewmodels.GenericExamResultSummary
import com.typosbro.multilevel.ui.viewmodels.HistoryPeriod
import com.typosbro.multilevel.ui.viewmodels.ProgressViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A sensible order for displaying the practice part tabs
private val multilevelPartOrder = listOf("FULL", "P1_1", "P1_2", "P2", "P3")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onNavigateToIeltsResult: (resultId: String) -> Unit,
    onNavigateToMultilevelResult: (resultId: String) -> Unit,
    onNavigateToSubscription: () -> Unit, // NEW: Callback to navigate to subscription page
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // NEW: A side-effect to handle navigation when the user needs to subscribe
    LaunchedEffect(uiState.navigateToSubscription) {
        if (uiState.navigateToSubscription) {
            onNavigateToSubscription()
            viewModel.onSubscriptionNavigationHandled() // Reset the trigger
        }
    }

    val currentHistory = when (uiState.selectedTab) {
        ExamType.IELTS -> uiState.ieltsHistory
        ExamType.MULTILEVEL -> uiState.multilevelHistory[uiState.selectedMultilevelPart]
            ?: emptyList()
    }

    val multilevelMaxScores = mapOf(
        "FULL" to 72.0, "P1_1" to 12.0, "P1_2" to 12.0, "P2" to 24.0, "P3" to 24.0
    )
    val yMaxForChart = when (uiState.selectedTab) {
        ExamType.IELTS -> 9.0
        ExamType.MULTILEVEL -> multilevelMaxScores[uiState.selectedMultilevelPart] ?: 72.0
    }

    Scaffold(topBar = { TopAppBar(title = { Text(text = stringResource(id = R.string.progress_title)) }) }) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ExamTypeSwitcher(
                selectedType = uiState.selectedTab,
                onTypeSelected = { viewModel.selectTab(it) },
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.selectedTab == ExamType.MULTILEVEL && uiState.multilevelHistory.isNotEmpty()) {
                MultilevelPartSwitcher(
                    availableParts = uiState.multilevelHistory.keys,
                    selectedPart = uiState.selectedMultilevelPart,
                    onPartSelected = { viewModel.selectMultilevelPart(it) }
                )
            }

            // NEW: History period switcher
            HistoryPeriodSwitcher(
                selectedPeriod = uiState.selectedPeriod,
                onPeriodSelected = { viewModel.selectPeriod(it) }
            )


            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (currentHistory.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(id = R.string.progress_empty),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(id = R.string.progress_score),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        ScoreHistoryChart(
                            scores = currentHistory.map { it.score },
                            yMax = yMaxForChart, // Use dynamic max value
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(16.dp)
                        )
                    }

                    item {
                        Text(
                            text = stringResource(id = R.string.progress_history),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 24.dp,
                                bottom = 8.dp
                            )
                        )
                    }
                    items(currentHistory) { result ->
                        ExamHistoryItem(result = result, onClick = {
                            when (result.type) {
                                ExamType.IELTS -> onNavigateToIeltsResult(result.id)
                                ExamType.MULTILEVEL -> onNavigateToMultilevelResult(result.id)
                            }
                        })
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryPeriodSwitcher(
    selectedPeriod: HistoryPeriod,
    onPeriodSelected: (HistoryPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val periods = HistoryPeriod.entries.toTypedArray()
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        periods.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selectedPeriod,
                onClick = { onPeriodSelected(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size)
            ) {
                val label = when (period) {
                    HistoryPeriod.SEVEN_DAYS -> "7 days"
                    HistoryPeriod.ONE_MONTH -> "1 month"
                    HistoryPeriod.SIX_MONTHS -> "6 months"
                }
                Text(label)
            }
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
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
                        "FULL" -> "Full"
                        "P1_1" -> "1.1"
                        "P1_2" -> "1.2"
                        "P2" -> "2"
                        "P3" -> "3"
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
        modifier = modifier.padding(
            horizontal = 16.dp,
            vertical = 8.dp
        )
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
            Text(text = stringResource(id = R.string.progress_no_enough_data))
        }
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val xStep = if (scores.size > 1) size.width / (scores.size - 1) else 0f
        val yMin = 0.0

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
            drawCircle(color = primaryColor, radius = 8f, center = Offset(x, clampedY))
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 5f, cap = StrokeCap.Round)
        )
    }
}