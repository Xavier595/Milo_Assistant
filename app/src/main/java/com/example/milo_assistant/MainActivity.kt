package com.example.milo_assistant

import java.text.Normalizer
import java.util.Calendar
import android.os.Bundle
import android.content.ActivityNotFoundException
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.milo_assistant.ai.LocalConversationalAi
import com.example.milo_assistant.knowledge.WikipediaKnowledgeClient
import com.example.milo_assistant.knowledge.OpenMeteoWeatherClient

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var textToSpeech: TextToSpeech
    private data class ContactPhone(
        val displayName: String,
        val phoneNumber: String
    )
    private var localAi: LocalConversationalAi? = null
    private val wikipediaKnowledgeClient =
        WikipediaKnowledgeClient()

    private val weatherClient =
        OpenMeteoWeatherClient()
    private var isAiReady by mutableStateOf(false)
    private var isAiThinking by mutableStateOf(false)
    private var speechRecognizer: SpeechRecognizer? = null
    private var pendingActionAfterSpeech: (() -> Unit)? = null
    private val mainHandler = Handler(
        Looper.getMainLooper()
    )

    private val restartListeningRunnable = Runnable {
        startListeningForMilo()
    }

    private var isActivityVisible = false
    private var hasMicrophonePermission by mutableStateOf(false)
    private var hasContactsPermission by mutableStateOf(false)
    private var hasCallPermission by mutableStateOf(false)
    private var pendingContactName: String? = null
    private var pendingCallContact: ContactPhone? = null

    private var isWaitingForCallConfirmation = false
    private var isTtsReady by mutableStateOf(false)
    private var isSpeaking by mutableStateOf(false)
    private var isListening by mutableStateOf(false)
    private var statusText by mutableStateOf("Preparando voz...")
    private var mouthPulse by mutableStateOf(0)
    private var lastCommand by mutableStateOf<String?>(null)
    private var commandResultText by mutableStateOf<String?>(null)
    private var phraseIndex = 0

    private val phrases = listOf(
        "Hola, soy Milo.",
        "Estoy listo para ayudarte.",
        "Poco a poco aprenderé cosas nuevas.",
        "Gracias por hablar conmigo."
    )

    private val recognitionIntent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "es-ES"
            )

            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                5
            )

            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                false
            )

        }
    }

    private val microphonePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasMicrophonePermission = granted

            if (granted) {
                initializeSpeechRecognizer()
                statusText = "Esperando a que digas Milo..."
                scheduleListeningRestart()
            } else {
                statusText = "Permiso de micrófono necesario"
            }
        }

    private val contactsPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            hasContactsPermission = granted

            val contactName = pendingContactName
            pendingContactName = null

            if (granted && contactName != null) {
                searchContactForCall(
                    contactName
                )
            } else {
                commandResultText =
                    "Permiso de contactos denegado"

                speakText(
                    "Necesito permiso para acceder a tus contactos"
                )
            }
        }

    private val callPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            hasCallPermission = granted

            val contact =
                pendingCallContact

            if (
                granted &&
                contact != null
            ) {
                placeConfirmedCall(
                    contact
                )
            } else {
                pendingCallContact = null
                isWaitingForCallConfirmation = false

                commandResultText =
                    "Permiso de llamadas denegado"

                speakText(
                    "No puedo realizar la llamada sin permiso"
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasMicrophonePermission = hasRecordAudioPermission()
        hasContactsPermission = hasReadContactsPermission()
        hasCallPermission = hasCallPhonePermission()
        textToSpeech = TextToSpeech(this, this)
        setContent {
            MiloScreen(
                statusText = statusText,
                isSpeaking = isSpeaking,
                mouthPulse = mouthPulse,
                lastCommand = lastCommand,
                commandResultText = commandResultText
            )
        }
        if (hasMicrophonePermission) {
            initializeSpeechRecognizer()
        } else {
            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            statusText = "No se pudo iniciar la voz"
            return
        }

        val languageResult = textToSpeech.setLanguage(
            Locale.forLanguageTag("es-ES")
        )

        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            statusText = "Voz en español no disponible"
            return
        }

        textToSpeech.setSpeechRate(0.9f)
        textToSpeech.setPitch(1.0f)

        configureSpeechListener()

        isTtsReady = true

        if (hasMicrophonePermission) {
            initializeSpeechRecognizer()
            statusText = "Esperando a que digas Milo..."
            scheduleListeningRestart()
        } else {
            statusText = "Permiso de micrófono necesario"
        }
    }
    private fun configureSpeechListener() {
        textToSpeech.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {
                    runOnUiThread {
                        isSpeaking = true
                        statusText = "Hablando..."
                        mouthPulse++
                    }
                }

                override fun onRangeStart(
                    utteranceId: String?,
                    start: Int,
                    end: Int,
                    frame: Int
                ) {
                    runOnUiThread {
                        if (isSpeaking) {
                            mouthPulse++
                        }
                    }
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        val action = pendingActionAfterSpeech
                        pendingActionAfterSpeech = null

                        isSpeaking = false

                        if (action != null) {
                            statusText = "Completando orden..."
                            action()
                        } else {
                            statusText = "Di Milo seguido de una orden"

                            scheduleListeningRestart(
                                delayMillis = 700L
                            )
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        pendingActionAfterSpeech = null
                        isSpeaking = false
                        statusText = "Error"

                        scheduleListeningRestart(
                            delayMillis = 1_000L
                        )
                    }
                }
            }
        )
    }

    private fun initializeSpeechRecognizer() {
        if (speechRecognizer != null || !hasMicrophonePermission) {
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText = "Reconocimiento de voz no disponible"
            return
        }

        speechRecognizer = SpeechRecognizer
            .createSpeechRecognizer(this)
            .apply {
                setRecognitionListener(
                    createRecognitionListener()
                )
            }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                statusText = "Escuchando..."
            }

            override fun onBeginningOfSpeech() {
                statusText = "Escuchando..."
            }

            override fun onRmsChanged(rmsdB: Float) {
            }

            override fun onBufferReceived(buffer: ByteArray?) {
            }

            override fun onEndOfSpeech() {
                statusText = "Procesando..."
            }

            override fun onError(error: Int) {
                isListening = false
                if (
                    isWaitingForCallConfirmation &&
                    isActivityVisible
                ) {
                    statusText = "Di sí o no"

                    speakText(
                        text = "No te he oído. Di sí o no.",
                        afterSpeech = {
                            startListeningForCallConfirmation()
                        }
                    )

                    return
                }

                if (isSpeaking || !isActivityVisible) {
                    return
                }

                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        statusText = "Permiso de micrófono necesario"
                    }

                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        statusText = "Reiniciando escucha..."

                        scheduleListeningRestart(
                            delayMillis = 1_000L
                        )
                    }

                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        statusText = "Error de red al escuchar"

                        scheduleListeningRestart(
                            delayMillis = 1_500L
                        )
                    }

                    else -> {
                        statusText = "Di Milo seguido de una orden"

                        scheduleListeningRestart(
                            delayMillis = 800L
                        )
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false

                val recognizedTexts = results
                    ?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    .orEmpty()

                if (isWaitingForCallConfirmation) {
                    handleCallConfirmation(
                        recognizedTexts
                    )

                    return
                }

                val capturedCommand = recognizedTexts
                    .asSequence()
                    .mapNotNull { recognizedText ->
                        extractCommandAfterMilo(recognizedText)
                    }
                    .firstOrNull()

                if (capturedCommand != null) {
                    lastCommand = capturedCommand
                    commandResultText = null
                    statusText = "Ejecutando orden..."

                    executeCommand(capturedCommand)
                } else {
                    statusText = "Di Milo seguido de una orden"

                    scheduleListeningRestart(
                        delayMillis = 700L
                    )
                }
            }

            override fun onPartialResults(
                partialResults: Bundle?
            ) {
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {
            }
        }
    }

    private fun handleCallConfirmation(
        recognizedTexts: List<String>
    ) {
        val normalizedAnswers =
            recognizedTexts.map { answer ->
                normalizeCommand(answer)
            }

        when {
            normalizedAnswers.any { answer ->
                answer == "si"
            } -> {
                val contact =
                    pendingCallContact

                isWaitingForCallConfirmation = false

                if (contact == null) {
                    cancelPendingCall(
                        "No hay ninguna llamada pendiente"
                    )

                    return
                }

                confirmCall(
                    contact
                )
            }

            normalizedAnswers.any { answer ->
                answer == "no"
            } -> {
                cancelPendingCall(
                    "Llamada cancelada"
                )
            }

            else -> {
                commandResultText =
                    "No he entendido la confirmación"

                speakText(
                    text =
                        "No te he entendido. Di sí o no.",
                    afterSpeech = {
                        startListeningForCallConfirmation()
                    }
                )
            }
        }
    }

    private fun confirmCall(
        contact: ContactPhone
    ) {
        hasCallPermission =
            hasCallPhonePermission()

        if (!hasCallPermission) {
            pendingCallContact = contact

            callPermissionLauncher.launch(
                Manifest.permission.CALL_PHONE
            )

            return
        }

        placeConfirmedCall(
            contact
        )
    }

    private fun placeConfirmedCall(
        contact: ContactPhone
    ) {
        if (!hasCallPhonePermission()) {
            commandResultText =
                "Permiso de llamadas necesario"

            return
        }

        pendingCallContact = null
        isWaitingForCallConfirmation = false

        commandResultText =
            "Llamando a ${contact.displayName}"

        val callIntent = Intent(
            Intent.ACTION_CALL
        ).apply {
            data = Uri.fromParts(
                "tel",
                contact.phoneNumber,
                null
            )
        }

        try {
            startActivity(
                callIntent
            )
        } catch (_: SecurityException) {
            commandResultText =
                "No tengo permiso para realizar la llamada"

            speakText(
                "No tengo permiso para realizar la llamada"
            )
        } catch (_: ActivityNotFoundException) {
            commandResultText =
                "No encuentro una aplicación de teléfono"

            speakText(
                "No encuentro una aplicación para realizar la llamada"
            )
        }
    }

    private fun startListeningForMilo() {
        if (
            !isActivityVisible ||
            !hasMicrophonePermission ||
            !isTtsReady ||
            isSpeaking ||
            isListening
        ) {
            return
        }

        initializeSpeechRecognizer()

        val recognizer = speechRecognizer ?: return

        try {
            isListening = true
            statusText = "Esperando a que digas Milo..."

            recognizer.startListening(
                recognitionIntent
            )
        } catch (_: SecurityException) {
            isListening = false
            statusText = "Permiso de micrófono necesario"
        } catch (_: RuntimeException) {
            isListening = false
            statusText = "No se pudo iniciar la escucha"

            scheduleListeningRestart(
                delayMillis = 1_000L
            )
        }
    }

    private fun stopListeningForMilo() {
        mainHandler.removeCallbacks(
            restartListeningRunnable
        )

        if (isListening) {
            speechRecognizer?.cancel()
            isListening = false
        }
    }

    private fun scheduleListeningRestart(
        delayMillis: Long = 300L
    ) {
        mainHandler.removeCallbacks(
            restartListeningRunnable
        )

        if (
            !isActivityVisible ||
            !hasMicrophonePermission ||
            !isTtsReady ||
            isSpeaking
        ) {
            return
        }

        mainHandler.postDelayed(
            restartListeningRunnable,
            delayMillis
        )
    }

    private fun speakNextPhrase() {
        if (!isTtsReady || isSpeaking) {
            return
        }

        val phrase = phrases[phraseIndex]

        phraseIndex = (phraseIndex + 1) % phrases.size

        speakText(phrase)
    }

    private fun extractWeatherLocation(
        command: String
    ): String? {

        val normalized =
            normalizeCommand(command)

        val prefixes =
            listOf(
                "que tiempo hace hoy en ",
                "que tiempo hace en ",
                "como esta el tiempo hoy en ",
                "como esta el tiempo en ",
                "que clima hace hoy en ",
                "que clima hace en ",
                "tiempo en ",
                "clima en "
            )

        for (prefix in prefixes) {

            if (
                normalized.startsWith(
                    prefix
                )
            ) {
                return normalized
                    .removePrefix(prefix)
                    .trim()
                    .ifBlank {
                        null
                    }
            }
        }

        return null
    }

    private suspend fun answerWeather(
        location: String
    ) {

        statusText =
            "Consultando el tiempo..."

        val weather =
            try {
                weatherClient.getCurrentWeather(
                    location
                )
            } catch (
                exception: Exception
            ) {
                null
            }

        if (weather == null) {

            val message =
                "No he podido encontrar información meteorológica para ese lugar."

            commandResultText =
                message

            isAiThinking =
                false

            speakText(message)

            return
        }

        val response =
            weather.spokenText()

        commandResultText =
            """
        $response

        Fuente meteorológica: Open-Meteo
        """.trimIndent()

        isAiThinking =
            false

        speakText(response)
    }

    private fun speakText(
        text: String,
        afterSpeech: (() -> Unit)? = null
    ) {
        if (!isTtsReady || isSpeaking) {
            return
        }

        pendingActionAfterSpeech = afterSpeech
        isSpeaking = true
        statusText = "Hablando..."

        stopListeningForMilo()

        val result = textToSpeech.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "milo-${System.currentTimeMillis()}"
        )

        if (result == TextToSpeech.ERROR) {
            pendingActionAfterSpeech = null
            isSpeaking = false
            statusText = "Error al hablar"

            scheduleListeningRestart(
                delayMillis = 1_000L
            )
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasReadContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCallPhonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onStart() {
        super.onStart()

        isActivityVisible = true
        scheduleListeningRestart()
    }

    override fun onStop() {
        isActivityVisible = false

        mainHandler.removeCallbacks(
            restartListeningRunnable
        )

        stopListeningForMilo()

        super.onStop()
    }

    private fun shouldUseWikipediaKnowledge(
        command: String
    ): Boolean {

        val normalized =
            normalizeCommand(command)

        val prefixes =
            listOf(
                "quien es",
                "quien fue",
                "que es",
                "que fue",
                "donde esta",
                "donde nacio",
                "cuando nacio",
                "cuando murio",
                "como funciona",
                "por que",
                "explicame",
                "hablame de"
            )

        return prefixes.any {
            normalized.startsWith(it)
        }
    }

    private fun normalizeCommand(
        command: String
    ): String {
        return Normalizer
            .normalize(
                command.lowercase(Locale.ROOT),
                Normalizer.Form.NFD
            )
            .replace(
                "\\p{M}+".toRegex(),
                ""
            )
            .replace(
                "[¿?¡!.,;:]".toRegex(),
                ""
            )
            .replace(
                "\\s+".toRegex(),
                " "
            )
            .trim()
    }

    private fun extractContactName(
        command: String
    ): String? {
        val match = CALL_CONTACT_COMMAND
            .find(command.trim())
            ?: return null

        return match
            .groupValues[1]
            .trim()
            .takeIf { contactName ->
                contactName.isNotBlank()
            }
    }

    private fun isTimeCommand(
        command: String
    ): Boolean {
        return command in setOf(
            "dime la hora",
            "que hora es",
            "dime que hora es"
        )
    }

    private fun isGreetingCommand(
        command: String
    ): Boolean {
        return command in setOf(
            "saluda",
            "saludame",
            "dime algo",
            "di una frase",
            "presentate",
            "di hola"
        )
    }

    private fun tellCurrentTime() {
        val calendar = Calendar.getInstance()

        val hour = calendar.get(
            Calendar.HOUR_OF_DAY
        )

        val minute = calendar.get(
            Calendar.MINUTE
        )

        val formattedTime = String.format(
            Locale.ROOT,
            "%02d:%02d",
            hour,
            minute
        )

        commandResultText = "Hora actual: $formattedTime"

        val spokenTime = when {
            hour == 1 && minute == 0 -> {
                "Es la una en punto"
            }

            hour == 1 -> {
                "Es la una y $minute"
            }

            minute == 0 -> {
                "Son las $hour en punto"
            }

            else -> {
                "Son las $hour y $minute"
            }
        }

        speakText(spokenTime)
    }

    private fun isOpenYouTubeCommand(
        command: String
    ): Boolean {
        return command in setOf(
            "abre youtube",
            "abrir youtube",
            "abreme youtube",
            "inicia youtube"
        )
    }

    private fun openYouTube() {
        val launchIntent = packageManager
            .getLaunchIntentForPackage(
                YOUTUBE_PACKAGE
            )

        if (launchIntent == null) {
            commandResultText =
                "YouTube no está instalado"

            speakText(
                "No encuentro YouTube instalado"
            )

            return
        }

        commandResultText = "Abriendo YouTube"

        speakText(
            text = "Abriendo YouTube",
            afterSpeech = {
                try {
                    startActivity(launchIntent)
                } catch (_: ActivityNotFoundException) {
                    commandResultText =
                        "No se pudo abrir YouTube"

                    speakText(
                        "No se pudo abrir YouTube"
                    )
                }
            }
        )
    }

    private fun handleCallContactCommand(
        contactName: String
    ) {
        hasContactsPermission =
            hasReadContactsPermission()

        if (!hasContactsPermission) {
            pendingContactName = contactName

            commandResultText =
                "Solicitando acceso a contactos"

            contactsPermissionLauncher.launch(
                Manifest.permission.READ_CONTACTS
            )

            return
        }

        searchContactForCall(
            contactName
        )
    }

    private fun searchContactForCall(
        contactName: String
    ) {
        lifecycleScope.launch {
            statusText = "Buscando contacto..."

            val contact = findContactPhone(
                contactName
            )

            if (contact == null) {
                commandResultText =
                    "No encuentro a $contactName"

                speakText(
                    "No encuentro a $contactName en tus contactos"
                )

                return@launch
            }

            askForCallConfirmation(
                contact
            )
        }
    }

    private fun askForCallConfirmation(
        contact: ContactPhone
    ) {
        pendingCallContact = contact
        isWaitingForCallConfirmation = true

        commandResultText =
            "Esperando confirmación"

        speakText(
            text =
                "¿Seguro que quieres llamar a ${contact.displayName}?",
            afterSpeech = {
                startListeningForCallConfirmation()
            }
        )
    }

    private fun startListeningForCallConfirmation() {
        if (
            !isActivityVisible ||
            !hasMicrophonePermission ||
            isSpeaking ||
            isListening
        ) {
            return
        }

        initializeSpeechRecognizer()

        val recognizer =
            speechRecognizer ?: return

        try {
            isListening = true
            statusText = "Di sí o no"

            recognizer.startListening(
                recognitionIntent
            )
        } catch (_: SecurityException) {
            isListening = false

            cancelPendingCall(
                "No tengo permiso para escuchar"
            )
        } catch (_: RuntimeException) {
            isListening = false

            cancelPendingCall(
                "No pude escuchar la confirmación"
            )
        }
    }

    private fun cancelPendingCall(
        message: String
    ) {
        pendingCallContact = null
        isWaitingForCallConfirmation = false

        commandResultText = message

        speakText(
            "De acuerdo, cancelo la llamada"
        )
    }

    private suspend fun findContactPhone(
        contactName: String
    ): ContactPhone? = withContext(Dispatchers.IO) {

        val searchUri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
            Uri.encode(contactName)
        )

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            contentResolver.query(
                searchUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->

                val nameColumn =
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    )

                val numberColumn =
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )

                var firstMatch: ContactPhone? = null

                while (cursor.moveToNext()) {

                    val displayName =
                        cursor.getString(nameColumn)
                            ?: continue

                    val phoneNumber =
                        cursor.getString(numberColumn)
                            ?: continue

                    val candidate = ContactPhone(
                        displayName = displayName,
                        phoneNumber = phoneNumber
                    )

                    if (firstMatch == null) {
                        firstMatch = candidate
                    }

                    if (
                        normalizeCommand(displayName) ==
                        normalizeCommand(contactName)
                    ) {
                        return@withContext candidate
                    }
                }

                firstMatch
            }
        } catch (_: SecurityException) {
            null
        }
    }

    private suspend fun getOrCreateLocalAi(): LocalConversationalAi {
        localAi?.let { existingAi ->
            return existingAi
        }

        val newAi = LocalConversationalAi(
            context = applicationContext,
            modelPath = LOCAL_AI_MODEL_PATH
        )

        newAi.initialize()

        localAi = newAi
        isAiReady = true

        return newAi
    }

    private fun askLocalAi(
        question: String
    ) {
        if (
            isAiThinking ||
            isSpeaking
        ) {
            return
        }

        isAiThinking = true

        commandResultText = null

        statusText =
            if (isAiReady) {
                "Pensando..."
            } else {
                "Cargando IA local..."
            }

        stopListeningForMilo()

        lifecycleScope.launch {

            try {
                val ai =
                    getOrCreateLocalAi()

                if (
                    shouldUseWikipediaKnowledge(
                        question
                    )
                ) {

                    statusText =
                        "Buscando información..."

                    val sources =
                        try {
                            wikipediaKnowledgeClient.search(
                                question
                            )
                        } catch (
                            exception: Exception
                        ) {
                            emptyList()
                        }

                    if (sources.isEmpty()) {

                        val message =
                            "No he podido verificar esa información ahora mismo."

                        commandResultText =
                            message

                        isAiThinking =
                            false

                        speakText(message)

                        return@launch
                    }

                    val groundingContext =
                        sources.joinToString(
                            separator = "\n\n"
                        ) {
                            it.asPromptContext()
                        }

                    statusText =
                        "Pensando..."

                    val response =
                        ai.ask(
                            question = question,
                            groundingContext =
                                groundingContext
                        )

                    val sourceNames =
                        sources.joinToString(
                            separator = ", "
                        ) {
                            it.title
                        }

                    commandResultText =
                        """
        $response

        Fuente: Wikipedia — $sourceNames
        """.trimIndent()

                    isAiThinking =
                        false

                    speakText(response)

                    return@launch
                }

                statusText =
                    "Pensando..."

                val response =
                    ai.ask(question)

                commandResultText =
                    response

                isAiThinking =
                    false

                speakText(response)

            } catch (exception: Exception) {

                isAiThinking = false

                commandResultText =
                    "IA local no disponible"

                speakText(
                    "No puedo iniciar mi inteligencia local ahora mismo"
                )
            }
        }
    }

    private fun executeCommand(
        command: String
    ) {
        val normalizedCommand = normalizeCommand(
            command
        )
        val contactName = extractContactName(
            command
        )

        when {
            contactName != null -> {
                handleCallContactCommand(
                    contactName
                )
            }

            isOpenYouTubeCommand(normalizedCommand) -> {
                openYouTube()
            }

            isTimeCommand(normalizedCommand) -> {
                tellCurrentTime()
            }

            isGreetingCommand(normalizedCommand) -> {
                commandResultText =
                    "Milo ha pronunciado una frase"

                speakNextPhrase()
            }

            else -> {
                askLocalAi(
                    command
                )
            }
        }
    }

    private fun extractCommandAfterMilo(
        recognizedText: String
    ): String? {
        val miloMatch = MILO_WORD.find(recognizedText)
            ?: return null

        val command = recognizedText
            .substring(miloMatch.range.last + 1)
            .trim { character ->
                character.isWhitespace() ||
                        character in ",.:;-!?¿¡"
            }

        return command.ifBlank {
            null
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(
            restartListeningRunnable
        )
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        localAi?.close()
        localAi = null
        super.onDestroy()
    }

    private companion object {
        const val LOCAL_AI_MODEL_PATH =
            "/data/local/tmp/llm/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
        const val YOUTUBE_PACKAGE =
            "com.google.android.youtube"

        val MILO_WORD = Regex(
            pattern = "\\bmilo\\b",
            option = RegexOption.IGNORE_CASE
        )

        val CALL_CONTACT_COMMAND = Regex(
            pattern = "^(?:llama|llamar)(?:\\s+a)?\\s+(.+)$",
            option = RegexOption.IGNORE_CASE
        )
    }
}

