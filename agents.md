# Agents Guidelines

## Project Overview

SMS Toolbox is a native Android app (Kotlin) with three features: backing up received and sent SMS to the user's own Gmail account over IMAP and restoring them to a phone (the primary feature and landing tab), forwarding incoming SMS that match user-defined rules, and sending scheduled messages. See `Architecture.md` for the full system design.

It ships as two product flavors of the `distribution` dimension: **`play`** (Google Play — Backup & Restore only, no `SEND_SMS`, no self-update) and **`github`** (APK on GitHub releases — every feature, plus a daily in-app update check against GitHub releases). Both share the `applicationId`, signing key and `versionCode` so either can install over the other. See `Architecture.md` → "Two distributions".

The app is named "SMS Toolbox" but the `applicationId` and Kotlin package remain `com.neteinstein.smsredirect`, and the deep link remains `smsredirect://app` — changing them would create a new Play listing and cut existing users off from updates.

## Tech Stack

- **Language:** Kotlin (JVM target 17)
- **UI:** Jetpack Compose with Material 3
- **Navigation:** Navigation Compose
- **Database:** Room (with KSP annotation processing)
- **Background work:** WorkManager (expedited coroutine workers)
- **Async:** Kotlin Coroutines + Flow
- **DI:** Manual constructor injection via `AppContainer` (no framework)
- **Linting:** ktlint 1.8.0 (via `org.jlleitschuh.gradle.ktlint` plugin)
- **Testing:** JUnit 4, kotlinx-coroutines-test, Turbine
- **Email:** JavaMail Android port (`com.sun.mail:android-mail`, `javax.mail` namespace)
- **Min SDK:** 33 | **Target SDK:** 37 | **Compile SDK:** 37

## Architecture

The project follows **Clean Architecture** with three layers:

```
presentation/ → ViewModels + Compose screens
domain/       → Use cases, repository interfaces, models
data/         → Room entities, DAOs, repository implementations
```

### Rules

- **Domain models** are plain Kotlin data classes (no Android dependencies).
- **Use cases** are single-responsibility classes with an `operator fun invoke(...)`.
- **Repository interfaces** live in `domain/repository/`; implementations live in `data/repository/`.
- **Mappers** convert between Room entities and domain models; they live in `data/repository/`.
- **ViewModels** depend only on use cases and domain interfaces, never on Room or data-layer classes.
- **Compose screens** are stateless functions that receive state + event callbacks from the ViewModel.

### Dependency Injection

All wiring happens in `SmsRedirectApplication.onCreate()` through the `AppContainer` data class. When adding new dependencies:

1. Create the interface in `domain/`.
2. Create the implementation in `data/`.
3. Instantiate in `SmsRedirectApplication` and add to `AppContainer`.
4. Pass through the ViewModel factory.

Do **not** introduce Hilt, Dagger, or Koin unless explicitly requested.

## Coding Conventions

### Kotlin Style

- Follow **ktlint** rules (run `./gradlew ktlintCheck` to verify).
- Use trailing commas in multi-line parameter lists and collections.
- Prefer `data class` for models; use `sealed class`/`sealed interface` for state hierarchies.
- Use `StateFlow` (not `LiveData`) for observable state in ViewModels.
- Prefer `Flow` operators over manual coroutine collection where possible.

### Naming

- Use cases: `VerbNounUseCase` (e.g., `FindMatchingRuleUseCase`, `GetRulesUseCase`).
- ViewModels: `FeatureViewModel` (e.g., `HomeViewModel`, `RuleViewModel`).
- Screens: `FeatureScreen` composable function.
- Entities: `NounEntity` (e.g., `RuleEntity`, `AppSettingsEntity`).
- DAOs: `NounDao` (e.g., `RuleDao`).
- Repositories: `NounRepository` (interface) / `NounRepositoryImpl` (implementation).
- Fakes for testing: `FakeNounRepository`.
- Anything belonging to the SMS→Gmail feature: prefix `SmsBackup*` or `Gmail*`.

### Compose

- Annotate composable functions with `@Composable`.
- Keep screens stateless — hoist state to the ViewModel.
- Use `remember` / `rememberSaveable` only for local UI state (animations, scroll position).
- Follow Material 3 theming via `Theme.kt`.

## Testing

### Guidelines

