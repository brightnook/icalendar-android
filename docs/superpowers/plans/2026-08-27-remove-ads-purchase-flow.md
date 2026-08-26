# Remove Ads Purchase Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a signed-in user buy or restore permanent ad removal through Google Play and have the banner react only to confirmed ownership.

**Architecture:** `BillingGateway` exposes refresh, restore, and a suspending purchase operation that converts the Play Billing callback into a typed result. `MonetizationRepository` maps all billing outcomes to the existing `MonetizationUiState`. AppRoot owns the Android Activity boundary and SettingsScreen remains a stateless UI that receives monetization state and callbacks.

**Tech Stack:** Kotlin, Coroutines, Jetpack Compose Material 3, Google Play Billing 9.1.0, JUnit 5, Robolectric/Compose UI testing.

**Spec:** `docs/superpowers/specs/2026-08-27-remove-ads-purchase-flow-design.md`

## Global Constraints

- The one-time Google Play product ID is exactly `remove_ads_forever`.
- The UI price comes from Play Billing and is never hard-coded.
- Ads remain visible for unavailable, cancelled, failed, and pending purchases.
- Only a confirmed `PURCHASED` state makes `canShowAds` false.
- The banner remains only on the authenticated main calendar screen.
- Release builds require a real `adMobAppId`; debug builds continue using Google sample values.
- Add Chinese, English, and Japanese strings for all user-facing purchase copy.

---

### Task 1: Model typed purchase outcomes and repository state updates

**Files:**
- Modify: `app/src/main/java/com/victor2319/ioscalendar/monetization/BillingGateway.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/monetization/MonetizationRepository.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/monetization/MonetizationModels.kt`
- Modify: `app/src/test/java/com/victor2319/ioscalendar/monetization/MonetizationRepositoryTest.kt`

**Interfaces:**
- Produces `sealed interface PurchaseOutcome` with `Completed(snapshot: BillingSnapshot)`, `Pending(snapshot: BillingSnapshot)`, `Cancelled(price: String?)`, and `Failed`.
- Produces `suspend fun BillingGateway.purchase(activity: Activity): PurchaseOutcome`.
- Produces `suspend fun MonetizationRepository.purchase(activity: Activity)`.

- [ ] **Step 1: Write failing repository tests**

```kotlin
@Test
fun completedPurchaseDisablesAdsImmediately() = runTest {
    val repository = MonetizationRepository(FakeBillingGateway(purchase = PurchaseOutcome.Completed(BillingSnapshot(true, "¥250"))))
    repository.purchase(Robolectric.buildActivity(Activity::class.java).get())
    assertEquals(PurchaseUiState.OWNED, repository.state.value.purchaseState)
    assertFalse(repository.state.value.canShowAds)
}

@Test
fun cancelledPurchaseKeepsAdsAndPriceAvailable() = runTest {
    val repository = MonetizationRepository(FakeBillingGateway(purchase = PurchaseOutcome.Cancelled("¥250")))
    repository.purchase(Robolectric.buildActivity(Activity::class.java).get())
    assertEquals(PurchaseUiState.AVAILABLE, repository.state.value.purchaseState)
    assertTrue(repository.state.value.canShowAds)
    assertEquals("¥250", repository.state.value.price)
}
```

- [ ] **Step 2: Run the new tests and verify RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.victor2319.ioscalendar.monetization.MonetizationRepositoryTest" --no-daemon --console=plain`

Expected: compilation failure because `PurchaseOutcome` and `MonetizationRepository.purchase` do not exist.

- [ ] **Step 3: Add the typed interface and minimal repository mapping**

```kotlin
sealed interface PurchaseOutcome {
    data class Completed(val snapshot: BillingSnapshot) : PurchaseOutcome
    data class Pending(val snapshot: BillingSnapshot) : PurchaseOutcome
    data class Cancelled(val price: String?) : PurchaseOutcome
    data object Failed : PurchaseOutcome
}

interface BillingGateway {
    suspend fun refresh(): BillingSnapshot
    suspend fun restore(): BillingSnapshot
    suspend fun purchase(activity: Activity): PurchaseOutcome
}
```

