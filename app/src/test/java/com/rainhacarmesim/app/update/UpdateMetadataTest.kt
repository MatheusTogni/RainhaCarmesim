package com.rainhacarmesim.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateMetadataTest {
    private val repo = "MatheusTogni/RainhaCarmesim"
    private val sha = "a".repeat(64)

    private fun json(apkUrl: String = "https://github.com/$repo/releases/download/v1.0.1/RainhaCarmesim-1.0.1.apk", hash: String = sha, code: Long = 10001) =
        """{"versionName":"1.0.1","versionCode":$code,"releaseNotes":"Correções","apkUrl":"$apkUrl","sha256":"$hash","publishedAt":"2026-11-24T12:00:00Z"}"""

    @Test fun parsesValidMetadata() {
        val info = UpdateMetadata.parse(json(), repo)
        assertEquals("1.0.1", info.versionName)
        assertEquals(10001L, info.versionCode)
        assertEquals("Correções", info.releaseNotes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsForeignUrl() {
        UpdateMetadata.parse(json(apkUrl = "https://evil.example.com/app.apk"), repo)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHttp() {
        UpdateMetadata.parse(json(apkUrl = "http://github.com/$repo/releases/download/v1/app.apk"), repo)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadHash() {
        UpdateMetadata.parse(json(hash = "xyz"), repo)
    }

    @Test fun versionComparison() {
        assertTrue(VersionComparator.isNewer(10001, 10000))
        assertFalse(VersionComparator.isNewer(10000, 10000))
        assertFalse(VersionComparator.isNewer(9999, 10000))
        assertEquals(10203L, VersionComparator.codeFromName("v1.2.3"))
        assertEquals(null, VersionComparator.codeFromName("1.2"))
    }

    @Test fun sha256OfKnownValue() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.of("abc".toByteArray()),
        )
    }
}
