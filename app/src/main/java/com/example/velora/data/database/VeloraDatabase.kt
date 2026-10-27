package com.example.velora.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.velora.data.dao.AccountDao
import com.example.velora.data.dao.BudgetDao
import com.example.velora.data.dao.RecurringDao
import com.example.velora.data.dao.ReminderDao
import com.example.velora.data.dao.TransactionDao
import com.example.velora.data.dao.UdhaarDao
import com.example.velora.data.entity.AccountEntity
import com.example.velora.data.entity.BudgetEntity
import com.example.velora.data.entity.RecurringPaymentEntity
import com.example.velora.data.entity.ReminderEntity
import com.example.velora.data.entity.TransactionEntity
import com.example.velora.data.entity.UdhaarEntity
import com.example.velora.data.entity.UdhaarPaymentEntity

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        UdhaarEntity::class,
        UdhaarPaymentEntity::class,
        BudgetEntity::class,
        ReminderEntity::class,
        RecurringPaymentEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(VeloraTypeConverters::class)
abstract class VeloraDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun udhaarDao(): UdhaarDao
    abstract fun budgetDao(): BudgetDao
    abstract fun reminderDao(): ReminderDao
    abstract fun recurringDao(): RecurringDao

    companion object {
        @Volatile
        private var INSTANCE: VeloraDatabase? = null

        fun getInstance(context: Context): VeloraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VeloraDatabase::class.java,
                    "velora_finance.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