- Unit tests live in `app/src/test/`.
- Use **Fake** implementations (not mocks) for repository interfaces. Existing fakes are in the test root package: `FakeRuleRepository`, `FakeSettingsRepository`, `FakeContactNameResolver`, `FakeForwardHistoryRepository`, `FakeScheduledSmsRepository`, `FakeScheduledSmsHistoryRepository`, `FakeScheduledSmsScheduler`, `FakeMailTransport`, `FakeSmsMessageSource`, `FakeSmsBackupStateRepository`, `FakeSmsBackupSettingsRepository`, `FakeSmsBackupScheduler`, `FakeSecretCipher`, `FakeSmsMessageWriter`, `FakeSmsRestoreScheduler`, `FakeDefaultSmsAppChecker`, `FakeAppReleaseSource`, `FakeAppUpdateRepository`, `FakeAppUpdateInstaller`.
- Tests for code that lives only in the `github` flavor source set (e.g. `GitHubReleaseParser`) go in `app/src/testGithub/`.
- Keep Android and third-party types (`Context`, `SmsManager`, `javax.mail`, WorkManager) behind a domain interface so the logic in front of it is testable. `SmsForwarder` is untested precisely because this was not done.
- Test use cases in isolation with their respective fakes.
- Test ViewModels using `kotlinx-coroutines-test` (`runTest`, `UnconfinedTestDispatcher`) and Turbine for Flow assertions.
- Keep tests focused on behavior, not implementation details.
- Run tests with `./gradlew test`.

### Test Structure

```kotlin
@Test
fun `descriptive test name using backticks`() = runTest {
    // Given
    val repository = FakeRuleRepository(initial = listOf(...))
    val useCase = SomeUseCase(repository)

    // When
    val result = useCase(...)

    // Then
    assertEquals(expected, result)
}
```

## CI / PR Checks

PR checks (`.github/workflows/pr-checks.yml`) run:

1. **ktlint** — `./gradlew ktlintCheck`
2. **Unit tests** — `./gradlew test`

Both must pass before merging. Always run these locally before pushing.

## Build & Run

```bash
# Lint check
./gradlew ktlintCheck

# Auto-format
./gradlew ktlintFormat

# Run unit tests
./gradlew test

# Build debug APKs (every feature / Play's Backup-only build)
./gradlew assembleGithubDebug
./gradlew assemblePlayDebug
```

## Common Patterns

### Adding a New Feature

1. Define the domain model in `domain/model/`.
2. Add repository interface methods in `domain/repository/`.
3. Create use case(s) in `domain/usecase/`.
4. Implement repository changes in `data/repository/` (add entity/DAO if needed).
5. Wire in `SmsRedirectApplication` → `AppContainer`.
6. Create/update ViewModel and Compose screen.
7. Write unit tests with fakes.

### Adding a Room Entity

1. Create `NounEntity` in `data/local/`.
2. Create `NounDao` in `data/local/`.
3. Register in `AppDatabase` (`@Database entities` array and abstract DAO getter).
4. Add mapper functions in `data/repository/`.
5. If schema changes, increment the DB version or use `fallbackToDestructiveMigration()` during development.

### WorkManager Tasks

- Use `CoroutineWorker` for background work.
- Use **Expedited** work requests for time-sensitive tasks (e.g., SMS forwarding).
- Pass data via `workDataOf(...)` input; keep payloads small.
- Handle retries via `Result.retry()`.

## Traps (read before touching these areas)

- **The Play build must never declare `SEND_SMS` or self-update.** Play's SMS declaration covers
  "Backup and restore" only, and Play forbids updates outside Play. `SEND_SMS` and
  `REQUEST_INSTALL_PACKAGES` live in `app/src/github/AndroidManifest.xml`, the play manifest
  removes them as a guard, and `release.yml` fails if the Play bundle's merged manifest has
  either. Gate features through `AppContainer.features` (`AppFeatures`), never by checking
  `BuildConfig.FLAVOR`; put update/install code in `app/src/github/` only.
- **`versionCode` is derived from `versionName`** in `app/build.gradle.kts`, so the Play bundle
  and GitHub APK of a release match. Do not set it by hand or from a CI run number.

- **"Backup" is an overloaded word here.** `ExportBackupUseCase` / `ImportBackupUseCase` /
  `BackupUseCasesTest` predate the Gmail feature and mean the local JSON export of rules,
  schedules and settings. The SMS→Gmail feature uses `SmsBackup*` / `Gmail*` names. Do not
  mix them.
- **Never use `android.util.Base64` in code a unit test can reach.** This module sets
  `testOptions.unitTests.isReturnDefaultValues = true`, under which every `android.jar` call
  silently returns null instead of throwing — a test would pass on a lie. Use `java.util.Base64`.
  The same applies to any other `android.*` API in otherwise-pure logic.
- **Never log, persist in plaintext, or send to analytics/Crashlytics the Gmail app password.**
  It grants full IMAP access to the user's entire mailbox. `GmailCredentials.toString()` is
  redacted on purpose. Do not add the credential columns to `ExportBackupUseCase` — its
  `buildSettingsJson()` is an explicit allowlist and that file leaves the device via a share sheet.
- **Room migrations are not optional here.** `fallbackToDestructiveMigration` does *not* rescue a
  registered-but-wrong migration: Room throws and the app crashes on open for every upgrading
  user. The migration DDL must byte-match the schema Room generates from the entity annotations.
  The v10 tables declare no SQL defaults specifically to avoid ambiguity about how Room quotes a
  default containing `$`, parentheses or an empty string. `exportSchema` is on — commit
  `app/schemas/` changes and diff them in review.
