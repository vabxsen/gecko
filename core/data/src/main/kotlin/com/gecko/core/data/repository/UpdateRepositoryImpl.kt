package com.gecko.core.data.repository

import android.content.Context
import com.gecko.core.model.update.AppUpdate
import com.gecko.domain.repository.UpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: error("Latest release has no APK asset attached")
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
            val request = Request.Builder().url(update.downloadUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
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
        }
    }

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
