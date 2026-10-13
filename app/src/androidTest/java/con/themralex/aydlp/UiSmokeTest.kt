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
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir, "ui-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun onboardingNavigationAndReadableAnalysisError() {
        compose.onNodeWithText("Let's begin").performClick()
        compose.onNodeWithText("Analyze link").assertIsNotEnabled()
        capture("download-dark")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("--invalid")
        compose.onNodeWithText("Analyze link").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("We couldn't read this link").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("We couldn't read this link").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Copy report").assertExists()
        capture("analysis-error")
        compose.onNodeWithText("Queue").performClick()
        compose.onNodeWithText("Your queue").assertExists()
        capture("queue")
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Made yours").assertExists()
        capture("library")
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Every detail, yours.").assertExists()
        capture("settings-dark")
        compose.onNodeWithText("Light").performClick()
        compose.onNodeWithText("Download").performClick()
        capture("download-light")
    }
}
