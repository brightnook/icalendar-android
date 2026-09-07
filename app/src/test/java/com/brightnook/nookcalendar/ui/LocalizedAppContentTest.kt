package com.brightnook.nookcalendar.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.domain.AppLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalizedAppContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun manualJapaneseUsesJapaneseResourcesForAllSettingsStringsOnAnEnglishDevice() {
        val englishContext = englishContext()
        composeRule.setContent {
            WithEnglishBaseContext(englishContext) {
                LocalizedAppContent(AppLanguage.JAPANESE) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        modifier = androidx.compose.ui.Modifier.testTag("localized-title"),
                    )
                    Text(
                        text = stringResource(R.string.settings_language_japanese),
                        modifier = androidx.compose.ui.Modifier.testTag("localized-japanese-language"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("localized-title").assertTextEquals("設定")
        composeRule.onNodeWithTag("localized-japanese-language").assertTextEquals("日本語")
    }

    @Test
    fun changingTheSelectedLanguageRecomposesWithJapaneseResources() {
        val englishContext = englishContext()
        var selectedLanguage by mutableStateOf(AppLanguage.ENGLISH)
        composeRule.setContent {
            WithEnglishBaseContext(englishContext) {
                LocalizedAppContent(selectedLanguage) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        modifier = androidx.compose.ui.Modifier.testTag("localized-title"),
                    )
                    Text(
                        text = stringResource(R.string.settings_language_japanese),
                        modifier = androidx.compose.ui.Modifier.testTag("localized-japanese-language"),
                    )
                }
            }
        }
        composeRule.onNodeWithTag("localized-title").assertTextEquals("Settings")
        composeRule.onNodeWithTag("localized-japanese-language").assertTextEquals("Japanese")

        composeRule.runOnIdle { selectedLanguage = AppLanguage.JAPANESE }

        composeRule.onNodeWithTag("localized-title").assertTextEquals("設定")
        composeRule.onNodeWithTag("localized-japanese-language").assertTextEquals("日本語")
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

    private fun englishContext(): Context =
        ApplicationProvider.getApplicationContext<Context>().createConfigurationContext(
            Configuration().apply { setLocale(Locale.US) },
        )

    @androidx.compose.runtime.Composable
    private fun WithEnglishBaseContext(context: Context, content: @androidx.compose.runtime.Composable () -> Unit) {
        CompositionLocalProvider(
            LocalContext provides context,
            LocalResources provides context.resources,
            LocalConfiguration provides context.resources.configuration,
            content = content,
        )
    }
}
