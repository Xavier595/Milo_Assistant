package com.example.milo_assistant.knowledge

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class KnowledgeSource(
    val title: String,
    val description: String?,
    val excerpt: String
) {

    fun asPromptContext(): String =
        buildString {
            appendLine("Fuente: Wikipedia - $title")

            description
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    appendLine("Descripción: $it")
                }

            if (excerpt.isNotBlank()) {
                appendLine("Extracto: $excerpt")
            }
        }
}

class WikipediaKnowledgeClient {

    suspend fun search(
        query: String,
        limit: Int = 3
    ): List<KnowledgeSource> =
        withContext(Dispatchers.IO) {

            val encodedQuery =
                URLEncoder.encode(
                    query,
                    StandardCharsets.UTF_8.toString()
                )

            val url =
                URL(
                    "https://es.wikipedia.org/w/rest.php/v1/search/page" +
                            "?q=$encodedQuery&limit=$limit"
                )

            val connection =
                url.openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000

                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                connection.setRequestProperty(
                    "User-Agent",
                    "MiloAssistant/1.0 Android"
                )

                if (connection.responseCode !in 200..299) {
                    return@withContext emptyList()
                }

                val response =
                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                val json =
                    JSONObject(response)

                val pages =
                    json.optJSONArray("pages")
                        ?: return@withContext emptyList()

                buildList {
                    for (
                    index in 0 until pages.length()
                    ) {
                        val page =
                            pages.getJSONObject(index)

                        val title =
                            page.optString("title")
                                .trim()

                        val description =
                            if (
                                page.isNull("description")
                            ) {
                                null
                            } else {
                                page.optString(
                                    "description"
                                ).trim()
                            }

                        val excerpt =
                            HtmlCompat.fromHtml(
                                page.optString("excerpt"),
                                HtmlCompat.FROM_HTML_MODE_LEGACY
                            )
                                .toString()
                                .trim()

                        if (
                            title.isNotBlank() &&
                            (
                                    excerpt.isNotBlank() ||
                                            !description.isNullOrBlank()
                                    )
                        ) {
                            add(
                                KnowledgeSource(
                                    title = title,
                                    description = description,
                                    excerpt = excerpt
                                )
                            )
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
}