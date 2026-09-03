package com.clashfit.engine.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Challenge card structure. */
@Serializable
data class ChallengeCard(
    val kind: String,
    val exerciseId: String,
    val mode: String,
    val name: String?,
    val target: Int?,
    val ghost: GhostDataPayload?,
) {
    @Serializable
    data class GhostDataPayload(
        val events: List<GhostEventPayload> = emptyList(),
    ) {
        @Serializable
        data class GhostEventPayload(
            val t: Long,
            val damage: Int,
        )
    }
}

/** Challenge codec — encode/decode CF1 challenge codes. */
object ChallengeCodec {
    private const val PREFIX = "CF1:"
    private const val B36 = 36

    /** The wire shape. Short keys keep the code short; the checksum keeps it honest. */
    @Serializable
    private data class Wire(
        val v: Int = 1,
        val k: String,
        val e: String,
        val m: String,
        val n: String = "",
        val t: Int? = null,
        val g: String = "",
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Encode a challenge card into a short shareable code. */
    fun encode(card: ChallengeCard): String {
        val wire = Wire(
            k = card.kind, e = card.exerciseId, m = card.mode,
            n = (card.name ?: "").take(24), t = card.target,
            g = packEvents(card.ghost?.events ?: emptyList()),
        )
        val body = json.encodeToString(Wire.serializer(), wire)
        return PREFIX + toB64(body) + "." + checksum(body)
    }

    /** Decode a challenge code into a card. Throws [IllegalArgumentException] with a friendly message. */
    fun decode(code: String): ChallengeCard {
        val raw = code.trim()
        if (!raw.startsWith(PREFIX)) throw IllegalArgumentException("Not a ClashFit challenge code.")
        val rest = raw.drop(PREFIX.length)
        val dot = rest.lastIndexOf('.')
        if (dot < 0) throw IllegalArgumentException("Challenge code is incomplete.")
        val body = runCatching { fromB64(rest.substring(0, dot)) }
            .getOrElse { throw IllegalArgumentException("Challenge code is damaged.") }
        if (checksum(body) != rest.substring(dot + 1)) {
            throw IllegalArgumentException("Challenge code is damaged — probably truncated in transit.")
        }
        val p = runCatching { json.decodeFromString(Wire.serializer(), body) }
            .getOrElse { throw IllegalArgumentException("Challenge code did not read.") }
        if (p.v != 1) throw IllegalArgumentException("Challenge code is version ${p.v}, this build reads version 1.")
        return ChallengeCard(
            kind = p.k.ifEmpty { "GHOST" },
            exerciseId = p.e.ifEmpty { "squat" },
            mode = p.m.ifEmpty { "GHOST_RACE" },
            name = p.n.takeIf { it.isNotEmpty() },
            target = p.t,
            ghost = p.g.takeIf { it.isNotEmpty() }?.let { packed ->
                ChallengeCard.GhostDataPayload(
                    events = unpackEvents(packed).map { ChallengeCard.GhostDataPayload.GhostEventPayload(it.t, it.damage) },
                )
            },
        )
    }

    private fun toB64(str: String): String {
        val bytes = str.toByteArray(Charsets.UTF_8)
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return b64
    }

    private fun fromB64(s: String): String {
        val padded = s.replace('-', '+').replace('_', '/')
        val padding = (4 - (padded.length % 4)) % 4
        val withPadding = padded + "=".repeat(padding)
        val bytes = java.util.Base64.getUrlDecoder().decode(withPadding)
        return String(bytes, Charsets.UTF_8)
    }

    private fun checksum(s: String): String {
        var h = 5381
        for (c in s) {
            h = ((h * 33) xor c.code) and 0xFFFFFFFF.toInt()
        }
        return h.toString(B36)
    }

    private fun packEvents(events: List<ChallengeCard.GhostDataPayload.GhostEventPayload>): String {
        if (events.isEmpty()) return ""
        var last = 0L
        return events.map { e ->
            val dt = maxOf(0, e.t - last)
            last = e.t
            "${dt.toString(B36)}.${e.damage.toString(B36)}"
        }.joinToString("~")
    }

    private data class PackedEvent(val t: Long, val damage: Int)

    private fun unpackEvents(packed: String): List<PackedEvent> {
        if (packed.isEmpty()) return emptyList()
        var t = 0L
        return packed.split("~").filter { it.isNotEmpty() }.map { chunk ->
            val parts = chunk.split(".")
            val dt = parts.getOrNull(0)?.toLong(B36) ?: 0L
            t += dt
            PackedEvent(t, parts.getOrNull(1)?.toInt(B36) ?: 0)
        }
    }
}
