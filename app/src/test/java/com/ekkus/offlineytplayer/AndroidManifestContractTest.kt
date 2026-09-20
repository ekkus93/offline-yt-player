package com.ekkus.offlineytplayer

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidManifestContractTest {
    @Test
    fun manifestDeclaresInternetPermission() {
        val manifest = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).firstOrNull(File::isFile) ?: error("Unable to locate app AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest)
        val permissions = document.getElementsByTagName("uses-permission")
        val names = (0 until permissions.length).map { index ->
            permissions.item(index).attributes
                .getNamedItemNS("http://schemas.android.com/apk/res/android", "name")
                ?.nodeValue
        }
        assertTrue(
            "Android app must retain INTERNET permission for source/network requests",
            "android.permission.INTERNET" in names,
        )
    }
}
