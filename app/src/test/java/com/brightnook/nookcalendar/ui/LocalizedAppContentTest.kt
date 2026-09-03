package com.brightnook.nookcalendar.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.domain.AppLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalizedAppContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun manualJapaneseUsesJapaneseResourcesEvenOnAnEnglishDevice() {
        composeRule.setContent {
            LocalizedAppContent(AppLanguage.JAPANESE) {
                Text(
                    text = stringResource(R.string.settings_title),
                    modifier = androidx.compose.ui.Modifier.testTag("localized-title"),
                )
            }
        }

        composeRule.onNodeWithTag("localized-title").assertTextEquals("設定")
    }

    @Test
    fun changingTheSelectedLanguageRecomposesWithJapaneseResources() {
        var selectedLanguage by mutableStateOf(AppLanguage.ENGLISH)
        composeRule.setContent {
            LocalizedAppContent(selectedLanguage) {
                Text(
                    text = stringResource(R.string.settings_title),
                    modifier = androidx.compose.ui.Modifier.testTag("localized-title"),
                )
            }
        }
        composeRule.onNodeWithTag("localized-title").assertTextEquals("Settings")

        composeRule.runOnIdle { selectedLanguage = AppLanguage.JAPANESE }

        composeRule.onNodeWithTag("localized-title").assertTextEquals("設定")
    }

    @Test
    fun changingTheLanguageRecreatesTheLocalizedContentTree() {
        var selectedLanguage by mutableStateOf(AppLanguage.ENGLISH)
        var firstIdentity = ""
        composeRule.setContent {
            LocalizedAppContent(selectedLanguage) {
                val identity = remember { Any().toString() }
                if (firstIdentity.isEmpty()) firstIdentity = identity
                Text(
                    if (identity == firstIdentity) "same" else "changed",
                    modifier = androidx.compose.ui.Modifier.testTag("localized-identity"),
                )
            }
        }

        composeRule.runOnIdle { selectedLanguage = AppLanguage.JAPANESE }

        composeRule.onNodeWithTag("localized-identity").assertTextEquals("changed")
    }

    @Test
    fun stableSaveableStateSurvivesALanguageContentTreeRecreation() {
        var selectedLanguage by mutableStateOf(AppLanguage.ENGLISH)
        composeRule.setContent {
            LocalizedAppContent(selectedLanguage) {
                var settingsOpen by rememberSaveable("main-calendar-settings-open") { mutableStateOf(true) }
                Text(
                    if (settingsOpen) "settings-open" else "settings-closed",
                    modifier = androidx.compose.ui.Modifier.testTag("settings-navigation-state"),
                )
            }
        }

        composeRule.runOnIdle { selectedLanguage = AppLanguage.JAPANESE }

        composeRule.onNodeWithTag("settings-navigation-state").assertTextEquals("settings-open")
    }
}
