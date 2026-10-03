# NewsFlow English — Native Android (Kotlin + Jetpack Compose)

Learn English through real US news videos. Watch, listen, repeat, and improve.

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Min SDK:** 29 (Android 10) / **Target SDK:** 36 (Android 16)
- **Build:** Gradle 8.9, AGP 8.7.0, Kotlin 2.0.20

## Open in Android Studio
1. `File → Open` → select the `newsflow_android` folder
2. Wait for Gradle sync to complete
3. Select a device/emulator
4. Click Run ▶

## Project Structure
```
newsflow_android/
├── app/
│   ├── build.gradle.kts          # targetSdk=36, minSdk=29
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml   # INTERNET + RECORD_AUDIO permissions
│       ├── java/com/newsflow/app/
│       │   ├── MainActivity.kt           # Entry point
│       │   ├── data/
│       │   │   ├── Models.kt              # NewsItem, NewsSegment, WordEntry
│       │   │   ├── MockData.kt            # MVP demo data + local dictionary
│       │   │   ├── TranslationService.kt  # 3-tier: local dict → MyMemory API
│       │   │   ├── SpeechService.kt       # SpeechRecognizer + TextToSpeech
│       │   │   └── SettingsService.kt     # SharedPreferences
│       │   └── ui/
│       │       ├── AppRoot.kt             # Bottom nav + navigation
│       │       ├── theme/                 # Material 3 colors
│       │       └── screens/
│       │           ├── NewsListScreen.kt  # Grouped by source (CBS/VOA/PBS/ABC)
│       │           ├── PlayerScreen.kt    # YouTube + segments + practice
│       │           └── SettingsScreen.kt  # Language + speed + about
│       └── res/
│           ├── values/strings.xml
│           ├── values/themes.xml
│           ├── values-night/themes.xml
│           └── xml/network_security_config.xml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

## Core Learning Loop
1. Pick a news video from the list
2. Watch a segment (5-7 seconds)
3. Read the transcript (tap any word for translation)
4. Practice: Type what you heard OR Speak it back
5. Get instant score (word match %)
6. Move to next segment

## All APIs are Free
- YouTube IFrame embed: free
- MyMemory Translation: 5000 words/day, no key
- Android SpeechRecognizer: system built-in
- Android TextToSpeech: system built-in

## Build for Google Play
```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

## Package ID
`com.newsflow.app`
