package com.canimussurdis.reachout.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Reads contacts from a .vcf (vCard) file, as exported by Google Contacts, iCloud, Outlook,
 * Samsung Contacts and others. Only names and phone numbers are used. Read-only: nothing is
 * written back to the file or to the phone's contacts.
 */
object VCard {

    private const val MAX_BYTES = 10L * 1024 * 1024

    fun read(input: InputStream): List<Contact> {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) throw IllegalArgumentException("That contacts file is too large.")
            out.write(buffer, 0, n)
        }
        return parse(out.toString(Charsets.UTF_8.name()))
    }

    fun parse(text: String): List<Contact> {
        // Unfold: a line starting with a space or tab continues the previous line.
        val lines = mutableListOf<String>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.lastIndex] = lines.last() + raw.substring(1)
            } else if (lines.isNotEmpty() && lines.last().endsWith("=") && lines.last().contains("QUOTED-PRINTABLE", true)) {
                // Quoted-printable soft line break (vCard 2.1)
                lines[lines.lastIndex] = lines.last().dropLast(1) + raw
            } else {
                lines += raw
            }
        }

        val contacts = mutableListOf<Contact>()
        var card: MutableList<Pair<Property, String>>? = null
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val prop = Property.parse(line.substring(0, colon))
            val value = line.substring(colon + 1)
            when {
                prop.name == "BEGIN" && value.equals("VCARD", true) -> card = mutableListOf()
                prop.name == "END" && value.equals("VCARD", true) -> {
                    card?.let { toContact(it) }?.let { contacts += it }
                    card = null
                }
                else -> card?.add(prop to value)
            }
        }
        return contacts.distinctBy { it.id }
    }

    private fun toContact(props: List<Pair<Property, String>>): Contact? {
        fun valueOf(name: String) = props.firstOrNull { it.first.name == name }?.let { decode(it.first, it.second) }

        val fullName = valueOf("FN")?.trim().orEmpty()
        val structured = valueOf("N")?.split(';')?.map { it.trim() }.orEmpty()
        val fromN = listOf(structured.getOrNull(3), structured.getOrNull(1), structured.getOrNull(2), structured.getOrNull(0), structured.getOrNull(4))
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
        val name = fullName.ifBlank { fromN }.ifBlank { valueOf("ORG")?.replace(';', ' ')?.trim().orEmpty() }
        if (name.isBlank()) return null

        val phones = props.filter { it.first.name == "TEL" }.mapNotNull { (p, v) ->
            val number = decode(p, v).removePrefix("tel:").trim()
            if (number.none { it.isDigit() }) return@mapNotNull null
            val types = p.types
            PhoneNumber(
                number = number,
                label = when {
                    "CELL" in types || "MOBILE" in types -> "Mobile"
                    "HOME" in types -> "Home"
                    "WORK" in types -> "Work"
                    else -> "Phone"
                },
                isDefault = "PREF" in types,
                isMobile = "CELL" in types || "MOBILE" in types,
            )
        }.distinctBy { p -> p.number.filter { it.isDigit() } }

        return Contact(id = stableId(name, phones), name = name, photoUri = null, phones = phones)
    }

    /** Same person in the same file always gets the same id, so importing twice doesn't duplicate. */
    private fun stableId(name: String, phones: List<PhoneNumber>): String {
        val key = name.lowercase() + "|" + phones.map { p -> p.number.filter { it.isDigit() } }.sorted().joinToString(",")
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        return "vcf-$hash"
    }

    private fun decode(p: Property, value: String): String {
        val raw = if (p.quotedPrintable) decodeQuotedPrintable(value, p.charset) else value
        return raw.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
    }

    private fun decodeQuotedPrintable(value: String, charset: String?): String {
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '=' && i + 2 < value.length) {
                val b = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (b != null) {
                    bytes.write(b)
                    i += 3
                    continue
                }
            }
            bytes.write(c.code and 0xFF)
            i++
        }
        val cs = runCatching { java.nio.charset.Charset.forName(charset ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        return String(bytes.toByteArray(), cs)
    }

    private class Property(val name: String, val types: Set<String>, val quotedPrintable: Boolean, val charset: String?) {
        companion object {
            fun parse(head: String): Property {
                val parts = head.split(';')
                val name = parts.first().substringAfterLast('.').uppercase() // drop "item1." groups
                val types = mutableSetOf<String>()
                var qp = false
                var charset: String? = null
                for (param in parts.drop(1)) {
                    val key = param.substringBefore('=').uppercase()
                    val values = if ('=' in param) param.substringAfter('=').trim('"') else param
                    when (key) {
                        "ENCODING" -> qp = values.equals("QUOTED-PRINTABLE", true)
                        "CHARSET" -> charset = values
                        "PREF" -> types += "PREF"
                        "TYPE" -> values.split(',').forEach { types += it.trim().uppercase() }
                        else -> if ('=' !in param) {
                            if (param.equals("QUOTED-PRINTABLE", true)) qp = true else types += param.uppercase()
                        }
                    }
                }
                return Property(name, types, qp, charset)
            }
        }
    }
}
