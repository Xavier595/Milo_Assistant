package com.example.milo_assistant.knowledge

import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class NewsArticle(
    val title: String,
    val url: String,
    val source: String
)

class GoogleNewsClient {

    suspend fun getLatestNews(
        topic: String? = null,
        limit: Int = 5
    ): List<NewsArticle> =
        withContext(Dispatchers.IO) {

            val url =
                if (
                    topic.isNullOrBlank()
                ) {

                    URL(
                        "https://news.google.com/rss" +
                                "?hl=es" +
                                "&gl=ES" +
                                "&ceid=ES:es"
                    )

                } else {

                    val encodedTopic =
                        URLEncoder.encode(
                            topic,
                            StandardCharsets.UTF_8.toString()
                        )

                    URL(
                        "https://news.google.com/rss/search" +
                                "?q=$encodedTopic" +
                                "&hl=es" +
                                "&gl=ES" +
                                "&ceid=ES:es"
                    )
                }

            Log.d(
                "MiloNews",
                "Requesting Google News: $url"
            )

            val connection =
                url.openConnection()
                        as HttpURLConnection

            try {

                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    10_000

                connection.readTimeout =
                    10_000

                connection.instanceFollowRedirects =
                    true

                connection.setRequestProperty(
                    "Accept",
                    "application/rss+xml, application/xml, text/xml"
                )

                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 MiloAssistant/1.0"
                )

                val responseCode =
                    connection.responseCode

                Log.d(
                    "MiloNews",
                    "Google News HTTP $responseCode"
                )

                if (
                    responseCode !in 200..299
                ) {

                    Log.e(
                        "MiloNews",
                        "Google News HTTP error: $responseCode"
                    )

                    return@withContext emptyList()
                }

                val parser =
                    Xml.newPullParser()

                parser.setInput(
                    connection.inputStream,
                    "UTF-8"
                )

                parseFeed(
                    parser = parser,
                    limit = limit
                )

            } finally {

                connection.disconnect()
            }
        }

    private fun parseFeed(
        parser: XmlPullParser,
        limit: Int
    ): List<NewsArticle> {

        val articles =
            mutableListOf<NewsArticle>()

        var eventType =
            parser.eventType

        var insideItem =
            false

        var title: String? =
            null

        var link: String? =
            null

        var source: String? =
            null

        while (
            eventType !=
            XmlPullParser.END_DOCUMENT
        ) {

            when (eventType) {

                XmlPullParser.START_TAG -> {

                    when (
                        parser.name
                            .lowercase()
                    ) {

                        "item" -> {

                            insideItem =
                                true

                            title =
                                null

                            link =
                                null

                            source =
                                null
                        }

                        "title" -> {

                            if (insideItem) {

                                title =
                                    parser
                                        .nextText()
                                        .trim()
                            }
                        }

                        "link" -> {

                            if (insideItem) {

                                link =
                                    parser
                                        .nextText()
                                        .trim()
                            }
                        }

                        "source" -> {

                            if (insideItem) {

                                source =
                                    parser
                                        .nextText()
                                        .trim()
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {

                    if (
                        parser.name.equals(
                            "item",
                            ignoreCase = true
                        )
                    ) {

                        insideItem =
                            false

                        val cleanTitle =
                            title
                                ?.trim()
                                .orEmpty()

                        val cleanLink =
                            link
                                ?.trim()
                                .orEmpty()

                        val cleanSource =
                            source
                                ?.trim()
                                .orEmpty()

                        if (
                            cleanTitle.isNotBlank() &&
                            cleanLink.isNotBlank()
                        ) {

                            articles.add(
                                NewsArticle(
                                    title =
                                        removeSourceFromTitle(
                                            cleanTitle,
                                            cleanSource
                                        ),
                                    url =
                                        cleanLink,
                                    source =
                                        cleanSource.ifBlank {
                                            "Fuente desconocida"
                                        }
                                )
                            )
                        }

                        if (
                            articles.size >= limit
                        ) {

                            break
                        }
                    }
                }
            }

            eventType =
                parser.next()
        }

        Log.d(
            "MiloNews",
            "Parsed ${articles.size} news articles"
        )

        return articles
    }

    private fun removeSourceFromTitle(
        title: String,
        source: String
    ): String {

        if (
            source.isBlank()
        ) {
            return title
        }

        val suffix =
            " - $source"

        return if (
            title.endsWith(
                suffix,
                ignoreCase = true
            )
        ) {

            title.dropLast(
                suffix.length
            )
                .trim()

        } else {

            title
        }
    }
}