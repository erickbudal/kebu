package com.kebu.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DatabaseBackup(
    private val context: Context,
    private val database: AppDatabase,
) {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private val mutex = Mutex()

    suspend fun isDailyBackupDue(): Boolean = withContext(Dispatchers.IO) {
        prefs.getString(KEY_LAST_DAILY_BACKUP, null) != LocalDate.now().toString()
    }

    suspend fun createDailyBackup(): String {
        val today = LocalDate.now()
        val fileName = createBackup()
        withContext(Dispatchers.IO) {
            prefs.edit(commit = true) { putString(KEY_LAST_DAILY_BACKUP, today.toString()) }
        }
        return fileName
    }

    suspend fun createBackup(): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val fileName = "kebu_backup_${LocalDateTime.now().format(FILE_TIMESTAMP)}.db"
            val snapshot = File(context.cacheDir, SNAPSHOT_FILE_NAME)
            snapshot.delete()
            try {
                database.openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf(snapshot.absolutePath))
                copyToDownloads(snapshot, fileName)
            } finally {
                snapshot.delete()
            }
            fileName
        }
    }

    private fun copyToDownloads(source: File, fileName: String) {
        val resolver = context.contentResolver
        val entry = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), entry)
            ?: throw IOException("O MediaStore não criou $fileName em Downloads")

        try {
            val descriptor = resolver.openFileDescriptor(uri, "w")
                ?: throw IOException("Não deu para abrir $fileName para escrita")
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
                runCatching { output.fd.sync() }
            }
            val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, ready, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    private companion object {
        const val PREFS_NAME = "database_backup"
        const val KEY_LAST_DAILY_BACKUP = "last_daily_backup"
        const val SNAPSHOT_FILE_NAME = "backup_snapshot.db"
        val FILE_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }
}
