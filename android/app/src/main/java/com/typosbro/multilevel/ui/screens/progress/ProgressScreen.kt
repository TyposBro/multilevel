// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/screens/progress/ProgressScreen.kt
package com.typosbro.multilevel.ui.screens.progress

import ExamResultSummary
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.typosbro.multilevel.ui.viewmodels.AppViewModelProvider
import com.typosbro.multilevel.ui.viewmodels.ProgressViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    // In a real app, you'd navigate to the full result screen
    onNavigateToResult: (resultId: String) -> Unit,
    viewModel: ProgressViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Progress") }) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No exam history yet. Take a test to see your progress!")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // --- Chart Section ---
                item {
                    Text(
                        "Score Over Time",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    ScoreHistoryChart(
                        scores = uiState.history.map { it.overallBand },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(16.dp)
                    )
                }

                // --- History List Section ---
                item {
                    Text(
                        "Exam History",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
                    )
                }
                items(uiState.history.reversed()) { result ->
                    ExamHistoryItem(
                        result = result,
                        onClick = { onNavigateToResult(result.id) }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
fun ExamHistoryItem(result: ExamResultSummary, onClick: () -> Unit) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    ListItem(
        headlineContent = { Text("Overall Band: ${result.overallBand}", fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(dateFormatter.format(Date(result.examDate))) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "View Details") },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/**
 * A simple placeholder line chart.
 * For a real app, consider a library like Vico or MPAndroidChart.
 */
@Composable
fun ScoreHistoryChart(scores: List<Double>, modifier: Modifier = Modifier) {
    if (scores.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Take at least two tests to see a chart.")
        }
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val xStep = size.width / (scores.size - 1)
        // IELTS scores range from 0 to 9. We'll map this to the canvas height.
        val yMin = 0.0
        val yMax = 9.0

        val path = Path()
        scores.forEachIndexed { index, score ->
            val x = index * xStep
            val y = size.height - ((score - yMin) / (yMax - yMin) * size.height).toFloat()

            if (index == 0) {
                path.moveTo(x, y.coerceIn(0f, size.height))
            } else {
                path.lineTo(x, y.coerceIn(0f, size.height))
            }
            // Draw a circle for each data point
            drawCircle(color = primaryColor, radius = 8f, center = Offset(x, y.coerceIn(0f, size.height)))
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 5f, cap = StrokeCap.Round)
        )
    }
}