Map `Completed` and `Pending` with the existing `BillingSnapshot.toUiState`; map `Cancelled` to AVAILABLE with the supplied price and `Failed` to UNAVAILABLE with a null price.

- [ ] **Step 4: Run the repository tests and verify GREEN**

Run the command from Step 2.

Expected: all `MonetizationRepositoryTest` cases pass.

- [ ] **Step 5: Commit the model and repository change**

```bash
git add app/src/main/java/com/victor2319/ioscalendar/monetization/BillingGateway.kt app/src/main/java/com/victor2319/ioscalendar/monetization/MonetizationModels.kt app/src/main/java/com/victor2319/ioscalendar/monetization/MonetizationRepository.kt app/src/test/java/com/victor2319/ioscalendar/monetization/MonetizationRepositoryTest.kt
git commit -m "feat: model remove ads purchase outcomes"
```

### Task 2: Launch and map Google Play purchase callbacks

**Files:**
- Modify: `app/src/main/java/com/victor2319/ioscalendar/monetization/PlayBillingGateway.kt`
- Modify: `app/src/test/java/com/victor2319/ioscalendar/monetization/PlayBillingMapperTest.kt`

**Interfaces:**
- Consumes `PurchaseOutcome` and `BillingGateway.purchase(activity)` from Task 1.
- Produces `internal fun purchaseOutcome(responseCode: Int, purchases: List<PlayPurchase>, price: String?): PurchaseOutcome`.

- [ ] **Step 1: Write failing mapper tests**

```kotlin
@Test
fun completedPlayPurchaseMapsToCompletedOutcome() {
    val outcome = purchaseOutcome(BillingClient.BillingResponseCode.OK, listOf(PlayPurchase(PURCHASED, true)), "¥250")
    assertEquals(PurchaseOutcome.Completed(BillingSnapshot(true, "¥250")), outcome)
}

@Test
fun cancelledPlayPurchaseMapsToAvailableOutcome() {
    val outcome = purchaseOutcome(BillingClient.BillingResponseCode.USER_CANCELED, emptyList(), "¥250")
    assertEquals(PurchaseOutcome.Cancelled("¥250"), outcome)
}
```

- [ ] **Step 2: Run mapper tests and verify RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.victor2319.ioscalendar.monetization.PlayBillingMapperTest" --no-daemon --console=plain`

Expected: compilation failure because `purchaseOutcome` does not exist.

- [ ] **Step 3: Implement one-shot callback bridging**

Create a `CompletableDeferred<PurchaseOutcome>` before `launchBillingFlow`. Query product details, build `BillingFlowParams` using `oneTimePurchaseOfferDetails.offerToken`, then launch. Complete the deferred from `PurchasesUpdatedListener`: acknowledge completed purchases once, report Pending for pending purchases, Cancelled for `USER_CANCELED`, and Failed for all other non-OK responses. Clear the deferred in `finally` so a later purchase can begin.

- [ ] **Step 4: Run mapper tests and verify GREEN**

Run the command from Step 2.

Expected: all PlayBillingMapperTest cases pass, including existing acknowledgement checks.

- [ ] **Step 5: Commit the Play Billing gateway change**

```bash
git add app/src/main/java/com/victor2319/ioscalendar/monetization/PlayBillingGateway.kt app/src/test/java/com/victor2319/ioscalendar/monetization/PlayBillingMapperTest.kt
git commit -m "feat: launch Google Play ad removal purchase"
```

### Task 3: Add settings controls and localized state labels

