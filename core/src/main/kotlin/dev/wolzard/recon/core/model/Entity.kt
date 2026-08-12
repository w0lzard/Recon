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
    @SerialName("media") MEDIA("media"),   // movie, show or song (D23)
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

/** D24: what a claim is about. Health, legal and financial claims need a named source to be anything but Unclear. */
@Serializable
enum class ClaimDomain(val wire: String, val sensitive: Boolean) {
    @SerialName("health") HEALTH("health", true),
    @SerialName("legal") LEGAL("legal", true),
    @SerialName("financial") FINANCIAL("financial", true),
    @SerialName("other") OTHER("other", false);

    companion object {
        fun fromWire(s: String?): ClaimDomain? = s?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "null" }
            ?.let { w -> entries.firstOrNull { it.wire == w } ?: OTHER }
    }
}

/** D26: what kind of media a media entity or answer is. Model-tagged; only these wire words are accepted. */
@Serializable
enum class MediaType(val wire: String) {
    @SerialName("movie") MOVIE("movie"),
    @SerialName("series") SERIES("series"),
    @SerialName("song") SONG("song"),
    @SerialName("game") GAME("game"),
    @SerialName("anime") ANIME("anime"),
    @SerialName("other") OTHER("other");

    companion object {
        /** Strict: anything not on the list is null, never a guess. */
        fun fromWire(s: String?): MediaType? = s?.trim()?.lowercase()?.let { w -> entries.firstOrNull { it.wire == w } }
    }
}

@Serializable
data class Entity(
    val type: EntityType,
    val name: String,          // repo: repo name; claim: the claim as one sentence
    val qualifier: String?,    // repo: owner; book: author; product/app: brand/developer; place: city/country; else null
    val evidence: Evidence,
    val saidInVideo: String,   // one sentence: what the video says about it (device language)
    val domain: ClaimDomain? = null,   // claims only (D24)
    val subtype: MediaType? = null,    // media only, allow-listed (D26)
)
