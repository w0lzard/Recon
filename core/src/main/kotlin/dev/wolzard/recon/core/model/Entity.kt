package dev.wolzard.recon.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EntityType(val wire: String) {
    @SerialName("repo") REPO("repo"),
    @SerialName("app") APP("app"),
    @SerialName("product") PRODUCT("product"),
    @SerialName("book") BOOK("book"),
    @SerialName("person") PERSON("person"),
    @SerialName("place") PLACE("place"),
    @SerialName("recipe") RECIPE("recipe"),
    @SerialName("claim") CLAIM("claim"),
    @SerialName("other") OTHER("other");

    companion object {
        fun fromWire(s: String?): EntityType = entries.firstOrNull { it.wire == s?.trim()?.lowercase() } ?: OTHER
    }
}

@Serializable
enum class Evidence(val wire: String) {
    @SerialName("spoken") SPOKEN("spoken"),
    @SerialName("on-screen") ON_SCREEN("on-screen"),
    @SerialName("inferred") INFERRED("inferred");

    companion object {
        // Unknown evidence is treated as the weakest kind, so it gets the "Guess" badge.
        fun fromWire(s: String?): Evidence = entries.firstOrNull { it.wire == s?.trim()?.lowercase() } ?: INFERRED
    }
}

@Serializable
data class Entity(
    val type: EntityType,
    val name: String,          // repo: repo name; claim: the claim as one sentence
    val qualifier: String?,    // repo: owner; book: author; product/app: brand/developer; place: city/country; else null
    val evidence: Evidence,
    val saidInVideo: String,   // one sentence: what the video says about it (device language)
)