**Files:**
- Modify: `app/src/main/java/com/victor2319/ioscalendar/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/victor2319/ioscalendar/ui/AppRoot.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Modify: `app/src/androidTest/java/com/victor2319/ioscalendar/ui/settings/SettingsScreenTest.kt`

**Interfaces:**
- Consumes `MonetizationUiState` and `onPurchase: () -> Unit`, `onRestorePurchase: () -> Unit` callbacks.
- Produces a SettingsScreen Support section tagged `settings-section-support`, a purchase action tagged `remove-ads`, and a restore action tagged `restore-purchases`.

- [ ] **Step 1: Write failing Compose tests**

```kotlin
@Test
fun availableMonetizationShowsPriceAndPurchaseAction() {
    composeRule.setContent { SettingsScreen(state = SettingsUiState(), monetization = MonetizationUiState(true, AVAILABLE, "¥250")) }
    composeRule.onNodeWithTag("remove-ads").assertIsDisplayed()
    composeRule.onNodeWithText("¥250").assertIsDisplayed()
}

@Test
fun ownedMonetizationDoesNotOfferPurchase() {
    composeRule.setContent { SettingsScreen(state = SettingsUiState(), monetization = MonetizationUiState(false, OWNED, "¥250")) }
    composeRule.onNodeWithTag("remove-ads").assertDoesNotExist()
}
```

- [ ] **Step 2: Run settings UI tests and verify RED**

Run: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.victor2319.ioscalendar.ui.settings.SettingsScreenTest`

Expected: compilation failure because SettingsScreen has no monetization parameter or purchase action tag.

- [ ] **Step 3: Implement the stateless Support section and AppRoot wiring**

Add `monetization`, `onPurchase`, and `onRestorePurchase` optional parameters to SettingsScreen. Use AppRoot's current Activity to call repository purchase/restore from `LaunchedEffect`/coroutine scope. In settings, show the Play price, a disabled loading state, completed text, pending text, unavailable text, and restore action. Add resource strings in English, Simplified Chinese, and Japanese.

- [ ] **Step 4: Run settings UI tests and verify GREEN**

Run the command from Step 2.

Expected: settings tests pass on an available emulator; if no emulator is connected, run equivalent Robolectric unit coverage and record the instrumentation test as pending device verification.

- [ ] **Step 5: Commit the settings UI change**

```bash
git add app/src/main/java/com/victor2319/ioscalendar/ui/settings/SettingsScreen.kt app/src/main/java/com/victor2319/ioscalendar/ui/AppRoot.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/main/res/values-ja/strings.xml app/src/androidTest/java/com/victor2319/ioscalendar/ui/settings/SettingsScreenTest.kt
git commit -m "feat: add remove ads settings controls"
```

### Task 4: Verify the integrated ad-supported flow

**Files:**
- Modify: `app/src/androidTest/java/com/victor2319/ioscalendar/ui/main/MainCalendarScreenTest.kt`

**Interfaces:**
- Consumes the Task 3 settings action and Task 1 `MonetizationUiState` mapping.
- Produces regression coverage proving only OWNED hides `AdBannerSlot`.

- [ ] **Step 1: Write a failing regression test**

```kotlin
@Test
fun pendingPurchaseStillDisplaysBanner() {
    composeRule.setContent { MainCalendarScreen(monetization = MonetizationUiState(true, PENDING, "¥250")) }
    composeRule.onNodeWithTag("ad-banner-slot").assertExists()
}
```

- [ ] **Step 2: Run the focused main screen test and verify RED**

Run: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.victor2319.ioscalendar.ui.main.MainCalendarScreenTest`

Expected: fail until the test tag and pending banner behaviour are explicit.

- [ ] **Step 3: Implement the smallest banner testability adjustment**

Expose the existing banner container with the `ad-banner-slot` test tag without changing the banner placement or its consent gating.

- [ ] **Step 4: Run focused and full relevant tests**

Run:

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.victor2319.ioscalendar.monetization.*" --no-daemon --console=plain
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.victor2319.ioscalendar.ui.main.MainCalendarScreenTest
./gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Expected: focused monetization and main-calendar tests pass and a debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 5: Commit verification coverage**

```bash
git add app/src/androidTest/java/com/victor2319/ioscalendar/ui/main/MainCalendarScreenTest.kt
git commit -m "test: cover ad state after purchase"
```
