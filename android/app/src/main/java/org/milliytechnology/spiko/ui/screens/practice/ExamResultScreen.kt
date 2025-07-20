package org.milliytechnology.spiko.ui.screens.practice

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.R
import org.milliytechnology.spiko.data.remote.models.DetailedBreakdown
import org.milliytechnology.spiko.data.remote.models.ExamResultResponse
import org.milliytechnology.spiko.data.remote.models.FeedbackBreakdown
import org.milliytechnology.spiko.data.remote.models.FeedbackCriterion
import org.milliytechnology.spiko.data.remote.models.TranscriptEntry
import com.typosbro.multilevel.ui.viewmodels.ResultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onNavigateBack: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onNavigateBack)
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
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.error != null -> ErrorView(uiState.error!!) // Reusing ErrorView
                uiState.result != null -> MultilevelResultContent(result = uiState.result!!)
            }
        }
    }
}

@Composable
fun MultilevelResultContent(result: ExamResultResponse) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val feedbackString = stringResource(id = R.string.exam_result_feedback)
    val transcriptString = stringResource(id = R.string.exam_result_transcript)
    val tabs = listOf(feedbackString, transcriptString)

    val isSinglePartResult = result.feedbackBreakdown.size == 1

    Column(modifier = Modifier.fillMaxSize()) {
        OverallScoreCardMultilevel(
            score = result.totalScore,
            maxScore = if (isSinglePartResult) null else 72,
            isSinglePart = isSinglePartResult
        )

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
            0 -> FeedbackTabMultilevel(feedbackBreakdown = result.feedbackBreakdown)
            1 -> TranscriptTab(transcript = result.transcript)
        }
    }
}

@Composable
fun OverallScoreCardMultilevel(score: Int, maxScore: Int?, isSinglePart: Boolean) {
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
        ) {
            Text(
                text = if (isSinglePart) stringResource(R.string.exam_result_part_score) else stringResource(
                    R.string.exam_result_overall
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (maxScore != null) {
                    Text(
                        text = " / $maxScore",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FeedbackTabMultilevel(feedbackBreakdown: List<FeedbackBreakdown>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(feedbackBreakdown) { feedback ->
            FeedbackPartCard(feedback = feedback)
        }
    }
}

@Composable
fun FeedbackPartCard(feedback: FeedbackBreakdown) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = feedback.part,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${feedback.score}",
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

            // --- MODIFIED: Display detailed V2 feedback or fallback to V1 ---
            if (feedback.detailedBreakdown != null) {
                // V2 Detailed Feedback
                feedback.overallFeedback?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                DetailedFeedbackItems(detailedBreakdown = feedback.detailedBreakdown)
            } else if (feedback.feedback != null) {
                // V1 Simple Feedback (Fallback)
                Text(
                    text = feedback.feedback,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
fun DetailedFeedbackItems(detailedBreakdown: DetailedBreakdown) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CriteriaItem(
            title = "Fluency and Coherence",
            criterion = detailedBreakdown.fluencyAndCoherence
        )
        CriteriaItem(
            title = "Lexical Resource (Vocabulary)",
            criterion = detailedBreakdown.lexicalResource
        )
        CriteriaItem(
            title = "Grammatical Range and Accuracy",
            criterion = detailedBreakdown.grammaticalRangeAndAccuracy
        )
        CriteriaItem(title = "Task Achievement", criterion = detailedBreakdown.taskAchievement)
    }
}

@Composable
private fun CriteriaItem(title: String, criterion: FeedbackCriterion) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        FeedbackDetailRow(label = "✅ Positive", text = criterion.positive)
        FeedbackDetailRow(label = "💡 Suggestion", text = criterion.suggestion)
        if (criterion.example.isNotBlank() && criterion.example != "N/A") {
            FeedbackDetailRow(label = "💬 Example", text = criterion.example)
        }
    }
}

@Composable
private fun FeedbackDetailRow(label: String, text: String) {
    Row(Modifier.padding(top = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
            lineHeight = 20.sp
        )
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

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Card(colors = CardDefaults.cardColors(containerColor = bubbleColor)) {
            Text(
                text = entry.text.trim(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}