- **Subject templating must use literal `String.replace`, never `Regex.replace`** — in a Kotlin
  regex replacement `$` introduces a group reference, so `$CONTACT_NAME` would be parsed as a
  backreference.
- **JavaMail's `META-INF/javamail.*` descriptors must be *merged*, not excluded**, in the
  `packaging` block. JavaMail concatenates them to build its provider registry; get it wrong and
  `Session.getStore("imaps")` throws `NoSuchProviderException` at runtime with no build warning.
- **The backup email format is a restore contract.** The `X-SMS-*` headers in `SmsMailHeaders`
  are what `SmsMailParser` reads back. Never rename or drop one. Bump `CURRENT_FORMAT` for an
  incompatible change and keep parsing older formats, including legacy backups that have no
  headers at all. Backups already in users' mailboxes can never be rewritten.
- **The default SMS role is borrowed, not kept.** Restore needs it because only the role holder
  can write `content://sms`. Never add a code path that asks for the role outside a
  user-started restore, and keep `SmsDeliverReceiver` saving incoming SMS: while the role is
  held, nothing else will.
- **`SmsForwarder` swallows exceptions**, which is why `ForwardSmsWorker`'s `Result.retry()` is
  effectively dead for send failures. Do not copy that pattern; new workers must report failures.

## Important Notes

- The app requires sensitive permissions (SMS, notifications, internet). Never log message content in production.
- Google Play's SMS & Call Log Permissions policy gates `READ_SMS` / `RECEIVE_SMS` behind a declared core use case. The declared one is **"Backup and restore"**, which is why Backup is the first tab and the landing screen. Changes that de-emphasise backup have Play-policy consequences, not just product ones.
- Backup state (`sms_backup_state`) stores a one-way hash per message, never the body. Keep it that way — a second on-device copy of message content would change both `PrivacyPolicy.md` and the Data safety form.
- `PrivacyPolicy.md` and `store-listing/data-safety.md` are load-bearing: they are linked from the store listing and describe commitments. Any change to what leaves the device must update both.
- `SmsForwarder` is the core domain logic — keep it free of Android framework coupling (except `SmsManager`).
- Rule matching is order-dependent (first match wins). Respect `sortOrder`.
- The `defaultPrefix` and `forwardPrefix` settings affect message formatting — test formatting changes thoroughly.
- No Dependency Injection framework is used intentionally — keep it simple with manual DI.

## Agent Orchestration

This repo defines four specialized agent roles, available to both **Claude Code** (`.claude/agents/*.md`) and **GitHub Copilot** (`.github/agents/*.agent.md`). The actual role instructions live once, in `.github/agent-roles/*.md`; the platform-specific files are thin wrappers — frontmatter (name/description/tools, which necessarily differ per platform) plus one line telling the agent to go read the shared file. Edit the shared file to change what a role does; only touch the wrappers for frontmatter/tool changes.

| Role | Shared definition | Responsibility |
|---|---|---|
| **Architect** | `.github/agent-roles/architect.md` | Plans a change: which layer(s) it touches, DI/AppContainer wiring, schema/WorkManager impact, updates `Architecture.md`. Writes no code. |
| **Developer** | `.github/agent-roles/developer.md` | Implements the plan following Clean Architecture and the coding conventions above. |
| **QA** | `.github/agent-roles/qa.md` | Writes/reviews tests, runs `ktlintCheck` and `test`, checks the invariants called out in this doc (rule ordering, prefix formatting, WorkManager retries). |
| **Security Manager** | `.github/agent-roles/security-manager.md` | Reviews SMS/permission handling, logging, analytics payloads, keystore/signing config, and dependency additions against `PrivacyPolicy.md` and `store-listing/data-safety.md`. |

**Default flow:** Architect (plan) → Developer (implement) → QA (test) → Security Manager (gate, for anything touching SMS content, permissions, logging, or signing config). Any agent can hand back to an earlier one if it finds a problem outside its own scope — this isn't a strict one-way pipeline.

### Using them in Claude Code

Subagents in `.claude/agents/` are invoked by the main assistant thread via the `Agent`/Task tool, either automatically (description-based matching) or by name (e.g. "use the architect agent to plan this"). Claude subagents don't call each other directly — the top-level thread drives the handoff sequence between them based on each agent's report.

### Using them in GitHub Copilot

Agent profiles in `.github/agents/` work in both the IDE (`@architect`, `@developer`, `@qa`, `@security-manager` in Copilot Chat) and the cloud coding agent. Each profile includes the `agent` tool, so — unlike the Claude setup — these agents can delegate to each other directly mid-session (real sub-agent sessions, not just a suggestion to the user), and the runtime can also auto-route a request to the matching agent by intent. When Copilot's cloud agent opens a PR from one of these roles, it reacts to review comments and CI failures on that PR automatically (standard Copilot coding agent behavior) — no extra configuration needed for that part.
