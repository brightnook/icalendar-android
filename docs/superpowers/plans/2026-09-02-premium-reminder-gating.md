# Premium Reminder Gating Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Limit free users to the earliest iCloud display reminder per event instance while premium owners receive every supported iCloud reminder and remain ad-free.

**Architecture:** Persist and parse every supported iCloud `VALARM`, then apply entitlement filtering only when `AndroidReminderScheduler` registers Android alarms. A purchase-state change invokes reconciliation immediately, allowing an upgrade to schedule future additional alarms without another iCloud sync.

**Tech Stack:** Kotlin, coroutines `StateFlow`, Room, Android `AlarmManager`, Google Play Billing state wrapped by `MonetizationRepository`, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-02-reminder-premium-boundary-design.md`

## Global Constraints

- Free users receive the earliest trigger instant from supported iCloud `ACTION:DISPLAY` alarms for each event instance.
- Premium means `PurchaseUiState.OWNED`; pending purchases use the free selection.
- Recurring event occurrences are eligible for reminders in both tiers.
- Non-display alarms and Android-side repeat/snooze behavior remain unsupported.
- A purchase state change reconciles alarms immediately, without an iCloud sync.

---

### Task 1: Pure entitlement reminder policy

**Files:**

- Create: `app/src/main/java/com/brightnook/nookcalendar/reminder/ReminderEntitlementPolicy.kt`
- Create: `app/src/test/java/com/brightnook/nookcalendar/reminder/ReminderEntitlementPolicyTest.kt`

**Interfaces:**

- Consumes: `ScheduledReminder` from `domain/Models.kt`.
- Produces: `fun remindersForEntitlement(reminders: List<ScheduledReminder>, premium: Boolean): List<ScheduledReminder>`.

- [ ] **Step 1: Write the failing test**

```kotlin
private fun reminder(id: String, instanceId: String, at: String) = ScheduledReminder(
    reminderId = id,
    instanceId = instanceId,
    date = LocalDate.parse("2026-09-02"),
    triggerAt = Instant.parse(at),
    title = id,
    location = null,
)
private val earlyA = reminder("early-a", "instance-a", "2026-09-02T08:45:00Z")
private val lateA = reminder("late-a", "instance-a", "2026-09-02T08:55:00Z")
private val earlyB = reminder("early-b", "instance-b", "2026-09-02T08:50:00Z")

@Test
fun freeTierKeepsOnlyEarliestTriggerForEachInstance() {
    assertEquals(listOf(earlyA, earlyB), remindersForEntitlement(listOf(lateA, earlyA, earlyB), premium = false))
}

