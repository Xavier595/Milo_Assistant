# Milo Assistant

<p align="center">
  <strong>A local-first Android voice assistant combining on-device AI, Android actions and reliable online information.</strong>
</p>

<p align="center">
  Designed primarily for Spanish voice interaction.
</p>

---

## About Milo
**Milo** is an experimental personal voice assistant for Android.

The goal of the project is to turn an Android phone into a dedicated assistant capable of combining:
- Voice interaction.
- Android commands.
- Local conversational AI.
- Factual information from Wikipedia.
- Current weather information.
- Current news.
- Text-to-Speech responses.

Milo follows a hybrid architecture: the local language model is mainly used for conversation, while Android actions and external information are handled by dedicated components.
This prevents the language model from having unrestricted control over the phone or being used as the source of truth for current information.

---

## Screenshots

### Main interface

<!--
IMAGE: docs/images/milo-home.png

Milo on the main screen while idle.
-->

<p align="center">
  <img src="docs/images/milo-home.png" width="300" alt="Milo main interface">
</p>

### Local conversation

<!--
IMAGE: docs/images/milo-conversation.png

Recommended example:
"Milo quién eres"
-->

<p align="center">
  <img src="docs/images/milo-conversation.png" width="300" alt="Milo local conversation">
</p>

### Wikipedia information

<!--
IMAGE: docs/images/milo-wikipedia.png

Recommended example:
"Milo dónde está Argelia"

The screenshot should show:
"Fuente: Wikipedia — Argelia"
-->

<p align="center">
  <img src="docs/images/milo-wikipedia.png" width="300" alt="Milo Wikipedia lookup">
</p>

### Current weather

<!--
IMAGE: docs/images/milo-weather.png

Recommended example:
"Milo qué tiempo hace en Madrid"

Show temperature, weather condition, humidity,
precipitation, wind and Open-Meteo attribution.
-->

<p align="center">
  <img src="docs/images/milo-weather.png" width="300" alt="Milo weather information">
</p>

### Current news

<!--
IMAGE: docs/images/milo-news.png

Recommended example:
"Milo últimas noticias"

Show several headlines and their news sources.
-->

<p align="center">
  <img src="docs/images/milo-news.png" width="300" alt="Milo current news">
</p>

---

## Features

### Voice interaction

Milo uses Android speech recognition and listens for the activation word:

```text
Milo
```

After detecting the activation word, the following speech is captured as the user's command.

Examples:

```text
Milo qué tiempo hace en Madrid
Milo llama a Javier
```

Speech recognition is temporarily stopped while Milo is processing or speaking to avoid conflicts with Text-to-Speech.

---

### Text-to-Speech

Milo uses Android TextToSpeech to speak its responses.

The interface also includes a mouth animation while Milo is speaking.

---

### Android commands

Some actions are implemented directly with Android code instead of using the language model.

Current functionality includes:
- Current time.
- Greetings.
- Opening YouTube.
- Searching saved contacts.
- Calling saved contacts.
- Voice confirmation before placing a call.

Sensitive Android actions remain deterministic and are not directly controlled by the language model.

---

## Local conversational AI

Milo currently uses:

```text
Qwen2.5-0.5B-Instruct
```

through **MediaPipe LLM Inference**.

The model runs directly on the Android device and is mainly used for casual conversation.

Examples:

```text
Milo quién eres
Milo inventa una historia corta
```

The model is instructed to:

- Identify itself as Milo.
- Respond mainly in Spanish.
- Keep normal responses relatively short.
- Avoid claiming that it executed Android actions.
- Admit uncertainty when appropriate.

Because the current model is small, Milo does not rely on it for current weather, news or factual Wikipedia information.

---

## Reliable information

### Wikipedia

General factual questions can be routed to **Spanish Wikipedia**.

Examples:

```text
Milo quién fue Albert Einstein
Milo dónde está Argelia
```

Milo attempts to:
1. Extract the relevant subject from the question.
2. Find the appropriate Wikipedia article.
3. Retrieve a short introduction.
4. Display the information directly.
5. Show the selected article as the source.

Example:

```text
Fuente: Wikipedia — Argelia
```

Wikipedia answers are kept separate from the local language model to reduce the risk of adding invented information.

---

### Current weather

Current weather information is retrieved using **Open-Meteo**.

Examples:

```text
Milo qué tiempo hace en Madrid
Milo qué tiempo hace hoy en Granada
Milo cómo está el tiempo en Londres
```

Weather responses can include:

- Temperature.
- Apparent temperature.
- Weather condition.
- Humidity.
- Precipitation.
- Wind speed.

Weather values come directly from the service instead of being guessed by the local language model.

---

### Current news

Milo retrieves recent news using **Google News RSS**.

Examples:

```text
Milo últimas noticias
Milo noticias de hoy
Milo noticias sobre inteligencia artificial
```

Milo displays retrieved headlines together with their news sources.
The local language model does not generate additional information about the retrieved articles.

---

## Architecture

Milo routes each request depending on its purpose:

```text
                     User voice
                         │
                         ▼
                 Speech recognition
                         │
                         ▼
                   Command routing
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
 Android actions   Information tools   Conversation
        │                │                │
        ▼                ▼                ▼
  Android code     External sources    Local Qwen
```

