package com.meshcore.repeater.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale

/**
 * Laufzeit-Übersetzung für die im Code stehenden (deutschen) UI-Texte.
 * Schlüssel = deutscher Originaltext mit Platzhaltern {0}, {1} …; Übersetzungen EN/NL/FR in assets/i18n.json.
 * [language] ist Compose-State: jeder Text, der in der Komposition über [tr] gelesen wird, aktualisiert sich
 * beim Sprachwechsel im Seitenmenü sofort.
 */
/** Reine Sprachcode-Normalisierung (ohne Compose-State, daher JVM-testbar). */
object LanguageCode {
    val SUPPORTED = listOf("de", "en", "nl", "fr")

    /**
     * Effektive 2-Buchstaben-App-Sprache: Nutzerwahl, sonst Systemsprache.
     * Leer, "system" oder ein vollständiges Locale ("de-DE", "de_DE") werden auf "de|en|nl|fr" normalisiert;
     * nicht unterstützte Sprachen fallen auf "en" zurück.
     */
    fun effective(selected: String?, systemLang: String): String {
        val sel = selected?.trim().orEmpty().takeUnless { it.equals("system", ignoreCase = true) }.orEmpty()
        return sel.ifBlank { systemLang }.lowercase(Locale.ROOT).take(2).let { if (it in SUPPORTED) it else "en" }
    }
}

object I18n {
    /** "de", "en", "nl" oder "fr" */
    var language: String by mutableStateOf(systemLanguage())
    private var table: Map<String, Array<String>> = emptyMap()   // de -> [en, nl, fr]

    fun systemLanguage(): String = LanguageCode.effective("", Locale.getDefault().language)

    /** Siehe [LanguageCode.effective]. */
    fun effectiveLanguage(selected: String?, systemLang: String = Locale.getDefault().language): String = LanguageCode.effective(selected, systemLang)

    fun init(context: Context) {
        if (table.isNotEmpty()) return
        table = try {
            val json = context.assets.open("i18n.json").use { it.readBytes().toString(Charsets.UTF_8) }
            val o = JSONObject(json)
            buildMap {
                for (k in o.keys()) {
                    val a = o.getJSONArray(k)
                    put(k, Array(3) { i -> a.optString(i, k) })
                }
            }
        } catch (_: Exception) { emptyMap() }
    }

    fun translate(de: String): String {
        val idx = when (language) { "en" -> 0; "nl" -> 1; "fr" -> 2; else -> return de }
        return table[de]?.get(idx)?.takeIf { it.isNotEmpty() } ?: de
    }
}

/** Übersetzt [de] in die aktive App-Sprache und setzt {0}, {1} … ein. Unbekannte Texte bleiben unverändert. */
fun tr(de: String, vararg args: Any?): String {
    var s = I18n.translate(de)
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a?.toString() ?: "") }
    return s
}

/** Context mit lokalisierten Ressourcen, der die Activity als Basis behält (Activity-Zugriff bleibt über [findActivity] möglich). */
class LocalizedContextWrapper(base: Context, private val localizedResources: android.content.res.Resources) : android.content.ContextWrapper(base) {
    override fun getResources(): android.content.res.Resources = localizedResources
}

fun Context.findActivity(): android.app.Activity? {
    var c: Context? = this
    while (c != null) {
        if (c is android.app.Activity) return c
        c = (c as? android.content.ContextWrapper)?.baseContext
    }
    return null
}
