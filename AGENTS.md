# Repository Guidelines

## Project Structure & Module Organization
- Root is a single-module Android app (`:app`) managed by Gradle Kotlin DSL.
- Main source: `app/src/main/java/com/example/chat`.
- UI and feature folders: `ui/`, `ui/social/`, `viewmodel/`, `data/`, `model/`, `service/`, `util/`.
- Resources and manifest: `app/src/main/res` and `app/src/main/AndroidManifest.xml`.
- Unit tests live in `app/src/test/java`.
- Instrumented tests live in `app/src/androidTest/java`.
- Build outputs (`app/build/`, `app/debug/`, `app/release/`) are generated artifacts; do not edit manually.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repository root:
- `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`): build debug APK.
- `./gradlew testDebugUnitTest`: run local JVM unit tests.
- `./gradlew connectedDebugAndroidTest`: run instrumented tests on a device/emulator.
- `./gradlew lintDebug`: run Android lint checks.
- `./gradlew clean`: clear build outputs when caches are stale.

## Coding Style & Naming Conventions
- Language: Kotlin (JDK 17 target).
- Follow standard Kotlin style: 4-space indentation, clear nullability, immutable `val` by default.
- Use `PascalCase` for classes/composables (`PetChatRepository`, `NotesScreen`).
- Use `camelCase` for functions/variables (`getPetResponseStreaming`).
- Package paths should mirror features (`com.example.chat.ui.social`).
- Keep composables focused; move data/storage logic to `Repository`/`ViewModel` layers.

## Testing Guidelines
- Frameworks in use: JUnit4 (`app/src/test`) and AndroidX test/Compose test (`app/src/androidTest`).
- Test file naming: `<ClassName>Test.kt` for unit tests, `<Feature>InstrumentedTest.kt` for device tests.
- Add or update tests for behavior changes in repository, database DAO, and viewmodel logic.

## Commit & Pull Request Guidelines
- Current history uses short, imperative commit subjects (examples: `release`, `animate`, `animation fix`).
- Prefer: `<scope>: <imperative summary>` (example: `chat: fix session ordering`).
- Keep commits focused; avoid mixing refactors with feature fixes.
- PRs should include what changed and why.
- Add screenshots/GIFs for UI changes.
- Include test evidence (command + result) and linked issue/task when available.

## Security & Configuration Tips
- Keep secrets out of source control. API keys and signing credentials must come from local config (for example `local.properties` / `keystore.properties`) and never be hardcoded.
- Do not commit machine-specific files (`local.properties`, IDE state) or generated APK artifacts.
