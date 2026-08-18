package com.example.milo_assistant.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalConversationalAi(
    private val modelPath: String
) : AutoCloseable {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun initialize() {
        withContext(Dispatchers.Default) {

            if (engine != null) {
                return@withContext
            }

            val newEngine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU()
                )
            )

            newEngine.initialize()

            val conversationConfig =
                ConversationConfig(
                    systemInstruction = Contents.of(
                        """
                        Eres Milo, un asistente personal conversacional.

                        Responde principalmente en español.

                        Tu personalidad es amable, natural, curiosa y directa.

                        Tus respuestas serán pronunciadas mediante voz,
                        por lo que normalmente debes responder de forma breve,
                        usando entre una y tres frases.

                        No afirmes que has abierto aplicaciones,
                        realizado llamadas o modificado el teléfono.
                        Esas acciones pertenecen al sistema Android de Milo.

                        Si no conoces la respuesta, dilo claramente.

                        Cuando una pregunta dependa de información actual
                        de Internet, indícalo en lugar de inventar datos.

                        Tu nombre es Milo.
                        """.trimIndent()
                    ),

                    samplerConfig = SamplerConfig(
                        topK = 20,
                        topP = 0.9,
                        temperature = 0.7
                    )
                )

            val newConversation =
                newEngine.createConversation(
                    conversationConfig
                )

            engine = newEngine
            conversation = newConversation
        }
    }

    suspend fun ask(
        question: String
    ): String = withContext(Dispatchers.Default) {

        val activeConversation =
            conversation
                ?: error(
                    "Local AI has not been initialized"
                )

        activeConversation
            .sendMessage(question)
            .toString()
            .trim()
    }

    override fun close() {
        conversation?.close()
        conversation = null

        engine?.close()
        engine = null
    }
}