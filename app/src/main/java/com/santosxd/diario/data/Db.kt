package com.santosxd.diario.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "entry")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val mood: Int,
    val note: String
)

@Entity(tableName = "activity", indices = [Index(value = ["name"], unique = true)])
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "entry_activity",
    primaryKeys = ["entryId", "activityId"],
    indices = [Index("activityId")],
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EntryActivityRef(
    val entryId: Long,
    val activityId: Long
)

data class EntryWithActivities(
    @Embedded val entry: EntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EntryActivityRef::class,
            parentColumn = "entryId",
            entityColumn = "activityId"
        )
    )
    val activities: List<ActivityEntity>
)

@Dao
interface DiaryDao {
    @Transaction
    @Query("SELECT * FROM entry ORDER BY createdAt DESC")
    fun observeEntries(): Flow<List<EntryWithActivities>>

    @Transaction
    @Query("SELECT * FROM entry ORDER BY createdAt ASC")
    suspend fun allEntries(): List<EntryWithActivities>

    @Query("SELECT * FROM activity ORDER BY name")
    fun observeActivities(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity")
    suspend fun allActivities(): List<ActivityEntity>

    @Query("SELECT COUNT(*) FROM activity")
    suspend fun activityCount(): Int

    @Query("SELECT id FROM activity WHERE name = :name")
    suspend fun activityIdByName(name: String): Long?

    @Insert
    suspend fun insertEntry(entry: EntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivity(activity: ActivityEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRefs(refs: List<EntryActivityRef>)

    @Update
    suspend fun updateEntry(entry: EntryEntity)

    @Query("DELETE FROM entry_activity WHERE entryId = :entryId")
    suspend fun clearRefs(entryId: Long)

    @Query("UPDATE activity SET name = :name WHERE id = :id")
    suspend fun renameActivity(id: Long, name: String)

    @Query("DELETE FROM activity WHERE id = :id")
    suspend fun deleteActivity(id: Long)

    @Query("DELETE FROM entry WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM entry")
    suspend fun clearEntries()

    @Query("DELETE FROM activity")
    suspend fun clearActivities()
}

@Database(
    entities = [EntryEntity::class, ActivityEntity::class, EntryActivityRef::class],
    version = 1,
    exportSchema = false
)
abstract class DiaryDb : RoomDatabase() {
    abstract fun dao(): DiaryDao

    companion object {
        @Volatile
        private var instance: DiaryDb? = null

        fun get(context: Context): DiaryDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DiaryDb::class.java,
                    "diario.db"
                ).build().also { instance = it }
            }
    }
}
