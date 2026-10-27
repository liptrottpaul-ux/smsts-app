# ASM Coach

An Android app (Kotlin, Jetpack Compose) that prepares a returning candidate for **Assistant Site
Manager** roles in UK construction. It is built around a 30-minute daily routine and runs entirely
on free tiers.

The app is built around one flow: **find a real role → prepare for that role → interview for that role.**

| Minutes | Tab | What it does |
|---|---|---|
| 0–5 | **Jobs** | Searches live ASM vacancies in the North West (Serper, Firecrawl or Reed). Tapping a role *prepares* it once: the full listing is read, then a strategy brief (fit score, *Why You Win*, 30-60-90 plan) and a **role pack** are generated in parallel and saved on the device. |
| 5–15 | **Prep** | Opens on the selected role: what the employer needs, prep topics drawn from the listing, and the questions this panel is likely to ask, each with what they're looking for and a model answer to listen to. Generic core-skills masterclasses sit below. |
| 15–30 | **Roleplay** | A voice interview for that role, with the role pack's panel (job titles that fit the employer), its question bank and its project-specific crisis scenarios. Questions are pre-written, so each one starts instantly. Gemini is called once at the end for the readiness score out of 100, the STAR critique and *Better Answers You Should Have Used*. |

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

## The agents

1. **Strategy agent** (`data/agent/StrategyAgent.kt`): scores the listing against the profile and writes the strengths, gaps, *Why You Win* brief, elevator pitch, 30-60-90 plan, questions to ask and keywords.
2. **Role pack agent** (`data/agent/RolePackAgent.kt`): writes the role-specific panel, prep topics, question bank (with what each question is looking for and a model answer) and crisis scenarios. Stored by `data/repo/RoleRepository.kt`.
3. **Interview agent** (`data/agent/InterviewAgent.kt`): evaluates the transcript against what the panel was looking for. Without a prepared role, it also plays a general panel turn by turn.
4. **Coach agent** (`data/agent/CoachAgent.kt`): personalises the generic core-skills lessons.

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

## Realism: research behind the interview

- **Question bank** (`data/content/QuestionBank.kt`): 80+ questions across motivation, H&S, quality,
  programme, people, commercial, technical, digital, customer and live scenarios. Each has what
  a strong answer contains and the follow-ups real panels use. Combined with each role's 16
  generated questions, interviews rotate least-asked questions first, so repeat sessions don't
  churn the same few.
- **Reactive panel:** after every answer the interviewer probes, challenges or moves on (up to 2
  follow-ups per question). A quick Flash-Lite call decides within 4 seconds while a spoken
  acknowledgement covers the gap. If the AI is slow, local rules probe thin answers the way panels
  do: "give me a real example", "what did *you* do?", "what was the result in numbers?".
- **Scoring** mirrors competency-interview practice: 1–5 per answer, ownership of actions and
  quantified results weigh most, and red flags (unsafe statements, wrong law, blame) are called out.
  Every answer gets a better-answer script; the evaluation also estimates the **chance of an offer**
  against a typical shortlist, with reasons and what would raise it.
- **Prep readiness** (Prep tab) = practised answers scored by the AI (50%), latest mock interview
  (20%), prep topics reviewed (15%) and a preparation checklist (15%).

Sources used to shape the content: UK interview guidance from
[Indeed UK](https://uk.indeed.com/career-advice/interviewing/site-manager-interview-questions),
[Randstad UK](https://www.randstad.co.uk/career-advice/interview-tips/interview-question-tips-site-manager-jobs/) and
[Workbred](https://workbred.co.uk/career/assistant-site-manager?page=interview); candidate reports for
[Barratt Redrow](https://www.glassdoor.co.uk/Interview/Barratt-Redrow-Bristol-Interview-Questions-EI_IE10844.0,14_IL.15,22_IC3280589.htm),
[Bellway](https://www.glassdoor.com/Interview/Bellway-Interview-Questions-E10116.htm),
[Morgan Sindall](https://www.glassdoor.co.uk/Interview/Morgan-Sindall-Group-Interview-Questions-E12356.htm) and
[Willmott Dixon](https://www.glassdoor.com/Interview/Willmott-Dixon-Management-Trainee-Interview-Questions-EI_IE36702.0,14_KO15,33.htm);
competency scoring guidance ([Shine](https://www.shineinterview.com/competency-based-interview-scoring/),
[build-construct](https://build-construct.com/practical-guide/building-structured-interview-process-home-building-leadership-hires/));
[NHBC Construction Quality Reviews](https://www.nhbc.co.uk/services/construction-quality/reviews) and key-stage inspection guides;
[excavation and temporary works guidance](https://humanfocus.co.uk/blog/excavation-safety-temporary-works-inspections-and-service-interfaces-that-prevent-collapse/);
2026 North West ASM adverts on [Find a Job](https://findajob.dwp.gov.uk/details/18008731).
Always check regulatory details against HSE guidance (e.g. CDM 2015 L153).
