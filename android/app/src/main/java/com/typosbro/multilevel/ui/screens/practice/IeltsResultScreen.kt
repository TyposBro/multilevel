package com.typosbro.multilevel.ui.screens.practice

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.R
import com.typosbro.multilevel.data.remote.models.Criterion
import com.typosbro.multilevel.data.remote.models.ExamResultResponse
import com.typosbro.multilevel.data.remote.models.TranscriptEntry
import com.typosbro.multilevel.ui.viewmodels.ExamResultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamResultScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExamResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.exam_result_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.button_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.error != null -> {
                    Text(
                        text = stringResource(
                            id = R.string.error_message,
                            uiState.error ?: "500"
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }

                uiState.result != null -> {
                    ResultDetailsContent(result = uiState.result!!)
                }
            }
        }
    }
}

@Composable
fun ResultDetailsContent(result: ExamResultResponse) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val feedbackString = stringResource(id = R.string.exam_result_feedback)
    val transcriptString = stringResource(id = R.string.exam_result_transcript)
    val tabs = listOf(feedbackString, transcriptString)

    Column(modifier = Modifier.fillMaxSize()) {
        OverallScoreCard(score = result.overallBand)

        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> FeedbackTab(criteria = result.criteria)
            1 -> TranscriptTab(transcript = result.transcript)
        }
    }
}

@Composable
fun OverallScoreCard(score: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = R.string.exam_result_overall),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "%.1f".format(score),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun FeedbackTab(criteria: List<Criterion>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(criteria) { criterion ->
            CriterionCard(criterion = criterion)
        }
    }
}

@Composable
fun CriterionCard(criterion: Criterion) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = criterion.criterionName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "%.1f".format(criterion.bandScore),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                text = criterion.feedback,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp
            )

            if (!criterion.examples.isNullOrEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.exam_result_suggestion),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                criterion.examples.forEach { example ->
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = "\"${example.userQuote}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SubdirectoryArrowRight,
                                contentDescription = stringResource(id = R.string.exam_result_suggestion_description),
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = example.suggestion,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (criterion.examples.last() != example) {
                            HorizontalDivider(
                                Modifier
                                    .padding(
                                        top = 12.dp,
                                        start = 8.dp,
                                        end = 8.dp
                                    )
                                    .alpha(0.2f)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun TranscriptTab(transcript: List<TranscriptEntry>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        reverseLayout = true
    ) {
        items(transcript.reversed()) { entry ->
            TranscriptItem(entry = entry)
        }
    }
}

@Composable
fun TranscriptItem(entry: TranscriptEntry) {
    val isUser = entry.speaker.equals("User", ignoreCase = true)
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor =
        if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val shape =
        if (isUser) MaterialTheme.shapes.medium.copy(bottomEnd = CornerSize(0.dp)) else MaterialTheme.shapes.medium.copy(
            bottomStart = CornerSize(0.dp)
        )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Text(
                text = entry.speaker,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Card(
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = bubbleColor)
            ) {
                Text(
                    text = entry.text.trim(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}