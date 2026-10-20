# Chalkroom

Chalkroom is an Android app where you learn by teaching. You paste your study notes, and an AI student
(Mira, Pip or Juno) reads them and comes back with a few believable misconceptions. You clear each one by
explaining it until the student is convinced.

The app talks to a small Ktor server, which holds the Gemini API key and all prompts. Both run on your
machine; the app runs on an Android emulator.

## Prerequisites

- **JDK 21** on your `PATH` (`java -version` should print 21).
- **Android SDK** with an emulator image (API 26 or newer). The easiest way to get both is
  [Android Studio](https://developer.android.com/studio): install it, then create a virtual device in
  Device Manager. In SDK Manager, install the API 37 SDK platform, which the app compiles against.
- **A Gemini API key** from [Google AI Studio](https://aistudio.google.com/apikey).

All commands below run from the repository root.

## 1. Get the code

Clone this repository and `cd` into it:

```bash
git clone <this repository's URL> chalkroom
```

```bash
cd chalkroom
```

## 2. Start the server

Copy the example env file and put your key in it. `.env` is gitignored.

```bash
cp .env.example .env
```

Open `.env` and replace `your-gemini-api-key-here` with your key. (Alternatively, `export GEMINI_API_KEY=…`
in your shell; a real environment variable wins over `.env`.)

Start the server:

```bash
./gradlew :server:run
```

It is ready when the log shows `Responding at http://0.0.0.0:8080`. Leave it running and use a second
terminal for the rest. Check it:

```bash
curl http://localhost:8080/health
```

This prints `ok`.

## 3. Build and install the app on the emulator

Tell Gradle where the Android SDK is by creating `local.properties` in the repo root (gitignored). On macOS
with a default Android Studio install:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

On Linux the default is usually `$HOME/Android/Sdk`; on Windows, `C:/Users/<you>/AppData/Local/Android/Sdk` (forward slashes).
Setting `ANDROID_HOME` instead also works.

Start an emulator (from Android Studio's Device Manager, or from the command line; replace `Pixel_8` with
your device's name, listed by `emulator -list-avds`):

```bash
$HOME/Library/Android/sdk/emulator/emulator -avd Pixel_8 -port 5554
```

Once it has booted, build, install and launch the app:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug
```

```bash
$HOME/Library/Android/sdk/platform-tools/adb -e shell am start -n app.chalkroom/.MainActivity
```

Or open the project in Android Studio and run the `app` configuration on the emulator.

The debug app talks to `http://10.0.2.2:8080`, which is the emulator's address for your computer's
`localhost`, so it reaches the server from step 2 with no extra setup.

## 4. Teach a lesson

1. Tap **Teach something**, paste a paragraph of notes (150–8,000 characters) and tap **Continue**.
2. Pick a student and wait while they read your notes.
3. The student says one misconception at a time. Type an explanation and tap **Explain**. A precise
   explanation clears it; a vague one doesn't. After a few misses you can take **One last try** or
   **See the answer**.
4. When nothing is left in the student's head, the closing screen shows what stuck.

## Pointing the app at another server

The server URL is baked in at build time from `chalkroom.apiBaseUrl`. The first one found wins:

1. A Gradle property: `./gradlew :app:installDebug -Pchalkroom.apiBaseUrl=https://your-server.example`
2. A line in `local.properties`: `chalkroom.apiBaseUrl=https://your-server.example`
3. The default, `http://10.0.2.2:8080`.

Rebuild and reinstall after changing it. If you run the server on another port (`PORT=9090 ./gradlew
:server:run`), point the app at `http://10.0.2.2:9090`.

## Running the evals

The evals call the real Gemini API, so they are opt-in: plain `./gradlew test` runs only the offline
unit tests, even with a key in `.env`. To run them, add `-Pevals`. They need your key (from `.env` or
the environment) and use quota.

```bash
./gradlew :server:test -Pevals
```

With `-Pevals` but no key, the evals are skipped. To run one eval:

```bash
./gradlew :server:test -Pevals --tests '*SayRulesEval'
```

The others are `FlawShapingEval` and `JudgeConsistencyEval`. Results are written to
`server/build/eval-report.md`; `server/eval-report.md` is the last recorded run.

## Server image (optional)

`server/Dockerfile` builds the server into a JRE image, for hosting it somewhere later. It is not needed to
run locally.

```bash
docker build -f server/Dockerfile -t chalkroom-server .
```

```bash
docker run --rm -p 8080:8080 -e GEMINI_API_KEY chalkroom-server
```

## Troubleshooting

- **"… lost the thread. Your notes are still here." while the student reads.** Either the app can't reach
  the server (see "The emulator can't reach the server" below) or the key is missing or wrong. The server log shows `GEMINI_API_KEY is not set` at startup, or `AI unavailable` on each call.
  Check `.env` (no quotes or spaces needed) and restart the server.
- **`Address already in use` when the server starts.** Something else holds port 8080. Find it with
  `lsof -i :8080` and stop it, or run the server on another port (`PORT=9090 ./gradlew :server:run`) and
  point the app at it (see above).
- **The emulator can't reach the server.** Check `curl http://localhost:8080/health` on your computer
  first. In the app, the URL must be `http://10.0.2.2:<port>`, not `localhost` (inside the emulator,
  `localhost` is the emulator itself). If you changed `chalkroom.apiBaseUrl`, rebuild and reinstall.
  Plain `http` only works in the debug build.
- **`SDK location not found` when building the app.** Create `local.properties` as in step 3, or set
  `ANDROID_HOME`.

## License

MIT, see [LICENSE](LICENSE). Fonts: Lora, Nunito and Caveat, under the SIL Open Font License (`licenses/`).
