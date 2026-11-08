package com.maan.voterapp

import android.content.Context
import androidx.room.*

@Entity(tableName = "voters", indices = [Index("serial"), Index("ward")])
data class Voter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serial: String,
    val name: String,
    val relation: String,
    val epic: String,
    val ward: String,
    val attendance: String = "NONE",
    val selectedCandidate: String = ""
)

@Dao
interface VoterDao {
    @Query("SELECT * FROM voters ORDER BY CAST(ward AS INTEGER), CAST(serial AS INTEGER), serial")
    suspend fun all(): List<Voter>
    @Query("SELECT * FROM voters WHERE ward = :ward ORDER BY CAST(serial AS INTEGER), serial")
    suspend fun byWard(ward: String): List<Voter>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<Voter>)
    @Update suspend fun update(voter: Voter)
    @Query("DELETE FROM voters") suspend fun clear()
    @Query("SELECT DISTINCT ward FROM voters ORDER BY CAST(ward AS INTEGER), ward")
    suspend fun wards(): List<String>
    @Query("SELECT COUNT(*) FROM voters") suspend fun count(): Int
}

@Database(entities = [Voter::class], version = 1, exportSchema = false)
abstract class VoterDatabase : RoomDatabase() {
    abstract fun dao(): VoterDao
    companion object {
        @Volatile private var INSTANCE: VoterDatabase? = null
        fun get(context: Context): VoterDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, VoterDatabase::class.java, "voters.db").build().also { INSTANCE = it }
        }
    }
}
