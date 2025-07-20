// {PATH_TO_PROJECT}/app/src/main/java/org/milliytechnology/spiko/ui/component/ImageLoader.kt
package org.milliytechnology.spiko.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import org.milliytechnology.spiko.R

/**
 * A Material 3-styled image loading component that handles loading and error states visually.
 * Uses SubcomposeAsyncImage to display composable content for loading/error states.
 *
 * @param imageUrl The URL of the image to load. Can be null.
 * @param contentDescription A description of the image for accessibility.
 * @param modifier The modifier to be applied to the image.
 * @param contentScale The scaling algorithm to apply to the image. Defaults to [ContentScale.Crop].
 */
@Composable
fun ImageLoader(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            // Crossfade is handled by SubcomposeAsyncImage by default, so we don't need it here.
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            // Show a loading indicator while the image is being fetched.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        error = {
            // Show a broken image icon if the image fails to load.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.BrokenImage,
                    contentDescription = stringResource(R.string.image_load_error),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    )
}