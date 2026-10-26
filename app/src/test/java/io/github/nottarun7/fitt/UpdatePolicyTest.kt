package io.github.nottarun7.fitt

import io.github.nottarun7.fitt.updates.*
import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val stable = "a".repeat(64)
    private val legacy = "b".repeat(64)
    private val manifest = UpdateManifest(1, "io.github.nottarun7.fitt", 5, "0.1.3", 26, "v0.1.3", "Improvements", listOf(
        UpdateAsset("fitt.apk", "c".repeat(64), stable), UpdateAsset("fitt-legacy.apk", "d".repeat(64), legacy)))
    private fun json() = """{"schemaVersion":1,"packageName":"io.github.nottarun7.fitt","versionCode":5,"versionName":"0.1.3","minSdk":26,"tag":"v0.1.3","notes":"Updates","assets":[{"file":"fitt.apk","sha256":"${"c".repeat(64)}","signerSha256":"$stable"}]}"""
    @Test fun numericCodesPreventDowngradesEvenWhenVersionNamesLookNewer() {
        assertNull(UpdatePolicy.available(manifest, 5, 36, stable)); assertNull(UpdatePolicy.available(manifest, 6, 36, stable))
        assertNotNull(UpdatePolicy.available(manifest, 4, 36, stable))
    }
    @Test fun installedSigningKeySelectsOnlyCompatibleApkAndDoesNotAcceptUnknownBuilds() {
        assertTrue(UpdatePolicy.available(manifest, 1, 36, stable)!!.downloadUrl.endsWith("/v0.1.3/fitt.apk"))
        assertTrue(UpdatePolicy.available(manifest, 1, 26, legacy)!!.downloadUrl.endsWith("/v0.1.3/fitt-legacy.apk"))
        assertNull(UpdatePolicy.available(manifest, 1, 36, "e".repeat(64)))
        assertNull(UpdatePolicy.available(manifest.copy(minSdk = 37), 1, 36, stable))
    }
    @Test fun remoteMetadataCannotChooseAnArbitraryDomainPathOrPackage() {
        assertEquals(5, UpdatePolicy.decode(json()).versionCode.toInt())
        for (bad in listOf(json().replace("fitt.apk", "https://evil.invalid/file.apk"), json().replace("v0.1.3", "../other"),
            json().replace("io.github.nottarun7.fitt", "other.app"), json().replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            json().replace("\"versionCode\":5", "\"versionCode\":0"), json().replace(stable, "missing"), json() + " ".repeat(65536))) {
            assertThrows(Exception::class.java) { UpdatePolicy.decode(bad) }
        }
    }
}
