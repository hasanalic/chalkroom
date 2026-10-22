package app.chalkroom.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * A dark surface runs under the status bar, so its icons go light while this screen shows; put back
 * after. [navigationBar] does the same for the navigation bar when the dark surface reaches the bottom.
 */
@Composable
fun LightStatusBarIcons(navigationBar: Boolean = false) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view, navigationBar) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        val previousNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        if (navigationBar) controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = previous
            if (navigationBar) controller.isAppearanceLightNavigationBars = previousNav
        }
    }
}
