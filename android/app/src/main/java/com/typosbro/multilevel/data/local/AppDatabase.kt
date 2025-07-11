// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/local/AppDatabase.kt
package com.typosbro.multilevel.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        WordEntity::class,
        IeltsExamResultEntity::class,
        MultilevelExamResultEntity::class
    ],
    version = 2, // Incremented version number
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun wordDao(): WordDao
    abstract fun ieltsExamResultDao(): IeltsExamResultDao
    abstract fun multilevelExamResultDao(): MultilevelExamResultDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration() // Simple migration strategy for this change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}