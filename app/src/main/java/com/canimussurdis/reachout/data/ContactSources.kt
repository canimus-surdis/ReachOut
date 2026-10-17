package com.canimussurdis.reachout.data

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts

/** Where swipe cards come from: your real phone contacts, or the demo list. */
interface ContactSource {
    fun load(): List<Contact>
}

/**
 * Reads the phone's contacts. Read-only: it only ever runs queries, and the app
 * only holds READ_CONTACTS, so Android itself blocks any write.
 * Throws SecurityException if contacts permission hasn't been granted yet.
 */
class PhoneContactsSource(private val context: Context) : ContactSource {

    override fun load(): List<Contact> {
        val resolver = context.contentResolver
        val res = context.resources

        // 1. All phone numbers, grouped by contact.
        val phonesByContact = HashMap<Long, MutableList<PhoneNumber>>()
        resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.IS_SUPER_PRIMARY),
            null, null, null,
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(Phone.CONTACT_ID)
            val iNumber = c.getColumnIndexOrThrow(Phone.NUMBER)
            val iType = c.getColumnIndexOrThrow(Phone.TYPE)
            val iLabel = c.getColumnIndexOrThrow(Phone.LABEL)
            val iPrimary = c.getColumnIndexOrThrow(Phone.IS_SUPER_PRIMARY)
            while (c.moveToNext()) {
                val number = c.getString(iNumber)?.trim()
                if (number.isNullOrEmpty()) continue
                val type = c.getInt(iType)
                val label = Phone.getTypeLabel(res, type, c.getString(iLabel)).toString()
                phonesByContact.getOrPut(c.getLong(iId)) { mutableListOf() }.add(
                    PhoneNumber(
                        number = number,
                        label = label,
                        isDefault = c.getInt(iPrimary) != 0,
                        isMobile = type == Phone.TYPE_MOBILE,
                    )
                )
            }
        }

        // 2. All contacts with a name.
        val contacts = mutableListOf<Contact>()
        resolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_URI),
            null, null, null,
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(Contacts._ID)
            val iLookup = c.getColumnIndexOrThrow(Contacts.LOOKUP_KEY)
            val iName = c.getColumnIndexOrThrow(Contacts.DISPLAY_NAME_PRIMARY)
            val iPhoto = c.getColumnIndexOrThrow(Contacts.PHOTO_URI)
            while (c.moveToNext()) {
                val name = c.getString(iName)?.trim()
                if (name.isNullOrEmpty()) continue
                val lookupKey = c.getString(iLookup) ?: continue
                val phones = phonesByContact[c.getLong(iId)].orEmpty()
                    .distinctBy { p -> p.number.filter { it.isDigit() } }
                contacts += Contact(
                    id = lookupKey,
                    name = name,
                    photoUri = c.getString(iPhoto),
                    phones = phones,
                )
            }
        }
        return contacts
    }
}

/** 20 made-up people for testing. Numbers use the 555-01xx range reserved for fiction. */
class DemoContactsSource(private val context: Context) : ContactSource {

    override fun load(): List<Contact> {
        // A plain image file (the app icon itself is a vector drawing, which can't be loaded as a photo).
        val photo = "android.resource://${context.packageName}/drawable/demo_photo"
        return NAMES.mapIndexed { i, name ->
            val number = "(555) 555-01%02d".format(i + 1)
            val phones = when (i) {
                3 -> emptyList() // "Al" has no number: Call and Text should gray out.
                5 -> listOf( // Several numbers: the default one should win.
                    PhoneNumber("(555) 555-0150", "Home", isDefault = false, isMobile = false),
                    PhoneNumber(number, "Mobile", isDefault = false, isMobile = true),
                    PhoneNumber("(555) 555-0160", "Work", isDefault = true, isMobile = false),
                )
                9 -> listOf(PhoneNumber(number, "Home", isDefault = false, isMobile = false))
                else -> listOf(PhoneNumber(number, "Mobile", isDefault = false, isMobile = true))
            }
            Contact(
                id = "demo-$i",
                name = name,
                photoUri = if (i % 4 == 0) photo else null,
                phones = phones,
            )
        }
    }

    private companion object {
        val NAMES = listOf(
            "Ava Thompson", "Marcus Bell", "Priya Raman", "Al",
            "Maximiliana Featherstonehaugh-Worthington", "Diego Alvarez", "Hannah Kim",
            "Theo Okafor", "Grandma Rose", "June Park", "Sam Rivera", "Olivia Chen",
            "Ben Carter", "Zoe Nakamura", "Coach Dan", "Lena Fischer", "Jamal Wright",
            "Mia Rossi", "Noah Patel", "Aunt Bea",
        )
    }
}
