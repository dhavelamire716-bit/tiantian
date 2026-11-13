package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [VideoEntity::class], version = 1, exportSchema = false)
abstract class VerdeReelDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao

    companion object {
        @Volatile
        private var instance: VerdeReelDatabase? = null

        fun getInstance(context: Context): VerdeReelDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VerdeReelDatabase::class.java,
                    "verdereel_database"
                ).build().also { instance = it }
            }
        }
    }
}
