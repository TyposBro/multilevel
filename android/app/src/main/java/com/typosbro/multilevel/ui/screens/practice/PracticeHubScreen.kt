// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/screens/practice/PracticeHubScreen.kt
package com.typosbro.multilevel.ui.screens.practice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.typosbro.multilevel.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeHubScreen(
    onNavigateToIELTS: () -> Unit,
    onNavigateToMultilevel: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(text = stringResource(id = R.string.practice_hub_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                onClick = onNavigateToMultilevel // Navigate to the full exam
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(id = R.string.practice_hub_multilevel),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(id = R.string.practice_hub_multilevel_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                onClick = onNavigateToIELTS // Navigate to the full exam
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(id = R.string.practice_hub_ielts),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(id = R.string.practice_hub_ielts_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}