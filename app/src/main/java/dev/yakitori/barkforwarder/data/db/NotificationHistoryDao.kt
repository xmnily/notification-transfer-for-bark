package dev.yakitori.barkforwarder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationHistoryDao {
    @Query("SELECT * FROM notification_history ORDER BY forwardedAt DESC")
    fun observeAll(): Flow<List<NotificationHistoryEntity>>

    @Insert
    suspend fun insert(entry: NotificationHistoryEntity)

    @Query("DELETE FROM notification_history")
    suspend fun deleteAll()
}
