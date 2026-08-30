package com.gecko.domain.repository

import com.gecko.core.model.update.AppUpdate
import java.io.File

interface UpdateRepository {
    suspend fun getLatestRelease(): Result<AppUpdate>

    suspend fun downloadApk(update: AppUpdate, onProgress: (Float) -> Unit): Result<File>
}
