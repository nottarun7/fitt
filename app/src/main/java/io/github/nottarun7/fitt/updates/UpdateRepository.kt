package io.github.nottarun7.fitt.updates

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.nottarun7.fitt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

interface UpdateSource { suspend fun fetch(): UpdateManifest }

class GithubUpdateSource : UpdateSource {
    override suspend fun fetch(): UpdateManifest = withContext(Dispatchers.IO) {
        var target = URL(UPDATE_URL)
        repeat(6) {
            require(target.protocol == "https" && target.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com") && target.userInfo == null)
            val connection = target.openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000; connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "Fitt/${BuildConfig.VERSION_NAME}")
                connection.setRequestProperty("Accept", "application/json")
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) target = URL(target, connection.getHeaderField("Location") ?: error("Missing redirect."))
                else {
                    check(status == 200) { "Update service unavailable." }
                    val output = ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(4096)
                        while (true) { val count = input.read(buffer); if (count < 0) break; check(output.size() + count <= 65_536); output.write(buffer, 0, count) }
                    }
                    return@withContext UpdatePolicy.decode(output.toString("UTF-8"))
                }
            } finally { connection.disconnect() }
        }
        error("Too many update redirects.")
    }
}

@Suppress("DEPRECATION")
fun installedSigner(context: Context): String {
    val info = context.packageManager.getPackageInfo(context.packageName,
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
    val signature = if (Build.VERSION.SDK_INT >= 28) info.signingInfo!!.apkContentsSigners.single() else info.signatures!!.single()
    return MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
}