@Composable
private fun MiloScreen(
    statusText: String,
    isSpeaking: Boolean,
    mouthPulse: Int,
    lastCommand: String?,
    commandResultText: String?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05080C)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiloEye()
                MiloEye()
            }
            Spacer(modifier = Modifier.height(30.dp))
            MiloMouth(
                isSpeaking = isSpeaking,
                mouthPulse = mouthPulse
            )
            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = "MILO",
                color = Color(0xFF9FE7FF),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 6.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = statusText,
                color = Color(0xFF77838E),
                fontSize = 14.sp
            )
            lastCommand?.let { command ->
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Última orden",
                    color = Color(0xFF77838E),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = command,
                    color = Color(0xFF9FE7FF),
                    fontSize = 16.sp
                )
            }
            commandResultText?.let { result ->
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Resultado",
                    color = Color(0xFF77838E),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = result,
                    color = Color(0xFFE1F8FF),
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun MiloMouth(
    isSpeaking: Boolean,
    mouthPulse: Int
) {
    var isMouthOpen by remember {
        mutableStateOf(false)
    }
    val openHeight = when (mouthPulse % 3) {
        0 -> 22.dp
        1 -> 28.dp
        else -> 18.dp
    }
    LaunchedEffect(mouthPulse, isSpeaking) {
        if (!isSpeaking) {
            isMouthOpen = false
            return@LaunchedEffect
        }

        isMouthOpen = true
        delay(95)
        isMouthOpen = false
    }

    val animatedHeight by animateDpAsState(
        targetValue = if (isMouthOpen) {
            openHeight
        } else {
            6.dp
        },
        animationSpec = tween(
            durationMillis = 70
        ),
        label = "Milo mouth height"
    )

    val animatedWidth by animateDpAsState(
        targetValue = if (isMouthOpen) {
            40.dp
        } else {
            48.dp
        },
        animationSpec = tween(
            durationMillis = 70
        ),
        label = "Milo mouth width"
    )

    Box(
        modifier = Modifier
            .size(
                width = animatedWidth,
                height = animatedHeight
            )
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF9FE7FF))
    )
}

@Composable
private fun MiloEye() {
    Box(
        modifier = Modifier
            .size(
                width = 82.dp,
                height = 110.dp
            )
            .clip(RoundedCornerShape(42.dp))
            .background(Color(0xFFE1F8FF)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(CircleShape)
                .background(Color(0xFF07151C))
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 720
)
@Composable
private fun MiloScreenPreview() {
    MiloScreen(
        statusText = "En espera",
        isSpeaking = false,
        mouthPulse = 0,
        lastCommand = "abre Youtube",
        commandResultText = "Abriendo YouTube"
    )
}