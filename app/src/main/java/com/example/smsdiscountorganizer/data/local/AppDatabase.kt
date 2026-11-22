package com.example.smsdiscountorganizer.data.local

import androidx.room.Database
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_preferences")
data class AppPreferenceEntity(
    @PrimaryKey val key: String,
    val enabled: Boolean,
)

@Dao
interface AppPreferenceDao {
    @Query("SELECT * FROM app_preferences WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<AppPreferenceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(preference: AppPreferenceEntity)
}

@Database(entities = [AppPreferenceEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appPreferenceDao(): AppPreferenceDao
}
