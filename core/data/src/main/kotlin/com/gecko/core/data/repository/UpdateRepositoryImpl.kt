package com.gecko.core.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.gecko.core.model.update.AppUpdate
import com.gecko.domain.repository.UpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class UpdateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) : UpdateRepository {

    override suspend fun getLatestRelease(): Result<AppUpdate> = runCatching {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("GitHub API returned HTTP ${response.code}")
                }
                val bodyText = response.body?.string().orEmpty()
                val release = UpdateJson.decodeFromString(GithubReleaseDto.serializer(), bodyText)
                val apkAsset = release.assets.firstOrNull { it.name.startsWith("gecko-") && it.name.endsWith(".apk") }
                    ?: error("Latest release has no APK asset attached")
                require(apkAsset.downloadUrl.startsWith("https://")) { "Update download URL is not HTTPS" }
                AppUpdate(
                    versionName = release.tagName.removePrefix("v"),
                    downloadUrl = apkAsset.downloadUrl,
                    releaseUrl = release.htmlUrl,
                )
            }
        }
    }

    override suspend fun downloadApk(update: AppUpdate, onProgress: (Float) -> Unit): Result<File> = runCatching {
        withContext(Dispatchers.IO) {
            require(update.downloadUrl.startsWith("https://")) { "Refusing to download an update over a non-HTTPS URL" }
            val request = Request.Builder().url(update.downloadUrl).get().build()
            val outputFile = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: error("Empty download response")
                val totalBytes = body.contentLength()
                val outputDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val outputFile = File(outputDir, "gecko-${update.versionName}.apk")

                body.byteStream().use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                        var totalRead = 0L
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalBytes > 0) {
                                onProgress(totalRead.toFloat() / totalBytes)
                            }
                        }
                    }
                }
                outputFile
            }

            // The only network-independent guarantee that this file is a genuine Gecko build,
            // not a tampered release asset — checked before ever prompting the user to install
            // it, rather than relying solely on the OS installer's own signature check.
            if (!signingCertificatesMatchInstalledApp(outputFile)) {
                outputFile.delete()
                error("Downloaded update's signature doesn't match this app's — refusing to install it.")
            }
            outputFile
        }
    }

    private fun signingCertificatesMatchInstalledApp(apkFile: File): Boolean {
        val installed = signingCertificateFingerprints(packageName = context.packageName)
        val downloaded = signingCertificateFingerprints(archiveFilePath = apkFile.absolutePath)
        return installed.isNotEmpty() && installed == downloaded
    }

    private fun signingCertificateFingerprints(packageName: String? = null, archiveFilePath: String? = null): Set<String> {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = runCatching {
            if (archiveFilePath != null) {
                pm.getPackageArchiveInfo(archiveFilePath, flags)
            } else {
                pm.getPackageInfo(packageName!!, flags)
            }
        }.getOrNull() ?: return emptySet()

        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.toList().orEmpty()
        }
        return certs.map { sha256Hex(it.toByteArray()) }.toSet()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val GITHUB_OWNER = "vabxsen"
        private const val GITHUB_REPO = "gecko"
        private const val DOWNLOAD_BUFFER_BYTES = 8 * 1024
        private val UpdateJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

@Serializable
private data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GithubAssetDto> = emptyList(),
)

@Serializable
private data class GithubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)
