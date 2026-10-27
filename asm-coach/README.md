# ASM Coach

An Android app (Kotlin, Jetpack Compose) that prepares a returning candidate for **Assistant Site
Manager** roles in UK construction. It is built around a 30-minute daily routine and runs entirely
on free tiers.

| Minutes | Tab | What it does |
|---|---|---|
| 0–5 | **Jobs** | Searches live ASM listings in the North West (Reed's official API, or Serper/Firecrawl web search across Reed, Indeed, Totaljobs and CV-Library), scores fit against the master profile, and writes a *Why You Win* brief plus a 30-60-90 day site management plan. |
| 5–15 | **Prep** | Four masterclasses (Narrative Pitch, Digital Site Tools, Technical Trade QA, Crisis Management) in accordion cards with read-aloud boxes, plus an AI coach that rewrites each lesson in your own words. |
| 15–30 | **Roleplay** | A live voice interview with three fictional panel personas across four modes, including crisis simulations. It ends with a readiness score out of 100, a STAR critique and *Better Answers You Should Have Used* scripts. |

## Free stack

| Need | Used | Cost |
|---|---|---|
| Speech-to-text | Android `SpeechRecognizer` (en-GB, continuous mode) | Free, on-device |
| Text-to-speech | Android `TextToSpeech` (en-GB voices, a distinct pitch and rate per persona) | Free, on-device |
| AI agents | Gemini API via a Google AI Studio key | Free tier |
| Job search | Reed Jobseeker API (recommended), Serper.dev (2,500 free queries) or Firecrawl (free credits) | Free |
| Persistence | On-device DataStore, plus optional Firestore sync with anonymous auth | Free (Spark plan) |

> **Model note:** Google has retired Gemini 1.5 Flash, so the default model is `gemini-2.5-flash`,
> its free-tier successor. You can change the model in Settings.

## The three agents

1. **Strategy agent** (`data/agent/StrategyAgent.kt`): scores the listing against the profile and writes the strengths, gaps, *Why You Win* brief, elevator pitch, 30-60-90 plan, questions to ask and keywords.
2. **Coach agent** (`data/agent/CoachAgent.kt`): turns a masterclass lesson into a speakable talking point grounded in the candidate's real history.
3. **Interview agent** (`data/agent/InterviewAgent.kt`): plays the panel turn by turn (probing weak answers and escalating crises), then evaluates the full transcript.

Every prompt includes the master profile (`data/model/CandidateProfile.kt`) and an instruction not to invent experience.

## Setup

1. Open `asm-coach/` in Android Studio (Ladybug or newer), or build with `./gradlew :app:assembleDebug`.
2. Add your keys. Either paste them into the app's **Settings** screen at runtime, or copy
   `local.properties.example` to `local.properties` and fill it in before building.
   - Gemini: https://aistudio.google.com/apikey
   - Reed (live jobs): https://www.reed.co.uk/developers/jobseeker
   - Serper: https://serper.dev
   - Firecrawl: https://firecrawl.dev
3. *(Optional)* Firestore sync:
   - Create a Firebase project and add an Android app with package `uk.co.asmcoach`.
   - Download `google-services.json` into `asm-coach/app/`.
   - Enable **Authentication → Anonymous** and create a Firestore database.
   - Deploy `firestore.rules`.
   - The Gradle build detects the file and applies the Google Services plugin automatically.

Without any keys the app still works: sample listings, all masterclass content and read-aloud.

CI (`.github/workflows/asm-coach-android.yml`) runs the unit tests and uploads a debug APK as a
build artifact.

## Project layout

```
app/src/main/java/uk/co/asmcoach/
├── AsmCoachApp.kt, MainActivity.kt, AppContainer.kt   # app entry + manual DI
├── data/
│   ├── model/        # profile, jobs, brief, interview, evaluation, masterclass models
│   ├── remote/       # OkHttp REST clients: Gemini, Serper, Firecrawl
│   ├── agent/        # Strategy, Coach and Interview agents (prompts + schemas)
│   ├── repo/         # JobRepository (search, scrape, sample listings)
│   ├── persist/      # SessionRepository (DataStore + Firestore sync)
│   ├── settings/     # DataStore-backed keys, model and profile
│   └── content/      # Offline masterclass curriculum
├── voice/            # SpeechRecognizerManager, TextToSpeechManager
└── ui/
    ├── theme/        # Stone/obsidian/slate/amber tokens, iOS-style type ramp
    ├── components/   # Tonal cards, segmented control, accordion, read-aloud box, score ring
    ├── navigation/   # Bottom tab bar + routes
    └── jobs/ strategy/ prep/ roleplay/ settings/
```

Masterclass technical content is guidance for interview preparation. On a real project, always
follow the specification, the manufacturer's tested details and your employer's procedures.
