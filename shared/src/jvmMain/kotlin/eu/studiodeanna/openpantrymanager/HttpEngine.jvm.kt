package eu.studiodeanna.openpantrymanager

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.java.Java

internal actual val httpEngine: HttpClientEngineFactory<*> = Java
