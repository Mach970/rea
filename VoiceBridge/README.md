# Voice Bridge for Android 14
Personal foreground prototype. Speak inside the bridge to send one question to the Gemini API. The chosen Markdown file holds the latest completed exchange. Each successful save replaces its contents. Choose a dedicated new file.

## Build and run
1. Use Android Studio with Android SDK 35, JDK 17 and Gradle 8.9. With Gradle 8.9 installed, run `gradle wrapper --gradle-version 8.9` from the extracted VoiceBridge folder. Open that folder in Android Studio and sync.
2. Enable USB debugging on your phone, connect it, select the phone and press Run. To build an installable debug APK use `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`). Output is app/build/outputs/apk/debug/app-debug.apk.
3. Enter your own Gemini API key directly in the app. It stays in memory for the session. Use a text-generation model ID available to your Google AI Studio project. The prefilled model is editable. API quota and charges depend on your project.
4. Choose a new handover file in Downloads. Tap Speak and ask Gemini and say “Please reply with the words bridge test complete.” After Android returns the transcript, the app sends it immediately. For manual review, type the question and use Send typed question.
5. Listen for the reply and open the Markdown file. Verify it contains the question and reply under one ID. Run a second test to check that the same file now contains the new exchange.

Voice input requires an available Android speech recognition activity. Speech output requires a working text-to-speech engine. Keep the bridge open during the request. Each request is independent. Integration with live consumer chats and unattended background operation are outside this first test.

[Gemini API reference](https://ai.google.dev/api/generate-content)
