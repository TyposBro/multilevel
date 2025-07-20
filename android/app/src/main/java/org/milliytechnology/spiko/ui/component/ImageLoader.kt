package org.milliytechnology.spiko.ui.component

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
fun ImageLoader(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    Log.d("AsyncImage", "Loading image: $imageUrl")

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .listener(
                onStart = {
                    isLoading = true
                    hasError = false
                    Log.d("AsyncImage", "Started loading: $imageUrl")
                },
                onSuccess = { _, _ ->
                    isLoading = false
                    Log.d("AsyncImage", "Successfully loaded: $imageUrl")
                },
                onError = { _, error ->
                    isLoading = false
                    hasError = true
                    Log.e("AsyncImage", "Failed to load: $imageUrl", error.throwable)
                }
            )
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,

        )
}