package com.cordobot.scanvault

import com.cordobot.scanvault.domain.CodeType
import com.cordobot.scanvault.domain.ContentParser
import com.cordobot.scanvault.domain.FilterHistoryUseCase
import com.cordobot.scanvault.domain.SaveScanUseCase
import com.cordobot.scanvault.domain.Scan
import com.cordobot.scanvault.domain.ScanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ContentParserTest {

    @Test
    fun `wifi with escaped characters`() {
        val content = ContentParser.parse("WIFI:T:WPA;S:Casa\\;Adrián;P:cl@ve\\;123;H:false;;")

        assertEquals(CodeType.WIFI, content.type)
        assertEquals("Casa;Adrián", content.title)
        assertEquals("WPA/WPA2", content.details.toMap()["Seguridad"])
        assertEquals("cl@ve;123", content.details.toMap()["Contraseña"])
    }

    @Test
    fun `open wifi has no password`() {
        val content = ContentParser.parse("WIFI:S:Cafe;T:nopass;;")
        assertEquals("Abierta", content.details.toMap()["Seguridad"])
        assertNull(content.details.toMap()["Contraseña"])
    }

    @Test
    fun `urls with and without scheme`() {
        val full = ContentParser.parse("https://www.github.com/Cordobot")
        assertEquals("github.com", full.title)
        assertEquals("https://www.github.com/Cordobot", full.openUri)
        val bare = ContentParser.parse("www.example.com/menu")
        assertEquals(CodeType.URL, bare.type)
        assertEquals("https://www.example.com/menu", bare.openUri)
    }

    @Test
    fun `vcard and mecard contacts`() {
        val vcard = ContentParser.parse("BEGIN:VCARD\nVERSION:3.0\nN:Alvarez;Adrián\nTEL:+573001234567\nEMAIL:a@b.co\nEND:VCARD")
        assertEquals(CodeType.CONTACT, vcard.type)
        assertEquals("Adrián Alvarez", vcard.title)
        assertEquals("+573001234567", vcard.details.toMap()["Teléfono"])

        val mecard = ContentParser.parse("MECARD:N:Pérez,Ana;TEL:123;EMAIL:ana@x.com;;")
        assertEquals("Ana Pérez", mecard.title)
    }

    @Test
    fun `phone, sms, email, geo and products`() {
        assertEquals("tel:+57300", ContentParser.parse("tel:+57300").openUri)
        assertEquals(listOf("Número" to "+57300", "Mensaje" to "Hola"), ContentParser.parse("SMSTO:+57300:Hola").details)
        assertEquals(CodeType.EMAIL, ContentParser.parse("hola@cordobot.dev").type)
        assertEquals("6.2442, -75.5812", ContentParser.parse("geo:6.2442,-75.5812").title)
        assertEquals(CodeType.PRODUCT, ContentParser.parse("7702004003508").type)
        assertEquals(CodeType.TEXT, ContentParser.parse("Mesa 12").type)
    }
}

class UseCaseTest {

    private class FakeRepository : ScanRepository {
        val scans = mutableListOf<Scan>()
        override fun observeHistory(): Flow<List<Scan>> = flowOf(scans)
        override fun observeScan(id: Long): Flow<Scan?> = flowOf(scans.find { it.id == id })
        override suspend fun latestWithValue(rawValue: String) = scans.filter { it.rawValue == rawValue }.maxByOrNull { it.scannedAt }
        override suspend fun insert(rawValue: String, format: String, scannedAtMillis: Long): Long {
            val id = scans.size + 1L
            scans += Scan(id, rawValue, format, Instant.ofEpochMilli(scannedAtMillis), false)
            return id
        }
        override suspend fun toggleFavorite(id: Long) = Unit
        override suspend fun delete(id: Long) = Unit
    }

    @Test
    fun `the same code read twice in a few seconds is stored once`() = runTest {
        val repository = FakeRepository()
        val save = SaveScanUseCase(repository)

        val first = save("https://a.co", "QR", now = 1_000)
        val again = save("https://a.co", "QR", now = 4_000)
        val later = save("https://a.co", "QR", now = 1_000 + SaveScanUseCase.DUPLICATE_WINDOW_MS + 1)

        assertEquals(first, again)
        assertEquals(2, repository.scans.size)
        assertEquals(2L, later)
    }

    @Test
    fun `history search matches parsed titles and favorites`() {
        val scans = listOf(
            Scan(1, "WIFI:S:Oficina;T:WPA;P:x;;", "QR", Instant.EPOCH, isFavorite = true),
            Scan(2, "https://github.com", "QR", Instant.EPOCH, isFavorite = false)
        )
        val filter = FilterHistoryUseCase()

        assertEquals(listOf(1L), filter(scans, "oficina", false).map { it.id })
        assertEquals(listOf(1L), filter(scans, "", true).map { it.id })
        assertEquals(listOf(2L), filter(scans, "github", false).map { it.id })
    }
}
