package com.craznail.flashnote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun toMode(value: String?): SummaryMode =
        value?.let { runCatching { SummaryMode.valueOf(it) }.getOrDefault(SummaryMode.NONE) }
            ?: SummaryMode.NONE

    @TypeConverter
    fun fromMode(mode: SummaryMode?): String = (mode ?: SummaryMode.NONE).name
}

@Database(entities = [Note::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flashnote.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
