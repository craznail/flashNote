package com.craznail.flashnote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun toMode(value: String?): SummaryMode =
        value?.let { runCatching { SummaryMode.valueOf(it) }.getOrDefault(SummaryMode.NONE) }
            ?: SummaryMode.NONE

    @TypeConverter
    fun fromMode(mode: SummaryMode?): String = (mode ?: SummaryMode.NONE).name
}

@Database(
    entities = [Note::class, FolderEntity::class, TagEntity::class, NoteTagCrossRef::class],
    version = 3,
    exportSchema = false
)
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
                ).addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN folderId INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN archivedAt INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN trashedAt INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS folders (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_folders_name ON folders(name)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS tags (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, colorKey INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags(name)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS note_tags (" +
                        "noteId INTEGER NOT NULL, tagId INTEGER NOT NULL, " +
                        "PRIMARY KEY(noteId, tagId), " +
                        "FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_note_tags_tagId ON note_tags(tagId)"
                )
            }
        }
    }
}
