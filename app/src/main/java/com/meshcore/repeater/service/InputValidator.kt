package com.meshcore.repeater.service

import com.meshcore.repeater.model.FilterMode
import com.meshcore.repeater.model.FilterStats

/**
 * InputValidator – zentrale Validierungs- und Sanitizer-Klasse.
 *
 * Schützt alle CLI-Eingaben vor Injection-Versuchen durch Steuerzeichen
 * (\r, \n etc.) und validiert Eingaben per Regex & Wertebereich.
 */
object InputValidator {

    // === Sanitizer ============================================================

    /**
     * Entfernt alle Steuerzeichen (inkl. CR, LF) aus einem String.
     * Muss vor jedem CLI-Befehl aufgerufen werden.
     */
    fun sanitize(input: String): String =
        input.replace(Regex("[\r\n\t\\p{Cntrl}]"), "").trim()

    /**
     * Sanitize + Längenbegrenzung kombiniert.
     */
    fun sanitize(input: String, maxLength: Int): String =
        sanitize(input).take(maxLength)

    // === Frequenz & Funk-Parameter ============================================

    /**
     * EU868 Frequenzbereich (Repeater-relevante Bänder):
     * 863.000–870.000 MHz
     */
    fun validateFrequencyMhz(value: Double): ValidationResult {
        return when {
            value < 100.0 || value > 1000.0 -> ValidationResult.Error("Frequency must be between 100 and 1000 MHz")
            value < 863.0 || value > 870.0 -> ValidationResult.Warning("Outside EU868 band (863–870 MHz). Check local regulations.")
            else -> ValidationResult.Ok
        }
    }

    /** Untergrenze von "set tx" (SX1262: -9 dBm). */
    const val MIN_TX_DBM = -9.0

    /**
     * TX-Leistung: [MIN_TX_DBM] bis [maxDbm]. [maxDbm] kommt aus dem aktiven Board-Profil
     * (RadioAntennaConfig.maxTxInputDbm), damit UI, Speichern und Validierung dieselbe Grenze nutzen.
     */
    fun validateTxPowerDbm(value: Double, maxDbm: Double = 30.0): ValidationResult {
        return when {
            value < MIN_TX_DBM || value > maxDbm -> ValidationResult.Error("TX power must be between ${MIN_TX_DBM.toInt()} and ${maxDbm.toInt()} dBm")
            else -> ValidationResult.Ok
        }
    }

    // === Identität & Standort =================================================

