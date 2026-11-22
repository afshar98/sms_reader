package com.example.smsdiscountorganizer.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import com.example.smsdiscountorganizer.domain.sms.SmsDataSource
import com.example.smsdiscountorganizer.domain.sms.SmsCursor
import com.example.smsdiscountorganizer.domain.sms.SmsInboxUnavailableException
import com.example.smsdiscountorganizer.domain.sms.SmsMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidSmsDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsDataSource {
    override suspend fun readInbox(after: SmsCursor?): List<SmsMessage> = withContext(Dispatchers.IO) {
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

        cursor.use {
            val idColumn = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressColumn = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyColumn = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateColumn = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            buildList {
                while (it.moveToNext()) {
                    add(
                        SmsMessage(
                            id = it.getLong(idColumn),
                            address = it.getString(addressColumn),
                            body = it.getString(bodyColumn),
                            dateMillis = it.getLong(dateColumn),
                        ),
                    )
                }
            }
        }
    }

    private fun checkReadPermission() {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("READ_SMS permission is not granted")
        }
    }
}
