package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.FeatureStates

/** The part of the backend's REST API for the switches under "Verwaltung"; [KtorBackendClient] implements it. */
interface FeatureBackend {
    suspend fun features(): FeatureStates
}
