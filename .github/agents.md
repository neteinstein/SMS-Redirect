# Agents Guidelines

## Project Overview

SMS Redirect is a native Android app (Kotlin) that intercepts incoming SMS messages matching user-defined rules and forwards them to a configured target phone number via SMS. See `Architecture.md` for the full system design.

## Tech Stack

- **Language:** Kotlin (JVM target 17)
- **UI:** Jetpack Compose with Material 3
- **Navigation:** Navigation Compose
- **Database:** Room (with kapt annotation processing)
- **Background work:** WorkManager (expedited coroutine workers)
- **Async:** Kotlin Coroutines + Flow
- **DI:** Manual constructor injection via `AppContainer` (no framework)
- **Linting:** ktlint 1.3.1 (via `org.jlleitschuh.gradle.ktlint` plugin)
- **Testing:** JUnit 4, kotlinx-coroutines-test, Turbine
- **Min/Target SDK:** 35 | **Compile SDK:** 35

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

### Compose

- Annotate composable functions with `@Composable`.
- Keep screens stateless — hoist state to the ViewModel.
- Use `remember` / `rememberSaveable` only for local UI state (animations, scroll position).
- Follow Material 3 theming via `Theme.kt`.

## Testing

### Guidelines

- Unit tests live in `app/src/test/`.
- Use **Fake** implementations (not mocks) for repository interfaces. Existing fakes are in the test root package.
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

# Build debug APK
./gradlew assembleDebug
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

## Important Notes

- The app requires sensitive permissions (SMS, notifications). Never log message content in production.
- `SmsForwarder` is the core domain logic — keep it free of Android framework coupling (except `SmsManager`).
- Rule matching is order-dependent (first match wins). Respect `sortOrder`.
- The `defaultPrefix` and `forwardPrefix` settings affect message formatting — test formatting changes thoroughly.
- No Dependency Injection framework is used intentionally — keep it simple with manual DI.
