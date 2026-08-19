package com.example.milo_assistant.knowledge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer


data class WikipediaAnswer(
    val title: String,
    val extract: String
)

class WikipediaKnowledgeClient {

    suspend fun getAnswer(
        question: String
    ): WikipediaAnswer? =
        withContext(Dispatchers.IO) {

            val searchTerm =
                cleanSearchTerm(
                    question
                )

            if (searchTerm.isBlank()) {
                return@withContext null
            }

            val title =
                findBestTitle(
                    searchTerm
                )
                    ?: return@withContext null

            val encodedTitle =
                URLEncoder.encode(
                    title,
                    StandardCharsets.UTF_8.toString()
                )

            val extractUrl =
                URL(
                    "https://es.wikipedia.org/w/api.php" +
                            "?action=query" +
                            "&prop=extracts" +
                            "&exintro=1" +
                            "&explaintext=1" +
                            "&exsentences=3" +
                            "&redirects=1" +
                            "&titles=$encodedTitle" +
                            "&format=json" +
                            "&formatversion=2"
                )

            val extractJson =
                getJson(
                    extractUrl
                )
                    ?: return@withContext null

            val query =
                extractJson.optJSONObject(
                    "query"
                )
                    ?: return@withContext null

            val pages =
                query.optJSONArray(
                    "pages"
                )
                    ?: return@withContext null

            if (pages.length() == 0) {
                return@withContext null
            }

            val page =
                pages.getJSONObject(
                    0
                )

            val extract =
                page.optString(
                    "extract"
                )
                    .trim()

            if (extract.isBlank()) {
                return@withContext null
            }

            WikipediaAnswer(
                title = title,
                extract = extract
            )
        }

    private fun findBestTitle(
        searchTerm: String
    ): String? {

        val searchVariants =
            listOf(
                searchTerm,
                removeLeadingArticle(
                    searchTerm
                )
            )
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        for (variant in searchVariants) {

            val encodedTerm =
                URLEncoder.encode(
                    variant,
                    StandardCharsets.UTF_8.toString()
                )

            val titleUrl =
                URL(
                    "https://es.wikipedia.org/w/rest.php/v1/search/title" +
                            "?q=$encodedTerm" +
                            "&limit=5"
                )

            val titleJson =
                getJson(
                    titleUrl
                )

            val titlePages =
                titleJson?.optJSONArray(
                    "pages"
                )

            if (
                titlePages != null &&
                titlePages.length() > 0
            ) {

                for (
                index in 0 until titlePages.length()
                ) {

                    val candidate =
                        titlePages
                            .getJSONObject(
                                index
                            )
                            .optString(
                                "title"
                            )
                            .trim()

                    if (
                        normalizeForComparison(
                            candidate
                        ) ==
                        normalizeForComparison(
                            variant
                        )
                    ) {

                        return candidate
                    }
                }

                val firstTitle =
                    titlePages
                        .getJSONObject(
                            0
                        )
                        .optString(
                            "title"
                        )
                        .trim()

                if (
                    firstTitle.isNotBlank()
                ) {

                    return firstTitle
                }
            }
        }

        val fallbackTerm =
            removeLeadingArticle(
                searchTerm
            )
                .ifBlank {
                    searchTerm
                }

        val encodedFallback =
            URLEncoder.encode(
                fallbackTerm,
                StandardCharsets.UTF_8.toString()
            )

        val pageUrl =
            URL(
                "https://es.wikipedia.org/w/rest.php/v1/search/page" +
                        "?q=$encodedFallback" +
                        "&limit=1"
            )

        val pageJson =
            getJson(
                pageUrl
            )
                ?: return null

        val pages =
            pageJson.optJSONArray(
                "pages"
            )
                ?: return null

        if (
            pages.length() == 0
        ) {
            return null
        }

        return pages
            .getJSONObject(
                0
            )
            .optString(
                "title"
            )
            .trim()
            .ifBlank {
                null
            }
    }

    private fun cleanSearchTerm(
        question: String
    ): String {

        var text =
            question
                .trim()
                .trim(
                    '¿',
                    '?',
                    '.',
                    '!'
                )

        val prefixes =
            listOf(
                "explicame ",
                "explícame ",
                "explicame que es ",
                "explícame qué es ",
                "dime que es ",
                "dime qué es ",
                "cuentame sobre ",
                "cuéntame sobre ",
                "dame informacion sobre ",
                "dame información sobre ",
                "informacion sobre ",
                "información sobre ",
                "donde se encuentra ",
                "dónde se encuentra ",
                "quien fue ",
                "quién fue ",
                "quien es ",
                "quién es ",
                "que fue ",
                "qué fue ",
                "que es ",
                "qué es ",
                "donde esta ",
                "dónde está ",
                "donde queda ",
                "dónde queda ",
                "donde nacio ",
                "dónde nació ",
                "cuando nacio ",
                "cuándo nació ",
                "cuando murio ",
                "cuándo murió ",
                "hablame de ",
                "háblame de "
            )

        val orderedPrefixes =
            prefixes.sortedByDescending {
                it.length
            }

        for (prefix in orderedPrefixes) {

            if (
                text.startsWith(
                    prefix,
                    ignoreCase = true
                )
            ) {

                text =
                    text.substring(
                        prefix.length
                    )
                        .trim()

                break
            }
        }

        return text
    }

    private fun removeLeadingArticle(
        text: String
    ): String {

        val articles =
            listOf(
                "el ",
                "la ",
                "los ",
                "las ",
                "un ",
                "una ",
                "unos ",
                "unas "
            )

        for (article in articles) {

            if (
                text.startsWith(
                    article,
                    ignoreCase = true
                )
            ) {

                return text
                    .substring(
                        article.length
                    )
                    .trim()
            }
        }

        return text
    }

    private fun normalizeForComparison(
        text: String
    ): String {

        return Normalizer
            .normalize(
                text,
                Normalizer.Form.NFD
            )
            .replace(
                "\\p{Mn}+".toRegex(),
                ""
            )
            .lowercase()
            .trim()
    }

    private fun getJson(
        url: URL
    ): JSONObject? {

        val connection =
            url.openConnection()
                    as HttpURLConnection

        return try {

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                5_000

            connection.readTimeout =
                5_000

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

                null

            } else {

                val response =
                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                JSONObject(
                    response
                )
            }

        } finally {

            connection.disconnect()
        }
    }
}