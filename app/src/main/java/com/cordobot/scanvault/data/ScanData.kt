package com.cordobot.scanvault.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cordobot.scanvault.domain.Scan
import com.cordobot.scanvault.domain.ScanRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "scans", indices = [Index("rawValue")])
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawValue: String,
    val format: String,
    val scannedAt: Long,
    val isFavorite: Boolean = false
)

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY scannedAt DESC")
    fun observeAll(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE id = :id")
    fun observe(id: Long): Flow<ScanEntity?>

    @Query("SELECT * FROM scans WHERE rawValue = :rawValue ORDER BY scannedAt DESC LIMIT 1")
    suspend fun latestWithValue(rawValue: String): ScanEntity?

    @Insert
    suspend fun insert(scan: ScanEntity): Long

    @Query("UPDATE scans SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [ScanEntity::class], version = 1, exportSchema = true)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
}

@Singleton
class ScanRepositoryImpl @Inject constructor(private val dao: ScanDao) : ScanRepository {
    override fun observeHistory(): Flow<List<Scan>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observeScan(id: Long): Flow<Scan?> = dao.observe(id).map { it?.toDomain() }
    override suspend fun latestWithValue(rawValue: String): Scan? = dao.latestWithValue(rawValue)?.toDomain()
    override suspend fun insert(rawValue: String, format: String, scannedAtMillis: Long): Long =
        dao.insert(ScanEntity(rawValue = rawValue, format = format, scannedAt = scannedAtMillis))
    override suspend fun toggleFavorite(id: Long) = dao.toggleFavorite(id)
    override suspend fun delete(id: Long) = dao.delete(id)
}

private fun ScanEntity.toDomain() = Scan(id, rawValue, format, Instant.ofEpochMilli(scannedAt), isFavorite)

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ScanDatabase =
        Room.databaseBuilder(context, ScanDatabase::class.java, "scanvault.db").build()

    @Provides
    fun provideDao(database: ScanDatabase): ScanDao = database.scanDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun bindRepository(impl: ScanRepositoryImpl): ScanRepository
}