More specifically:

```text
Android action
    → Deterministic Android code

Factual question
    → Spanish Wikipedia

Current weather
    → Open-Meteo

Current news
    → Google News RSS

Casual conversation
    → Local Qwen model
```

This separation allows the language model to focus on conversation while device actions and factual information remain controlled.

---

## Example routing

| Request | Milo uses |
|---|---|
| `Milo abre YouTube` | Android code |
| `Milo llama a Javier` | Contacts + Android call flow |
| `Milo dónde está Argelia` | Wikipedia |
| `Milo qué tiempo hace en Madrid` | Open-Meteo |
| `Milo últimas noticias` | Google News RSS |
| `Milo quén eres` | Local Qwen model |

---

## Technology

Milo currently uses:

- Kotlin
- Android SDK
- Jetpack Compose
- Android SpeechRecognizer
- Android TextToSpeech
- Kotlin Coroutines
- MediaPipe LLM Inference
- Qwen2.5-0.5B-Instruct
- Android Contacts API
- Spanish Wikipedia
- Open-Meteo
- Google News RSS

---

## Project structure

```text
app/src/main/java/com/example/milo_assistant/

├── MainActivity.kt
├── ai/
│   └── LocalConversationalAi.kt
├── knowledge/
│   ├── GoogleNewsClient.kt
│   ├── OpenMeteoWeatherClient.kt
│   └── WikipediaKnowledgeClient.kt
└── ui/
```

---

## Current limitations

Milo is still an experimental project.

### Local conversational model

The current `Qwen2.5-0.5B-Instruct` model was selected because of the memory limitations of the current development device.

Its main limitations are:
- Limited reasoning.
- Limited general knowledge.
- Occasional hallucinations.
- Limited contextual understanding.
- Difficulty with complex questions.

A more powerful device should allow Milo to use a significantly better local model.

### Wikipedia understanding

Wikipedia lookup is not perfect.

Some natural-language questions can still cause Milo to select a related but incorrect article.
Improving query understanding and article selection is one of the main planned improvements.

### Speech recognition

Android SpeechRecognizer behavior can vary between devices and manufacturers.

### Internet connection

Wikipedia, current weather and current news require Internet access.
Local conversation and deterministic Android commands do not depend on a paid cloud AI service.

---

# Roadmap

## More powerful device and conversational model

A major future improvement is moving Milo to a more capable Android device with:
- More RAM.
- Faster hardware.
- Better GPU/NPU acceleration.
- Better local AI support.
This would allow Milo to use a significantly larger and more capable conversational model.

Expected improvements include:
- Better Spanish understanding.
- Better reasoning.
- More natural conversation.
- Better instruction following.
- Better contextual understanding.
- Fewer hallucinations.

The goal is to keep conversational AI running locally while significantly increasing its capabilities.

---

## Better Wikipedia understanding

Improve Milo's ability to understand factual questions and select the correct Wikipedia article.

Possible improvements include:
- Better subject extraction.
- Better entity detection.
- Ranking several possible articles.
- Comparing article titles and descriptions.
- Rejecting unrelated results.
- Detecting ambiguous questions.
- Asking the user for clarification when necessary.

Example:

```text
User:
Milo háblame de Mercurio

Milo:
¿Te refieres al planeta Mercurio o al elemento químico?
```

This is preferable to automatically choosing the wrong article.

---

## Conversational context

Improve multi-turn conversations so Milo can understand follow-up questions.

Example:

```text
User:
Milo quién fue Albert Einstein

Milo:
...

User:
¿Dónde nació?
```

Milo should understand that the second question still refers to Albert Einstein.
This would make interaction with Milo feel much more natural.

---

## Alarms and reminders

Add deterministic alarm and reminder functionality.

Examples:

```text
Milo pon una alarma a las siete
```

```text
Milo pon una alarma mañana a las ocho
```

```text
Milo recuérdame comprar leche mañana
```

```text
Milo avísame dentro de veinte minutos
```

Possible functionality includes:
- Creating alarms.
- Cancelling alarms.
- One-time reminders.
- Repeating reminders.
- Listing active reminders.

These actions should remain controlled by Android functionality rather than arbitrary language-model actions.

---

## Improved interface

Redesign the current interface to make Milo feel more like a dedicated personal assistant.

Possible improvements include:
- Improved Milo face.
- Better eye animations.
- Better mouth animation.
- Listening animation.
- Thinking animation.
- Speaking animation.
- Better typography and spacing.
- Improved response presentation.
- Weather cards.
- News cards.
- Better source presentation.
- Dark mode.
- Smoother transitions.

The goal is for Milo to feel less like a normal Android application and more like a dedicated assistant.

---

# Long-term vision

The long-term goal of Milo is to turn a dedicated Android device into a personal assistant combining:

```text
Local AI
+
Voice interaction
+
Reliable information
+
Android device control
+
Privacy-conscious design
```

The objective is not to give a language model unrestricted control over the Android device.
Instead, Milo should understand what the user wants and route the request to the appropriate controlled component.

```text
"Abre YouTube"
        ↓
Android

"Qué tiempo hace en Madrid"
        ↓
Open-Meteo

"Quién fue Einstein"
        ↓
Wikipedia

"Quién eres"
        ↓
Local AI
```

---
