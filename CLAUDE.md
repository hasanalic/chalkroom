# Chalkroom — Project Rules

Android app (learn by teaching AI students) + Ktor server + shared DTOs. Plan lives in `devpost/` (`scope.md`, `prd.md`, `spec.md`, `checklist.md`). Build progress state is `devpost/checklist.md`.

## Ground rules (whole build)
- **Emulator only.** On-device checks run on an Android emulator, never a physical phone. Always target it explicitly: `adb -e …` or `ANDROID_SERIAL=emulator-5554`.
- **Never ask for API keys in chat.** The user sets `GEMINI_API_KEY` in their shell or a gitignored `.env`. Never print, log, or commit it.
- **.gitignore** must cover `.env`, `.env.*` (except `.env.example`), `/devpost/learner-profile.md`, `local.properties`, `.gradle/`, `build/`, `.idea/`, `.kotlin/`, `*.jks`, `*.keystore`.
- **License:** MIT, `LICENSE` at the repo root.
- **Commit after each verified slice.** Review the staged diff for secrets and private context first; stage files explicitly, no blanket `git add -A`.
- **Build order:** `:shared` DTOs → server (Gemini client, generator, judge, routes) → evals → Android.
- **Judges try it without their own key:** a debug APK in GitHub Releases pointing at the deployed server, plus README steps for running everything locally.
- **Build mode:** fast.

## Conventions
- App: Kotlin, Jetpack Compose, MVI per screen (Contract with UIState/UIEvent/UIEffect, ViewModel with single `onEvent`, Screen + stateless ScreenContent with preview), Koin, type-safe Navigation Compose, Ktor client.
- Server: Ktor (Netty), kotlinx.serialization, Google Gen AI Java SDK with structured output. The server is the only holder of the key and all prompts.
- Evals: JUnit 5 in `:server`, opt-in with `-Pevals` (plain `test` stays offline), skipped when `GEMINI_API_KEY` is unset.
