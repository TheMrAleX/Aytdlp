package con.themralex.aydlp

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import con.themralex.aydlp.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import java.io.File

class UiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Compose idleness precedes SurfaceFlinger presenting the frame.
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir, "ui-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun onboardingNavigationAndReadableAnalysisError() {
        compose.onNodeWithText("Let's begin").performClick()
        compose.onNodeWithText("Analyze").assertIsNotEnabled()
        capture("download-dark")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("--invalid")
        compose.onNodeWithText("Analyze").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Couldn't analyze the link").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Couldn't analyze the link").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Copy report").assertExists()
        capture("analysis-error")
        compose.onNodeWithText("Queue").performClick()
        compose.onNodeWithContentDescription("Pause all").assertExists()
        capture("queue")
        compose.onNodeWithText("Locate all").performClick()
        compose.onNodeWithText("No downloads yet").assertExists()
        capture("library")
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Appearance").assertExists()
        capture("settings-dark")
        compose.onNodeWithText("Light").performClick()
        compose.onNodeWithText("Download").performClick()
        capture("download-light")
    }
}
