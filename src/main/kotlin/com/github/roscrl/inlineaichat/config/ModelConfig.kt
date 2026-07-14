package com.github.roscrl.llm.config

import com.github.roscrl.inlineaichat.notifications.NotificationUtil
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object ModelConfig {
    private val logger = Logger.getInstance(ModelConfig::class.java)
    private const val DEFAULT_MODEL = "anthropic/claude-3.5-sonnet"
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val defaultModels: List<String> by lazy {
        try {
            logger.info("Loading default models from configuration")
            loadModels().also { models ->
                logger.info("Successfully loaded ${models.size} models: ${models.joinToString()}")
            }
        } catch (e: Exception) {
            logger.error("Failed to load models from configuration", e)
            handleModelLoadError(e)
            listOf(DEFAULT_MODEL).also {
                logger.info("Using fallback model: $DEFAULT_MODEL")
            }
        }
    }

    /**
     * Fetch available models from an OpenAI-compatible /models endpoint.
     * Returns sorted model IDs, or empty list on failure.
     */
    fun fetchModelsFromApi(baseUrl: String, apiKey: String = ""): List<String> {
        val url = "${baseUrl.trimEnd('/')}/models"
        logger.info("Fetching models from $url")

        return try {
            val request = Request.Builder()
                .url(url)
                .apply {
                    if (apiKey.isNotEmpty()) {
                        addHeader("Authorization", "Bearer $apiKey")
                    }
                }
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.warn("Failed to fetch models: HTTP ${response.code}")
                    return emptyList()
                }

                val body = response.body?.string() ?: return emptyList()
                val root = json.parseToJsonElement(body).jsonObject

                // Standard OpenAI format: {"data": [{"id": "model-name", ...}]}
                val dataArray = root["data"]?.jsonArray ?: return emptyList()

                val models = dataArray.mapNotNull { element ->
                    try {
                        element.jsonObject["id"]?.jsonPrimitive?.content
                    } catch (e: Exception) {
                        null
                    }
                }.sorted()

                logger.info("Fetched ${models.size} models from API")
                models
            }
        } catch (e: Exception) {
            logger.warn("Error fetching models from API: ${e.message}")
            emptyList()
        }
    }

    private fun loadModels(): List<String> {
        try {
            logger.info("Attempting to load models.json from resources")
            val inputStream = ModelConfig::class.java.classLoader.getResourceAsStream("models.json")
                ?: throw IOException("models.json not found in resources")

            val jsonContent = InputStreamReader(inputStream).use { it.readText() }
            logger.debug("Successfully read models.json: $jsonContent")

            val jsonObject = json.parseToJsonElement(jsonContent)
            val modelsArray = jsonObject.jsonObject["defaultModels"]?.jsonArray
                ?: throw IllegalStateException("No 'defaultModels' array found in configuration")

            return modelsArray.map { it.jsonPrimitive.content }.also { models ->
                if (models.isEmpty()) {
                    logger.error("No models found in configuration file")
                    throw IllegalStateException("No models found in configuration")
                }
                logger.info("Successfully loaded ${models.size} models: ${models.joinToString()}")
            }
        } catch (e: IOException) {
            logger.error("Failed to read models configuration", e)
            throw ModelConfigException("Failed to read models configuration", e)
        } catch (e: kotlinx.serialization.SerializationException) {
            logger.error("Invalid models configuration format", e)
            throw ModelConfigException("Invalid models configuration format", e)
        } catch (e: Exception) {
            logger.error("Unexpected error loading models", e)
            throw ModelConfigException("Unexpected error loading models", e)
        }
    }

    private fun handleModelLoadError(error: Exception) {
        val (title, content) = when (error) {
            is ModelConfigException -> when (error.cause) {
                is IOException -> "Configuration Not Found" to "Could not find or read models.json. Using default model."
                is kotlinx.serialization.SerializationException -> "Invalid Configuration" to "models.json has an invalid format. Using default model."
                else -> "Configuration Error" to "Error loading models configuration. Using default model."
            }

            else -> "Unexpected Error" to "An unexpected error occurred loading models. Using default model."
        }

        logger.warn("$title: $content (Error: ${error.message})")
        NotificationUtil.showError(
            title = title,
            content = "$content\nError: ${error.message}"
        )
    }
}

class ModelConfigException(message: String, cause: Throwable? = null) : Exception(message, cause)