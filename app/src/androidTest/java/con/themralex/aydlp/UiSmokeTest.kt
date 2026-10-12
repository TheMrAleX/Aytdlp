package con.themralex.aydlp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import con.themralex.aydlp.ui.MainActivity
import org.junit.Rule
import org.junit.Test

class UiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun onboardingAndNavigation() {
        compose.onNodeWithText("Let's begin").performClick()
        compose.onNodeWithText("Queue").performClick()
        compose.onNodeWithText("Your queue").assertExists()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Every detail, yours.").assertExists()
    }
}
