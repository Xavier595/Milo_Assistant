package com.example.milo_assistant.knowledge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class NewsArticle(
    val title: String,
    val url: String,
    val domain: String
)

class GdeltNewsClient {

    suspend fun getLatestNews(
        topic: String? = null,
        limit: Int = 5
    ): List<NewsArticle> =
        withContext(Dispatchers.IO) {

            val query =
                if (
                    topic.isNullOrBlank()
                ) {
                    /*
                     * Latest coverage published by
                     * Spanish-language Spanish outlets.
                     */
                    "sourcecountry:spain sourcelang:spanish"
                } else {

                    val cleanTopic =
                        topic.replace(
                            "\"",
                            ""
                        )

                    "\"$cleanTopic\" sourcelang:spanish"
                }

            val encodedQuery =
                URLEncoder.encode(
                    query,
                    StandardCharsets.UTF_8.toString()
                )

            val url =
                URL(
                    "https://api.gdeltproject.org/api/v2/doc/doc" +
                            "?query=$encodedQuery" +
                            "&mode=artlist" +
                            "&maxrecords=10" +
                            "&timespan=24h" +
                            "&sort=datedesc" +
                            "&format=jsonfeed"
                )

            val connection =
                url.openConnection()
                        as HttpURLConnection

            try {
                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    7_000

                connection.readTimeout =
                    7_000

                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                connection.setRequestProperty(
                    "User-Agent",
                    "MiloAssistant/1.0 Android"
                )

                if (
                    connection.responseCode
                    !in 200..299
                ) {
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

                val items =
                    json.optJSONArray(
                        "items"
                    )
                        ?: return@withContext emptyList()

                buildList {

                    val seenTitles =
                        mutableSetOf<String>()

                    for (
                    index in 0 until items.length()
                    ) {

                        val item =
                            items.getJSONObject(
                                index
                            )

                        val title =
                            item.optString(
                                "title"
                            )
                                .trim()

                        val articleUrl =
                            item.optString(
                                "url"
                            )
                                .ifBlank {
                                    item.optString(
                                        "external_url"
                                    )
                                }
                                .trim()

                        if (
                            title.isBlank() ||
                            articleUrl.isBlank()
                        ) {
                            continue
                        }

                        val normalizedTitle =
                            title.lowercase()

                        if (
                            !seenTitles.add(
                                normalizedTitle
                            )
                        ) {
                            continue
                        }

                        val domain =
                            try {
                                URL(
                                    articleUrl
                                )
                                    .host
                                    .removePrefix(
                                        "www."
                                    )
                            } catch (
                                exception: Exception
                            ) {
                                "fuente desconocida"
                            }

                        add(
                            NewsArticle(
                                title = title,
                                url = articleUrl,
                                domain = domain
                            )
                        )

                        if (size >= limit) {
                            break
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
}