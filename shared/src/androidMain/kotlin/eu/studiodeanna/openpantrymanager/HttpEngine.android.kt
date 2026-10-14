package eu.studiodeanna.openpantrymanager

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

internal actual val httpEngine: HttpClientEngineFactory<*> = OkHttp