@Test
fun premiumTierKeepsEveryReminderInTriggerOrder() {
    assertEquals(listOf(earlyA, lateA, earlyB), remindersForEntitlement(listOf(lateA, earlyA, earlyB), premium = true))
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.brightnook.nookcalendar.reminder.ReminderEntitlementPolicyTest`

Expected: compilation failure because `remindersForEntitlement` does not exist.

- [ ] **Step 3: Write the minimal policy**

```kotlin
fun remindersForEntitlement(reminders: List<ScheduledReminder>, premium: Boolean): List<ScheduledReminder> =
    if (premium) reminders.sortedBy(ScheduledReminder::triggerAt)
    else reminders.groupBy(ScheduledReminder::instanceId)
        .values.mapNotNull { it.minByOrNull(ScheduledReminder::triggerAt) }
        .sortedBy(ScheduledReminder::triggerAt)
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.brightnook.nookcalendar.reminder.ReminderEntitlementPolicyTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/brightnook/nookcalendar/reminder/ReminderEntitlementPolicy.kt app/src/test/java/com/brightnook/nookcalendar/reminder/ReminderEntitlementPolicyTest.kt
git commit -m "feat: gate calendar reminders by premium entitlement"
```

### Task 2: Apply entitlement during Android alarm reconciliation

**Files:**

- Modify: `app/src/main/java/com/brightnook/nookcalendar/reminder/ReminderScheduler.kt`
- Modify: `app/src/main/java/com/brightnook/nookcalendar/AppContainer.kt`
- Test: `app/src/test/java/com/brightnook/nookcalendar/reminder/ReminderEntitlementPolicyTest.kt`

**Interfaces:**

- Consumes: `remindersForEntitlement(reminders, premium)` from Task 1 and `MonetizationRepository.state`.
- Produces: `AndroidReminderScheduler.reconcile()` schedules only selected reminders.

- [ ] **Step 1: Add the pending-purchase test**

```kotlin
@Test
fun pendingPurchaseUsesTheFreeTierSelection() {
    assertEquals(listOf(earlyA), remindersForEntitlement(listOf(lateA, earlyA), premium = false))
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.brightnook.nookcalendar.reminder.ReminderEntitlementPolicyTest.pendingPurchaseUsesTheFreeTierSelection`

Expected: PASS; pending passes `premium = false`.

- [ ] **Step 3: Inject entitlement into the scheduler**

```kotlin
class AndroidReminderScheduler(
    private val context: Context,
    private val repository: CalendarRepository,
    private val isPremium: () -> Boolean,
) : ReminderScheduler {
    override suspend fun reconcile() {
        val planned = remindersForEntitlement(repository.upcomingReminders(SystemClockProvider.now), isPremium())
        mutableHasUpcomingReminders.value = planned.isNotEmpty()
        val plannedIds = planned.mapTo(linkedSetOf()) { it.reminderId }
        preferences.getStringSet(SCHEDULED_IDS, emptySet()).orEmpty()
            .filterNot(plannedIds::contains)
            .forEach(::cancel)
        planned.forEach(::schedule)
        preferences.edit().putStringSet(SCHEDULED_IDS, plannedIds).apply()
    }
}
```

In `AppContainer`, pass:

```kotlin
isPremium = { monetizationRepository.state.value.purchaseState == PurchaseUiState.OWNED }
```

- [ ] **Step 4: Compile production and test sources**

Run: `./gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/brightnook/nookcalendar/reminder/ReminderScheduler.kt app/src/main/java/com/brightnook/nookcalendar/AppContainer.kt
git commit -m "feat: filter scheduled reminders by entitlement"
```

### Task 3: Reconcile immediately after a purchase state change

**Files:**

- Modify: `app/src/main/java/com/brightnook/nookcalendar/ui/AppRoot.kt`
- Test: `app/src/test/java/com/brightnook/nookcalendar/ui/AppRootPurchaseActionTest.kt`

**Interfaces:**

- Consumes: `PurchaseUiState` and `ReminderScheduler.reconcile()`.
- Produces: a transition to `PurchaseUiState.OWNED` schedules all upcoming supported iCloud reminders without manual refresh.

- [ ] **Step 1: Write the failing decision test**

```kotlin
private class RecordingReminderScheduler : ReminderScheduler {
    override val hasUpcomingReminders = MutableStateFlow(false)
    var reconcileCalls = 0
    override suspend fun reconcile() { reconcileCalls++ }
    override fun initialize() = Unit
}

@Test
fun ownedPurchaseStateRequestsReminderReconciliation() = runTest {
    val scheduler = RecordingReminderScheduler()
    reconcileRemindersForPurchaseState(PurchaseUiState.OWNED, scheduler)
    assertEquals(1, scheduler.reconcileCalls)
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.brightnook.nookcalendar.ui.AppRootPurchaseActionTest.ownedPurchaseStateRequestsReminderReconciliation`

Expected: compilation failure because `reconcileRemindersForPurchaseState` does not exist.

- [ ] **Step 3: Add testable decision and Compose effect**

```kotlin
internal suspend fun reconcileRemindersForPurchaseState(state: PurchaseUiState, scheduler: ReminderScheduler) {
    if (state == PurchaseUiState.OWNED) scheduler.reconcile()
}

LaunchedEffect(monetization.purchaseState) {
    reconcileRemindersForPurchaseState(monetization.purchaseState, container.reminderScheduler)
}
```

- [ ] **Step 4: Run the focused test and compile**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.brightnook.nookcalendar.ui.AppRootPurchaseActionTest.ownedPurchaseStateRequestsReminderReconciliation :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/brightnook/nookcalendar/ui/AppRoot.kt app/src/test/java/com/brightnook/nookcalendar/ui/AppRootPurchaseActionTest.kt
git commit -m "feat: schedule premium reminders after upgrade"
```

### Task 4: Build and device verification

**Files:**

- Modify: `docs/superpowers/specs/2026-09-02-reminder-premium-boundary-design.md` only if verification exposes a specification contradiction.

**Interfaces:**

- Consumes: premium-gated `AndroidReminderScheduler` and purchase-triggered reconciliation.
- Produces: a verified debug build and test evidence.

- [ ] **Step 1: Build a debug APK**

Run: `./gradlew.bat :app:assembleDebug`

Expected: PASS and create `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Verify free tier**

Create one iCloud event with two display reminders, sync while not entitled, and verify only the earliest trigger is scheduled and displayed.

- [ ] **Step 3: Verify premium upgrade or restore**

Use a Play test account to obtain or restore entitlement, then verify the second future reminder is scheduled without another iCloud refresh and ads remain hidden.

- [ ] **Step 4: Verify a recurring iCloud event**

Create a daily or weekly iCloud event with display reminders, sync, and verify free schedules the earliest reminder for each occurrence while premium schedules all configured display alarms for each occurrence.

- [ ] **Step 5: Commit verification documentation only if changed**

```bash
git add docs/superpowers/specs/2026-09-02-reminder-premium-boundary-design.md
git commit -m "docs: record premium reminder verification"
```
