// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/component/LifecycleHandler.kt

package org.milliytechnology.spiko.ui.component

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * A reusable composable that manages crucial lifecycle events for an active exam.
 * 1. Keeps the screen on to prevent it from dimming or sleeping.
 * 2. Listens for the ON_STOP lifecycle event (e.g., user presses power button,
 *    switches app) and invokes the provided [onStop] callback.
 *
 * @param onStop A lambda function to be executed when the screen/app is stopped.
 */
@Composable
fun HandleAppLifecycle(onStop: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        // Find the window of the current activity
        val window = (context as? Activity)?.window

        // 1. Add the FLAG_KEEP_SCREEN_ON to prevent the screen from sleeping
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 2. Create and register a lifecycle observer
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // When the app is stopped, call the provided lambda
                onStop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Cleanup logic that runs when the composable leaves the screen
        onDispose {
            // 1. Clear the flag to allow the screen to sleep normally again
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // 2. Remove the observer to prevent memory leaks
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}