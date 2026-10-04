package com.kaos.tvappmanager.data.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `semver 1 5 0 is newer than 1 4 12`() {
        assertEquals(
            VersionComparator.Order.NEWER,
            VersionComparator.compare("1.5.0", "1.4.12", "semver"),
        )
    }

    @Test
    fun `semver strips leading v`() {
        assertEquals(
            VersionComparator.Order.NEWER,
            VersionComparator.compare("v2.0.0", "1.9.9", "semver"),
        )
    }

    @Test
    fun `semver equal ignoring trailing zero segments`() {
        assertEquals(
            VersionComparator.Order.SAME,
            VersionComparator.compare("1.0", "1.0.0", "semver"),
        )
    }

    @Test
    fun `semver shorter patch is older`() {
        assertEquals(
            VersionComparator.Order.OLDER,
            VersionComparator.compare("1.4.12", "1.5.0", "semver"),
        )
    }

    @Test
    fun `semver returns UNKNOWN for unparseable values`() {
        assertEquals(
            VersionComparator.Order.UNKNOWN,
            VersionComparator.compare("not-a-version", "also-not", "semver"),
        )
    }

    @Test
    fun `semver compares numeric segments numerically not lexically`() {
        assertEquals(
            VersionComparator.Order.NEWER,
            VersionComparator.compare("3.10.0", "3.9.0", "semver"),
        )
    }

    @Test
    fun `raw mode compares zero padded versions in order`() {
        assertEquals(
            VersionComparator.Order.NEWER,
            VersionComparator.compare("25.10.01", "25.09.30", "raw"),
        )
        assertEquals(
            VersionComparator.Order.SAME,
            VersionComparator.compare("25.04.06.B", "25.04.06.B", "raw"),
        )
    }

    @Test
    fun `missing installed version counts as update`() {
        assertEquals(
            VersionComparator.Order.NEWER,
            VersionComparator.compare("1.0.0", null, "semver"),
        )
    }

    @Test
    fun `unknown version is not reported as newer`() {
        assertEquals(
            VersionComparator.Order.UNKNOWN,
            VersionComparator.compare("garbage", "1.0.0", "semver"),
        )
    }
}

class ManifestParserTest {

    private val valid = """
        {
          "schemaVersion": 1,
          "apps": [
            {
              "name": "Media Player TV",
              "package": "com.example.mediaplayer",
              "version": "1.5.0",
              "apkUrl": "https://example.com/a.apk",
              "iconUrl": "https://example.com/icon.png",
              "sha256": "a1b2c3d4",
              "versionMode": "semver"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses a valid manifest`() {
        val result = ManifestParser.parse(valid)
        assertNull(result.error)
        val manifest = result.manifest
        assertNotNull(manifest)
        assertEquals(1, manifest!!.schemaVersion)
        assertEquals(1, manifest.apps.size)
        assertEquals("com.example.mediaplayer", manifest.apps[0].packageName)
        assertEquals(0, result.warnings.size)
    }

    @Test
    fun `rejects unknown schema version`() {
        val result = ManifestParser.parse("""{"schemaVersion": 99, "apps": []}""")
        assertNull(result.manifest)
        assertNotNull(result.error)
    }

    @Test
    fun `rejects malformed json`() {
        val result = ManifestParser.parse("{not json")
        assertNull(result.manifest)
        assertNotNull(result.error)
    }

    @Test
    fun `drops entry whose apk url is not https but keeps the rest`() {
        val raw = """
            {
              "schemaVersion": 1,
              "apps": [
                {"name":"Good","package":"com.a","version":"1.0","apkUrl":"https://ok.com/a.apk"},
                {"name":"Bad","package":"com.b","version":"1.0","apkUrl":"http://insecure.com/b.apk"}
              ]
            }
        """.trimIndent()
        val result = ManifestParser.parse(raw)
        assertNull(result.error)
        val names = result.manifest!!.apps.map { it.name }
        assertEquals(listOf("Good"), names)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `drops entry whose icon url is not https`() {
        val raw = """
            {
              "schemaVersion": 1,
              "apps": [
                {"name":"NoIcon","package":"com.a","version":"1.0","apkUrl":"https://ok.com/a.apk"},
                {"name":"BadIcon","package":"com.b","version":"1.0","apkUrl":"https://ok.com/b.apk","iconUrl":"http://bad/icon.png"}
              ]
            }
        """.trimIndent()
        val result = ManifestParser.parse(raw)
        assertNull(result.error)
        assertEquals(listOf("NoIcon"), result.manifest!!.apps.map { it.name })
    }

    @Test
    fun `allows missing iconUrl`() {
        val raw = """{"schemaVersion":1,"apps":[{"name":"A","package":"com.a","version":"1.0","apkUrl":"https://x.com/a.apk"}]}"""
        val result = ManifestParser.parse(raw)
        assertNull(result.error)
        assertNull(result.manifest!!.apps[0].iconUrl)
    }

    @Test
    fun `drops entries missing required fields`() {
        val raw = """
            {
              "schemaVersion": 1,
              "apps": [
                {"name":"","package":"com.a","version":"1.0","apkUrl":"https://x.com/a.apk"},
                {"name":"NoPkg","package":"","version":"1.0","apkUrl":"https://x.com/a.apk"},
                {"name":"NoVer","package":"com.c","version":"","apkUrl":"https://x.com/c.apk"},
                {"name":"OK","package":"com.d","version":"1.0","apkUrl":"https://x.com/d.apk"}
              ]
            }
        """.trimIndent()
        val result = ManifestParser.parse(raw)
        assertNull(result.error)
        assertEquals(listOf("OK"), result.manifest!!.apps.map { it.name })
        assertEquals(3, result.warnings.size)
    }

    @Test
    fun `drops duplicate package keeping first`() {
        val raw = """
            {
              "schemaVersion": 1,
              "apps": [
                {"name":"First","package":"com.a","version":"1.0","apkUrl":"https://x.com/1.apk"},
                {"name":"Second","package":"com.a","version":"2.0","apkUrl":"https://x.com/2.apk"}
              ]
            }
        """.trimIndent()
        val result = ManifestParser.parse(raw)
        assertNull(result.error)
        assertEquals(listOf("First"), result.manifest!!.apps.map { it.name })
    }

    @Test
    fun `keeps empty app list as a valid manifest`() {
        val result = ManifestParser.parse("""{"schemaVersion":1,"apps":[]}""")
        assertNull(result.error)
        assertEquals(0, result.manifest!!.apps.size)
    }

    @Test
    fun `isHttps rejects plain http and bare host`() {
        assertEquals(false, ManifestParser.isHttps("http://a.com/x"))
        assertEquals(true, ManifestParser.isHttps("https://a.com/x"))
        assertEquals(false, ManifestParser.isHttps("https://"))
        assertEquals(false, ManifestParser.isHttps("file:///sdcard/a.json"))
    }
}
