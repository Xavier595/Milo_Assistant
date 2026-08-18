package com.example.milo_assistant.knowledge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

data class CurrentWeather(
    val locationName: String,
    val country: String?,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val humidityPercent: Int,
    val precipitationMm: Double,
    val weatherCode: Int,
    val windSpeedKmh: Double
) {

    fun spokenText(): String {

        val place =
            if (country.isNullOrBlank()) {
                locationName
            } else {
                "$locationName, $country"
            }

        val condition =
            weatherCodeToSpanish(
                weatherCode
            )

        return buildString {
            append(
                "Ahora mismo en $place hay "
            )

            append(
                "${temperatureC.roundToInt()} grados"
            )

            append(
                ", con $condition"
            )

            append(
                ". La sensación térmica es de " +
                        "${apparentTemperatureC.roundToInt()} grados"
            )

            append(
                ", la humedad es del $humidityPercent por ciento"
            )

            append(
                " y el viento es de aproximadamente " +
                        "${windSpeedKmh.roundToInt()} kilómetros por hora."
            )

            if (precipitationMm > 0.0) {
                append(
                    " Se están registrando " +
                            "$precipitationMm milímetros de precipitación."
                )
            }
        }
    }
}

private fun weatherCodeToSpanish(
    code: Int
): String =
    when (code) {
        0 ->
            "cielo despejado"

        1 ->
            "cielo mayormente despejado"

        2 ->
            "cielo parcialmente nuboso"

        3 ->
            "cielo cubierto"

        45, 48 ->
            "niebla"

        51, 53, 55 ->
            "llovizna"

        56, 57 ->
            "llovizna helada"

        61, 63, 65 ->
            "lluvia"

        66, 67 ->
            "lluvia helada"

        71, 73, 75, 77 ->
            "nieve"

        80, 81, 82 ->
            "chubascos"

        85, 86 ->
            "chubascos de nieve"

        95 ->
            "tormenta"

        96, 99 ->
            "tormenta con granizo"

        else ->
            "condiciones meteorológicas variables"
    }

class OpenMeteoWeatherClient {

    suspend fun getCurrentWeather(
        place: String
    ): CurrentWeather? =
        withContext(Dispatchers.IO) {

            val encodedPlace =
                URLEncoder.encode(
                    place,
                    StandardCharsets.UTF_8.toString()
                )

            val geocodingUrl =
                URL(
                    "https://geocoding-api.open-meteo.com/v1/search" +
                            "?name=$encodedPlace" +
                            "&count=1" +
                            "&language=es" +
                            "&format=json"
                )

            val geocodingJson =
                getJson(geocodingUrl)
                    ?: return@withContext null

            val results =
                geocodingJson.optJSONArray(
                    "results"
                )
                    ?: return@withContext null

            if (results.length() == 0) {
                return@withContext null
            }

            val location =
                results.getJSONObject(0)

            val latitude =
                location.getDouble(
                    "latitude"
                )

            val longitude =
                location.getDouble(
                    "longitude"
                )

            val name =
                location.getString(
                    "name"
                )

            val country =
                location.optString(
                    "country"
                )
                    .takeIf {
                        it.isNotBlank()
                    }

            val weatherUrl =
                URL(
                    "https://api.open-meteo.com/v1/forecast" +
                            "?latitude=$latitude" +
                            "&longitude=$longitude" +
                            "&current=" +
                            "temperature_2m," +
                            "apparent_temperature," +
                            "relative_humidity_2m," +
                            "precipitation," +
                            "weather_code," +
                            "wind_speed_10m" +
                            "&timezone=auto"
                )

            val weatherJson =
                getJson(weatherUrl)
                    ?: return@withContext null

            val current =
                weatherJson.optJSONObject(
                    "current"
                )
                    ?: return@withContext null

            CurrentWeather(
                locationName = name,
                country = country,
                temperatureC =
                    current.getDouble(
                        "temperature_2m"
                    ),
                apparentTemperatureC =
                    current.getDouble(
                        "apparent_temperature"
                    ),
                humidityPercent =
                    current.getInt(
                        "relative_humidity_2m"
                    ),
                precipitationMm =
                    current.getDouble(
                        "precipitation"
                    ),
                weatherCode =
                    current.getInt(
                        "weather_code"
                    ),
                windSpeedKmh =
                    current.getDouble(
                        "wind_speed_10m"
                    )
            )
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

            if (
                connection.responseCode
                !in 200..299
            ) {
                null
            } else {
                val text =
                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                JSONObject(text)
            }
        } finally {
            connection.disconnect()
        }
    }
}