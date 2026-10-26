package io.github.nottarun7.fitt.updates

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val RELEASES_URL = "https://github.com/nottarun7/fitt/releases/latest"
const val UPDATE_URL = "$RELEASES_URL/download/update.json"

@Serializable
data class UpdateAsset(val file: String, val sha256: String, val signerSha256: String)
@Serializable
data class UpdateManifest(val schemaVersion: Int, val packageName: String, val versionCode: Long,
    val versionName: String, val minSdk: Int, val tag: String, val notes: String, val assets: List<UpdateAsset>)
data class AvailableUpdate(val versionCode: Long, val versionName: String, val notes: String, val downloadUrl: String)

object UpdatePolicy {
    private val json = Json { ignoreUnknownKeys = false }
    fun decode(text: String): UpdateManifest {
        require(text.toByteArray(Charsets.UTF_8).size <= 65_536) { "Update information is too large." }
        val manifest = json.decodeFromString<UpdateManifest>(text)
        require(manifest.schemaVersion == 1 && manifest.packageName == "io.github.nottarun7.fitt") { "Unsupported update information." }
        require(manifest.versionCode in 1..2_100_000_000 && manifest.minSdk in 26..1000)
        require(manifest.versionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")) && manifest.tag == "v${manifest.versionName}")
        require(manifest.notes.length <= 8000 && manifest.assets.size in 1..2)
        require(manifest.assets.map { it.file }.distinct().size == manifest.assets.size)
        require(manifest.assets.map { it.signerSha256 }.distinct().size == manifest.assets.size)
        manifest.assets.forEach { asset ->
            require(asset.file in setOf("fitt.apk", "fitt-legacy.apk"))
            require(asset.sha256.matches(Regex("[0-9a-f]{64}")) && asset.signerSha256.matches(Regex("[0-9a-f]{64}")))
        }
        return manifest
    }
    fun available(manifest: UpdateManifest, installedCode: Long, sdk: Int, signer: String): AvailableUpdate? {
        if (manifest.versionCode <= installedCode || manifest.minSdk > sdk) return null
        val asset = manifest.assets.singleOrNull { it.signerSha256 == signer } ?: return null
        return AvailableUpdate(manifest.versionCode, manifest.versionName, manifest.notes,
            "https://github.com/nottarun7/fitt/releases/download/${manifest.tag}/${asset.file}")
    }
}
