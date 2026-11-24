package com.calcsuits.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class CalculatorDatabase : RoomDatabase() {
    abstract val historyDao: HistoryDao
}