    /** Breitengrad als Dezimalzahl (Punkt oder Komma) im Bereich -90..90. */
    fun isValidLatitude(input: String): Boolean =
        input.trim().replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it in -90.0..90.0 } == true

    /** Längengrad als Dezimalzahl (Punkt oder Komma) im Bereich -180..180. */
    fun isValidLongitude(input: String): Boolean =
        input.trim().replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it in -180.0..180.0 } == true

    /** Länge des MeshCore-Private-Keys in Hex-Zeichen (PRV_KEY_SIZE = 64 Byte). */
    const val PRIVATE_KEY_HEX_LEN = 128
    private val PRIVATE_KEY_HEX = Regex("^[0-9a-fA-F]{$PRIVATE_KEY_HEX_LEN}$")

    /** Private Key für "set prv.key": exakt 64 Byte = 128 Hex-Zeichen, nur 0-9/a-f. */
    fun isValidPrivateKeyHex(input: String): Boolean = PRIVATE_KEY_HEX.matches(input.trim())

    /**
     * Spreading Factor: 6–12
     */
    fun validateSpreadingFactor(value: Int): ValidationResult =
        if (value in 6..12) ValidationResult.Ok
        else ValidationResult.Error("Spreading Factor must be 6–12")

    /**
     * Bandbreite: Typische LoRa-Werte in kHz
     */
    private val validBandwidths = setOf(7.8, 10.4, 15.6, 20.8, 31.25, 41.7, 62.5, 125.0, 250.0, 500.0)
    fun validateBandwidthKhz(value: Double): ValidationResult =
        if (validBandwidths.any { kotlin.math.abs(it - value) < 0.1 }) ValidationResult.Ok
        else ValidationResult.Warning("Bandwidth ${value} kHz is not a standard LoRa value")

    // === Filter-Eingaben ======================================================

    /**
     * Pfad-Hex-Präfix: 2–8 Hex-Ziffern, gerade Anzahl (1–4 vollständige Bytes)
     * Regex: ^[0-9A-Fa-f]{2,8}$
     */
    private val HEX_PATH_REGEX = Regex("^[0-9A-Fa-f]{2,8}$")
    fun validateHexPathPrefix(input: String): ValidationResult {
        val s = sanitize(input)
        return when {
            s.isEmpty() -> ValidationResult.Error("Path prefix must not be empty")
            s.length % 2 != 0 -> ValidationResult.Error("Path prefix must have an even number of hex digits (2, 4, 6 or 8)")
            !s.matches(HEX_PATH_REGEX) -> ValidationResult.Error("Path prefix must be 2–8 hex digits (0-9, A-F only)")
            else -> ValidationResult.Ok
        }
    }

    /**
     * Kanalname: Optionales '#' gefolgt von 1–31 Zeichen (a-z, A-Z, 0-9, _, -)
     * Oder 'Public' exakt
     * Regex: ^#?[A-Za-z0-9_-]{1,31}$
     */
    private val CHANNEL_NAME_REGEX = Regex("^#?[A-Za-z0-9_-]{1,31}$")
    fun validateChannelName(input: String): ValidationResult {
        val s = sanitize(input)
        return when {
            s.isEmpty() -> ValidationResult.Error("Channel name must not be empty")
            s.equals("Public", ignoreCase = true) -> ValidationResult.Ok
            !s.matches(CHANNEL_NAME_REGEX) -> ValidationResult.Error("Channel name: use #name or 'Public', max 31 chars (a-z, 0-9, _, -)")
            else -> ValidationResult.Ok
        }
    }

    /**
     * Filter-Regel-Pattern: 1–63 Zeichen, keine Steuerzeichen
     */
    fun validateFilterPattern(input: String): ValidationResult {
        val s = sanitize(input)
        return when {
            s.isEmpty() -> ValidationResult.Error("Pattern must not be empty")
            s.length > 63 -> ValidationResult.Error("Pattern too long (max 63 characters)")
            else -> ValidationResult.Ok
        }
    }

    /**
     * Drop-Wahrscheinlichkeit: 1–100 %
     */
    fun validateDropPercent(value: Int): ValidationResult =
        if (value in 1..100) ValidationResult.Ok
        else ValidationResult.Error("Drop probability must be 1–100 %")

    /**
     * Timeout-Sekunden: 0–65535 (0 = permanent)
     */
    fun validateTimeoutSecs(value: Int): ValidationResult =
        if (value in 0..65535) ValidationResult.Ok
        else ValidationResult.Error("Timeout must be 0–65535 seconds (0 = permanent)")

    /**
     * Hop-Limit: 0–64
     */
    fun validateHopLimit(value: Int): ValidationResult =
        if (value in 0..64) ValidationResult.Ok
        else ValidationResult.Error("Hop limit must be 0–64")

    /**
     * Altersgrenze: 0 (off) oder 1–10080 (1 Woche in Minuten)
     */
    fun validateAgeMins(value: Int): ValidationResult =
        if (value == 0 || value in 1..10080) ValidationResult.Ok
        else ValidationResult.Error("Age must be 0 (off) or 1–10080 minutes")

    // === Allgemeine Hilfsmethoden =============================================

    /**
     * Stellt sicher, dass ein CLI-Befehl-String sicher ist.
     * Blockiert Multi-Command-Injection über \r\n.
     */
    fun sanitizeCliArg(input: String, maxLength: Int = 64): String =
        sanitize(input, maxLength)
}

/** Validierungsergebnis mit optionaler Fehlermeldung */
sealed class ValidationResult {
    object Ok : ValidationResult()
    data class Warning(val message: String) : ValidationResult()
    data class Error(val message: String) : ValidationResult()

    val isOk: Boolean get() = this is Ok || this is Warning
    val isError: Boolean get() = this is Error
    val messageOrNull: String? get() = when (this) {
        is Ok -> null
        is Warning -> message
        is Error -> message
    }
}
