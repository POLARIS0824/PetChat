# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PetChat is an Android application built with Kotlin and Jetpack Compose that provides intelligent pet chat experiences using AI models (DeepSeek API via Alibaba DashScope). Users can chat with various virtual pets (cat, dogs, hamster) with unique personalities.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build and install debug APK directly to connected device
./gradlew installDebug

# Build release APK (requires keystore.properties)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.example.chat.ExampleUnitTest"

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Run Android lint checks
./gradlew lintDebug
```

## Android CLI

This project can be managed with the `android` CLI tool for common development tasks. Typical commands used in this repo:

```bash
# Deploy the debug APK to a connected device/emulator
android run --apks=app/build/outputs/apk/debug/app-debug.apk

# List and manage emulators
android emulator list
android emulator start <avd-name>

# Capture a screenshot from the device
android screen capture -o screenshot.png

# Inspect the current UI layout tree (JSON output)
android layout -p

# Search Android documentation for APIs/concepts
android docs search <keywords>

# Check environment info (SDK location, etc.)
android info
```

For full CLI documentation, run `android help` or `android help <command>`.

## Architecture

### MVVM Pattern with Repository Layer

```
UI Layer (Compose)
    ↓
ViewModel Layer (PetChatViewModel, CardsViewModel, NotesViewModel, SocialViewModel)
    ↓
Repository Layer (ChatRepository - @Singleton via Hilt)
    ↓
Data Layer (Room Database via ChatDao) & Network Layer (ChatApiService via OkHttp)
```

### Key Components

- **PetChatViewModel** (`ui/chat/PetChatViewModel.kt`): Manages UI state (`ChatUiState` sealed interface: Loading/Ready), handles streaming responses via `StreamResponseListener` callbacks, coordinates chat history and sessions. Uses `viewModelScope` for coroutines.
- **CardsViewModel** (`ui/cards/CardsViewModel.kt`): Manages pet card collection.
- **NotesViewModel** (`ui/notes/NotesViewModel.kt`): CRUD operations for sticky notes, filtered by pet type.
- **SocialViewModel** (`ui/social/SocialViewModel.kt`): Manages state for the social feed screen independently.
- **ChatRepository** (`data/repository/ChatRepository.kt`): `@Singleton` injected via Hilt. Manages all data operations, API calls, prompt enhancement with user profiling, and local database persistence. Contains pet personality prompts and streaming API logic.
- **ChatDatabase** (`data/ChatDatabase.kt`): Room database with `fallbackToDestructiveMigration()`.

### Dependency Injection (Hilt)

- `PetChatApplication` is `@HiltAndroidApp`; `MainActivity` is `@AndroidEntryPoint`
- `DatabaseModule` (`di/DatabaseModule.kt`) provides `ChatDatabase`, `ChatDao`, and `ChatApiService` as singletons
- `ChatRepository` uses `@Inject constructor` with `@Singleton` scope
- `PetGreetingWorker` is `@HiltWorker`, injected via `HiltWorkerFactory`

### Data Flow

1. **Chat Flow**: User input → ViewModel → Repository API call → Streaming response via `StreamResponseListener` → UI update → Database save
2. **Analysis Flow**: Accumulates 10 unprocessed messages → AI analysis via `analyzeChats()` → Saves `ChatAnalysisEntity` → Used to enhance prompts
3. **Greeting Flow**: WorkManager triggers daily → `PetGreetingWorker` generates personalized greeting → System notification

### Streaming API

The app uses Server-Sent Events (SSE) for streaming AI responses:
- `ChatRepository` calls `ChatApiService` which handles SSE parsing
- `StreamResponseListener` callbacks: `onContent()`, `onComplete()`, `onError()`
- Response parsing uses OkHttp `BufferedSource` line reading for streaming, `suspendCancellableCoroutine` for non-streaming

### Pet Types & Personalities

Four pet types with distinct system prompts in `PromptConfig` (`data/repository/PromptConfig.kt`):
- **CAT** (布丁): Gold Shaded Persian - tsundere personality
- **DOG** (大白): Samoyed - energetic and cheerful
- **HAMSTER** (团绒): Silver Shaded Persian - cute and clingy
- **SHIBA** (豆豆): Shiba Inu - stubborn and quiet

Chat history is organized by `petType`, not by sessions. Each pet type maintains its own conversation history.

### Database Schema

- `ChatEntity`: Message content, sender, petType, sessionId, importance flags
- `ChatAnalysisEntity`: Summary, preferences, patterns for user profiling
- `NoteEntity`: User notes
- Database version: 4 (uses destructive migration on version change)

### UI Structure

The app uses a single-activity architecture with bottom navigation and a modal drawer:

**Screens** (navigated using NavKey routes in `ui/navigation/Routes.kt`):
- **Chat** (`ui/chat/ChatScreen.kt` + `ChatComponents.kt`): Main chat interface with pet selector dropdown
- **Cards** (`ui/cards/PetCards.kt`): Pet card collection with draggable interactions
- **Notes** (`ui/notes/NotesScreen.kt`): Sticky notes screen
- **Social** (`ui/social/SocialScreen.kt`): Social feed with `SocialViewModel`
- **SessionList** (`ui/session/SessionListScreen.kt`): Chat session history list

**Navigation**:
- Driven by Jetpack Navigation 3 (`androidx.navigation3.runtime.NavKey`) with `TopLevelBackStack` and `NavDisplay` hosting routes (`ChatRoute`, `CardsRoute`, `NotesRoute`, etc.)
- Bottom navigation bar covers Chat, Cards, Notes, Social (supports Navigation Rail on non-compact screens)
- Modal drawer (triggered from Chat screen) provides navigation to SessionList, user profile preferences, and settings
- Drawer content lives in `ui/drawer/DrawerComponents.kt`
- Chat-specific composables (bubbles, input) live in `ui/chat/ChatComponents.kt`

### Session Management

- `PetChatViewModel` exposes `allSessions: StateFlow<List<SessionInfo>>` for the session list UI
- Sessions are identified by `sessionId`; chat history is loaded per session
- The default session ID is `"default"`

## API Configuration

- Default Base URL: `https://api.ppio.com/openai`
- Default Model: `deepseek/deepseek-v4-flash`
- The API settings (Base URL, API Key, and Model) are configured dynamically inside the app's Settings Screen, backed by Jetpack DataStore Preferences.
- Timeout: 60s connect, 60s read, 30s write

## Important Notes

- Uses `kotlinx.serialization` (not Gson) for JSON — see `proguard-rules.pro` for keep rules
- Repository is a Hilt `@Singleton` — inject via constructor, do not instantiate manually
- Database uses `fallbackToDestructiveMigration()` - version changes wipe data
- Streaming responses update UI in real-time via `StreamResponseListener`
- Message history limited to last 3 messages for context (`contextMessageLimit = 3`)
- Unprocessed messages trigger analysis when count >= 10
- Conversation summary triggers when unprocessed count > 20
- `local.properties` and `keystore.properties` must not be committed to version control
- Sign release builds with `keystore.properties` file (optional for debug)

## Available Claude Skills

When working on this project, the following skills can be invoked via slash commands:

- **`/android-cli`** — Manage devices, emulators, deploy APKs, capture screenshots, inspect UI layouts (`android layout`), and search Android docs.
- **`/navigation-3`** — Migrate from Navigation 2 to Navigation 3, implement type-safe destinations, deep links, multiple backstacks, scenes (dialogs, bottom sheets, list-detail), and conditional navigation.
- **`/edge-to-edge`** — Migrate the app to adaptive edge-to-edge support and fix system bar / IME inset issues.
