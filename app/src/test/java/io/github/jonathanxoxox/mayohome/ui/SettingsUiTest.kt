package io.github.jonathanxoxox.mayohome.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import io.github.jonathanxoxox.mayohome.MayoHomeApp
import io.github.jonathanxoxox.mayohome.data.Settings
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Öffnet die Einstellungen und ihre Unterseiten, wie man es mit der Fernbedienung täte. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class SettingsUiTest {
    @get:Rule val rule = createComposeRule()

    /** Wie OK auf der Fernbedienung: die Klick-Aktion der fokussierbaren Fläche mit diesem Text. */
    private fun click(text: String) {
        rule.onNode(hasClickAction() and hasText(text)).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    private fun open(): () -> Boolean {
        var closed = false
        val vm = HomeViewModel(RuntimeEnvironment.getApplication() as MayoHomeApp)
        rule.setContent {
            MayoTheme(Settings()) { SettingsScreen(vm, Settings(), onClose = { closed = true }, onRequestTvPermission = {}) }
        }
        return { closed }
    }

    @Test
    fun diagnoseOpensAndStays() {
        val closed = open()
        click("Über")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Diagnose"))
        click("Diagnose")
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        rule.onNodeWithText("Gewählte Sender").assertExists()
        // Die Auswahl muss in der Unterseite liegen, nicht auf einer Kategorie links.
        rule.onNode(hasClickAction() and hasText("Zurück", substring = true)).assertIsFocused()
        assertFalse(closed())
    }

    @Test
    fun everyCategoryRenders() {
        val closed = open()
        for (c in listOf("Start", "Live TV", "Sender-Katalog", "Apps", "Aussehen", "Hauptlauncher", "Sichern & Schutz", "Über")) {
            click(c)
            rule.waitForIdle()
        }
        assertFalse(closed())
    }
}
