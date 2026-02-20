# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PetChat is an Android application built with Kotlin and Jetpack Compose that provides intelligent pet chat experiences using AI models (DeepSeek API via Alibaba DashScope). Users can chat with various virtual pets (cat, dogs, hamster) with unique personalities.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires keystore.properties)
./gradlew assembleRelease

# Run tests
./gradlew test
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Architecture

### MVVM Pattern with Repository Layer

```
UI Layer (Compose)
    ↓
ViewModel Layer (PetChatViewModel)
    ↓
Repository Layer (PetChatRepository - Singleton)
    ↓
Data Layer (Room Database) & Network Layer (OkHttp)
```

### Key Components

- **PetChatViewModel**: Manages UI state, handles user interactions, coordinates chat history. Uses `viewModelScope` for coroutines.
- **PetChatRepository**: Singleton instance managing all data operations, API calls, and local database persistence. Contains pet personality prompts and streaming API logic.
- **ChatDatabase**: Room database with single-instance pattern using `fallbackToDestructiveMigration()`.

### Data Flow

1. **Chat Flow**: User input → ViewModel → Repository API call → Streaming response via `StreamResponseListener` → UI update → Database save
2. **Analysis Flow**: Accumulates 10 unprocessed messages → AI analysis via `analyzeChats()` → Saves `ChatAnalysisEntity` → Used to enhance prompts
3. **Greeting Flow**: WorkManager triggers daily → `PetGreetingWorker` generates personalized greeting → System notification

### Streaming API

The app uses Server-Sent Events (SSE) for streaming AI responses:
- `PetChatRepository.makeStreamingApiRequest()` handles SSE parsing
- `StreamResponseListener` callbacks: `onContent()`, `onComplete()`, `onError()`
- Response parsing uses OkHttp `BufferedSource`

### Pet Types & Personalities

Four pet types with distinct system prompts in `PetChatRepository`:
- **CAT** (布丁): Gold Shaded Persian - tsundere personality
- **DOG** (大白): Samoyed - energetic and cheerful
- **HAMSTER** (团绒): Silver Shaded Persian - cute and clingy
- **DOG2** (豆豆): Shiba Inu - stubborn and quiet

Chat history is organized by `petType`, not by sessions. Each pet type maintains its own conversation history.

### Database Schema

- `ChatEntity`: Message content, sender, petType, sessionId, importance flags
- `ChatAnalysisEntity`: Summary, preferences, patterns for user profiling
- `NoteEntity`: User notes
- Database version: 4 (uses destructive migration on version change)

### UI Navigation

Bottom navigation with 4 screens:
- Chat (main interface with pet selector dropdown)
- Cards (pet card collection)
- Notes (sticky notes)
- Social (social feed)

Sidebar drawer provides additional navigation to session list and settings.

## API Configuration

- Base URL: `https://dashscope.aliyuncs.com/compatible-mode/v1`
- Model: `deepseek-v3`
- API key is hardcoded in `PetChatRepository.kt:50`
- Timeout: 60s connect, 60s read, 30s write

## Important Notes

- Repository is a singleton - use `PetChatRepository.getInstance(chatDao)`
- Database uses `fallbackToDestructiveMigration()` - version changes wipe data
- Streaming responses update UI in real-time via `StreamResponseListener`
- Message history limited to last 3 messages for context (`contextMessageLimit = 3`)
- Unprocessed messages trigger analysis when count >= 10
- Conversation summary triggers when unprocessed count > 20
- Sign release builds with `keystore.properties` file (optional for debug)
