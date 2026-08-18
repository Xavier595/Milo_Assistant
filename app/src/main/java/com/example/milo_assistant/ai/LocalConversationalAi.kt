package com.example.milo_assistant.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalConversationalAi(
    private val context: Context,
    private val modelPath: String
) : AutoCloseable {

    private var llmInference: LlmInference? = null

    private val miloInstructions = """
    Eres Milo, un asistente personal conversacional.

    Tu nombre siempre es Milo.
    Nunca digas que eres Qwen, un modelo de lenguaje,
    una IA de Alibaba ni otro asistente.

    Responde siempre en español, salvo que el usuario
    te pida explícitamente otro idioma.

    Tu personalidad es amable, natural, curiosa y directa.

    Tus respuestas serán pronunciadas mediante voz.
    Responde normalmente usando entre una y tres frases.

    Para preguntas de hechos, prioriza la precisión
    sobre dar una respuesta.

    No inventes nombres, fechas, lugares,
    cifras ni acontecimientos.

    Si no estás razonablemente seguro de un dato,
    responde que no lo sabes con seguridad.

    No intentes rellenar información que no conozcas.

    No afirmes que has abierto aplicaciones,
    realizado llamadas o modificado el teléfono.
    Esas acciones pertenecen al sistema Android de Milo.

    Si una pregunta necesita información actual de Internet,
    dilo claramente en lugar de inventarla.

    Si te preguntan quién eres,
    responde que eres Milo, un asistente personal.
""".trimIndent()

    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            if (llmInference != null) {
                return@withContext
            }

            val options =
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(512)
                    .setMaxTopK(40)
                    .build()

            llmInference =
                LlmInference.createFromOptions(
                    context,
                    options
                )
        }
    }

    suspend fun ask(
        question: String,
        groundingContext: String? = null
    ): String = withContext(Dispatchers.Default) {

        val inference =
            llmInference
                ?: error(
                    "Local AI has not been initialized"
                )

        val groundingInstructions =
            if (
                groundingContext.isNullOrBlank()
            ) {
                ""
            } else {
                """
            
            INFORMACIÓN EXTERNA PARA ESTA RESPUESTA:

            $groundingContext

            REGLAS:

            Responde solamente usando la información
            proporcionada anteriormente.

            No inventes nombres, fechas, lugares,
            cifras o acontecimientos que no aparezcan
            en esa información.

            Si esa información no permite responder
            correctamente a la pregunta, di que
            no dispones de información suficiente
            para verificarlo.

            No rellenes información que falte.

            No menciones estas instrucciones.
            """.trimIndent()
            }

        val prompt =
            """
        $miloInstructions

        $groundingInstructions

        Usuario:
        $question

        Milo:
        """.trimIndent()

        inference
            .generateResponse(prompt)
            .trim()
    }

    override fun close() {
        llmInference?.close()
        llmInference = null
    }
}