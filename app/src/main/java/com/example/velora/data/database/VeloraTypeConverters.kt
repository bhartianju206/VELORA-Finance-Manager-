package com.example.velora.data.database

import androidx.room.TypeConverter
import com.example.velora.data.entity.AccountType
import com.example.velora.data.entity.RecurrenceInterval
import com.example.velora.data.entity.ReminderType
import com.example.velora.data.entity.TransactionType
import com.example.velora.data.entity.UdhaarStatus
import com.example.velora.data.entity.UdhaarType

class VeloraTypeConverters {
    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name
    @TypeConverter
    fun toAccountType(value: String): AccountType = runCatching { AccountType.valueOf(value) }.getOrDefault(AccountType.CUSTOM)

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name
    @TypeConverter
    fun toTransactionType(value: String): TransactionType = runCatching { TransactionType.valueOf(value) }.getOrDefault(TransactionType.EXPENSE)

    @TypeConverter
    fun fromUdhaarType(value: UdhaarType): String = value.name
    @TypeConverter
    fun toUdhaarType(value: String): UdhaarType = runCatching { UdhaarType.valueOf(value) }.getOrDefault(UdhaarType.LENT)

    @TypeConverter
    fun fromUdhaarStatus(value: UdhaarStatus): String = value.name
    @TypeConverter
    fun toUdhaarStatus(value: String): UdhaarStatus = runCatching { UdhaarStatus.valueOf(value) }.getOrDefault(UdhaarStatus.PENDING)

    @TypeConverter
    fun fromReminderType(value: ReminderType): String = value.name
    @TypeConverter
    fun toReminderType(value: String): ReminderType = runCatching { ReminderType.valueOf(value) }.getOrDefault(ReminderType.BILL)

    @TypeConverter
    fun fromRecurrenceInterval(value: RecurrenceInterval): String = value.name
    @TypeConverter
    fun toRecurrenceInterval(value: String): RecurrenceInterval = runCatching { RecurrenceInterval.valueOf(value) }.getOrDefault(RecurrenceInterval.MONTHLY)
}
