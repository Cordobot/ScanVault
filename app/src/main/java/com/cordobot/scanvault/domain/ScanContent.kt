package com.cordobot.scanvault.domain

import java.time.Instant

enum class CodeType(val label: String) {
    URL("Enlace"),
    WIFI("Red Wi-Fi"),
    CONTACT("Contacto"),
    EMAIL("Correo"),
    PHONE("Teléfono"),
    SMS("SMS"),
    GEO("Ubicación"),
    PRODUCT("Producto"),
    TEXT("Texto")
}

data class Scan(
    val id: Long,
    val rawValue: String,
    val format: String,
    val scannedAt: Instant,
    val isFavorite: Boolean
) {
    val content: ParsedContent get() = ContentParser.parse(rawValue)
}

data class ParsedContent(
    val type: CodeType,
    val title: String,
    val details: List<Pair<String, String>> = emptyList(),
    // What "Abrir" launches (a URL, tel:, mailto:, geo:…), null when there is nothing to open
    val openUri: String? = null
)

/**
 * Turns the raw text of a QR or barcode into something readable. Pure Kotlin so every format is
 * covered by unit tests; ML Kit only provides the raw text and the barcode format.
 */
object ContentParser {

    fun parse(raw: String): ParsedContent {
        val text = raw.trim()
        val upper = text.uppercase()
        return when {
            upper.startsWith("WIFI:") -> wifi(text)
            upper.startsWith("BEGIN:VCARD") -> vcard(text)
            upper.startsWith("MECARD:") -> mecard(text)
            upper.startsWith("MAILTO:") -> email(text.substring(7).substringBefore('?'))
            upper.startsWith("MATMSG:") -> email(field(text.substring(7), "TO") ?: "")
            upper.startsWith("TEL:") -> phone(text.substring(4))
            upper.startsWith("SMSTO:") || upper.startsWith("SMS:") -> sms(text.substringAfter(':'))
            upper.startsWith("GEO:") -> geo(text)
            upper.startsWith("HTTP://") || upper.startsWith("HTTPS://") -> url(text)
            WWW.matches(text) -> url("https://$text")
            EMAIL.matches(text) -> email(text)
            PRODUCT.matches(text) -> ParsedContent(CodeType.PRODUCT, text, listOf("Código" to text), "https://www.google.com/search?q=$text")
            else -> ParsedContent(CodeType.TEXT, text.lineSequence().first().take(80), listOf("Contenido" to text))
        }
    }

    private fun url(value: String): ParsedContent {
        val host = value.substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.")
        return ParsedContent(CodeType.URL, host, listOf("Dirección" to value), value)
    }

    private fun wifi(value: String): ParsedContent {
        val body = value.substring(5)
        val ssid = field(body, "S").orEmpty()
        val security = when (field(body, "T")?.uppercase()) {
            null, "", "NOPASS" -> "Abierta"
            "WEP" -> "WEP"
            else -> "WPA/WPA2"
        }
        val details = buildList {
            add("Red (SSID)" to ssid)
            add("Seguridad" to security)
            field(body, "P")?.takeIf { it.isNotEmpty() }?.let { add("Contraseña" to it) }
            if (field(body, "H").equals("true", ignoreCase = true)) add("Oculta" to "Sí")
        }
        return ParsedContent(CodeType.WIFI, ssid.ifEmpty { "Red Wi-Fi" }, details)
    }

    private fun vcard(value: String): ParsedContent {
        val lines = value.lines().map { it.trim() }
        fun line(key: String) = lines.firstOrNull { it.uppercase().startsWith(key) }?.substringAfter(':')?.takeIf { it.isNotBlank() }
        val name = line("FN") ?: line("N:")?.split(';')?.filter { it.isNotBlank() }?.reversed()?.joinToString(" ") ?: "Contacto"
        val details = listOfNotNull(
            "Nombre" to name,
            line("TEL")?.let { "Teléfono" to it },
            line("EMAIL")?.let { "Correo" to it },
            line("ORG")?.let { "Organización" to it },
            line("URL")?.let { "Web" to it }
        )
        return ParsedContent(CodeType.CONTACT, name, details)
    }

    private fun mecard(value: String): ParsedContent {
        val body = value.substring(7)
        val name = field(body, "N")?.split(',')?.reversed()?.joinToString(" ")?.trim() ?: "Contacto"
        val details = listOfNotNull(
            "Nombre" to name,
            field(body, "TEL")?.let { "Teléfono" to it },
            field(body, "EMAIL")?.let { "Correo" to it }
        )
        return ParsedContent(CodeType.CONTACT, name, details)
    }

    private fun email(address: String) =
        ParsedContent(CodeType.EMAIL, address, listOf("Correo" to address), "mailto:$address")

    private fun phone(number: String) =
        ParsedContent(CodeType.PHONE, number, listOf("Número" to number), "tel:$number")

    private fun sms(body: String): ParsedContent {
        val number = body.substringBefore(':')
        val message = body.substringAfter(':', "")
        val details = listOfNotNull("Número" to number, message.takeIf { it.isNotEmpty() }?.let { "Mensaje" to it })
        return ParsedContent(CodeType.SMS, number, details, "smsto:$number")
    }

    private fun geo(value: String): ParsedContent {
        val coordinates = value.substring(4).substringBefore('?')
        val (lat, lng) = coordinates.split(',').let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
        return ParsedContent(CodeType.GEO, "$lat, $lng", listOf("Latitud" to lat, "Longitud" to lng), value)
    }

    /** Value of `KEY:value;` in WIFI/MECARD payloads, honoring `\;` escapes. */
    private fun field(body: String, key: String): String? {
        var index = 0
        while (index < body.length) {
            val end = nextSeparator(body, index)
            val part = body.substring(index, end)
            if (part.startsWith("$key:", ignoreCase = true)) {
                return part.substring(key.length + 1).replace(Regex("\\\\([;,:\\\\\"])"), "$1")
            }
            index = end + 1
        }
        return null
    }

    private fun nextSeparator(body: String, from: Int): Int {
        var i = from
        while (i < body.length) {
            if (body[i] == '\\') i += 2 else if (body[i] == ';') return i else i++
        }
        return body.length
    }

    private val WWW = Regex("^www\\.[^\\s]+\\.[a-z]{2,}(/\\S*)?$", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[a-z]{2,}$", RegexOption.IGNORE_CASE)

    // EAN-8, UPC-A, EAN-13, ITF-14
    private val PRODUCT = Regex("^\\d{8}$|^\\d{12,14}$")
}
