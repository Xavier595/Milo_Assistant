package com.example.milo_assistant

import android.os.Bundle
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
import androidx.compose.material3.Button
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

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var textToSpeech: TextToSpeech
    private var hasMicrophonePermission by mutableStateOf(false)
    private var isTtsReady by mutableStateOf(false)
    private var isSpeaking by mutableStateOf(false)
    private var statusText by mutableStateOf("Preparando voz...")
    private var mouthPulse by mutableStateOf(0)
    private var phraseIndex = 0

    private val phrases = listOf(
        "Hola, soy Milo.",
        "Estoy listo para ayudarte.",
        "Poco a poco aprenderé cosas nuevas.",
        "Gracias por hablar conmigo."
    )

    private val microphonePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasMicrophonePermission = granted

            statusText = if (granted) {
                "Micrófono preparado"
            } else {
                "Permiso de micrófono necesario"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasMicrophonePermission = hasRecordAudioPermission()
        textToSpeech = TextToSpeech(this, this)
        setContent {
            MiloScreen(
                statusText = statusText,
                isSpeaking = isSpeaking,
                mouthPulse = mouthPulse,
                isSpeakEnabled = isTtsReady && !isSpeaking,
                onSpeak = ::speakNextPhrase
            )
        }
        if (!hasMicrophonePermission) {
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

        statusText = if (hasMicrophonePermission) {
            "Micrófono preparado"
        } else {
            "Permiso de micrófono necesario"
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
                        isSpeaking = false
                        statusText = "En espera"
                    }
                }

                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        isSpeaking = false
                        statusText = "Error al hablar"
                    }
                }
            }
        )
    }
    private fun speakNextPhrase() {
        if (!isTtsReady || isSpeaking) {
            return
        }

        val phrase = phrases[phraseIndex]

        phraseIndex = (phraseIndex + 1) % phrases.size

        val result = textToSpeech.speak(
            phrase,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "milo-${System.currentTimeMillis()}"
        )

        if (result == TextToSpeech.ERROR) {
            isSpeaking = false
            statusText = "Error al hablar"
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }

        super.onDestroy()
    }

}

@Composable
private fun MiloScreen(
    statusText: String,
    isSpeaking: Boolean,
    mouthPulse: Int,
    isSpeakEnabled: Boolean,
    onSpeak: () -> Unit
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
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSpeak,
                enabled = isSpeakEnabled
            ) {
                Text(text = "Hablar")
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
        isSpeakEnabled = true,
        onSpeak = {}
    )
}