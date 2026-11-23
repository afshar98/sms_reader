package com.example.smsdiscountorganizer.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.Telephony
import com.example.smsdiscountorganizer.domain.sms.SmsDataSource
import com.example.smsdiscountorganizer.domain.sms.SmsCursor
import com.example.smsdiscountorganizer.domain.sms.SmsInboxUnavailableException
import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

@Singleton
class AndroidSmsDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsDataSource {
    override fun readInbox(after: SmsCursor?): Flow<SmsMessage> = flow {
        checkReadPermission()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        val selection = after?.let {
            "${Telephony.Sms._ID} > ?"
        }
        val selectionArgs = after?.let { arrayOf(it.smsId.toString()) }
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms._ID} ASC",
        ) ?: throw SmsInboxUnavailableException()

        cursor.use { rows ->
            val idColumn = rows.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressColumn = rows.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyColumn = rows.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateColumn = rows.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (rows.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val id = rows.readSafely { getLong(idColumn) } ?: continue
                if (id <= 0L) continue
                val dateMillis = rows.readSafely { getLong(dateColumn) } ?: 0L
                emit(
                    SmsMessage(
                        id = id,
                        address = rows.readSafely { getString(addressColumn) },
                        body = rows.readSafely { getString(bodyColumn) },
                        dateMillis = dateMillis,
                    ),
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun readMessageById(id: Long): SmsMessage? = withContext(Dispatchers.IO) {
        if (id <= 0L) return@withContext null
        checkReadPermission()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms._ID} = ?",
            arrayOf(id.toString()),
            null,
        ) ?: throw SmsInboxUnavailableException()

        cursor.use { rows ->
            if (!rows.moveToNext()) return@withContext null
            val idColumn = rows.getColumnIndexOrThrow(Telephony.Sms._ID)
            val bodyColumn = rows.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateColumn = rows.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val rowId = rows.readSafely { getLong(idColumn) } ?: return@withContext null
            SmsMessage(
                id = rowId,
                address = null,
                body = rows.readSafely { getString(bodyColumn) },
                dateMillis = rows.readSafely { getLong(dateColumn) } ?: 0L,
            )
        }
    }

    private inline fun <T> Cursor.readSafely(read: Cursor.() -> T): T? =
        try {
            read()
        } catch (_: RuntimeException) {
            null
        }

    private fun checkReadPermission() {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("READ_SMS permission is not granted")
        }
    }
}
