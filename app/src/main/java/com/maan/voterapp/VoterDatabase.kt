package com.maan.voterapp

import android.content.Context
import androidx.room.*

@Entity(tableName = "voters", indices = [Index("serial"), Index("ward"), Index("deletedAt")])
data class Voter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serial: String,
    val name: String,
    val relation: String,
    val epic: String,
    val ward: String,
    val attendance: String = "NONE",
    val selectedCandidate: String = "",
    val deletedAt: Long? = null
)

@Entity(tableName = "candidates", indices = [Index("deletedAt")])
data class Candidate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val deletedAt: Long? = null
)

@Dao
interface VoterDao {
    @Query("SELECT * FROM voters WHERE deletedAt IS NULL ORDER BY CAST(ward AS INTEGER), ward, CAST(serial AS INTEGER), serial")
    suspend fun all(): List<Voter>
    @Query("SELECT * FROM voters WHERE deletedAt IS NULL AND ward = :ward ORDER BY CAST(serial AS INTEGER), serial")
    suspend fun byWard(ward: String): List<Voter>
    @Query("SELECT * FROM voters WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun recycleVoters(): List<Voter>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<Voter>)
    @Update suspend fun update(voter: Voter)
    @Query("UPDATE voters SET deletedAt = :time WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteVoter(id: Long, time: Long)
    @Query("UPDATE voters SET deletedAt = :time WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(time: Long)
    @Query("UPDATE voters SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreVoter(id: Long)
    @Query("UPDATE voters SET deletedAt = NULL")
    suspend fun restoreAllVoters()
    @Query("DELETE FROM voters WHERE id = :id")
    suspend fun permanentlyDeleteVoter(id: Long)
    @Query("DELETE FROM voters WHERE deletedAt IS NOT NULL")
    suspend fun emptyVoterBin()
    @Query("SELECT DISTINCT ward FROM voters WHERE deletedAt IS NULL ORDER BY CAST(ward AS INTEGER), ward")
    suspend fun wards(): List<String>
    @Query("SELECT COUNT(*) FROM voters WHERE deletedAt IS NULL")
    suspend fun count(): Int

    @Query("SELECT * FROM candidates WHERE deletedAt IS NULL ORDER BY id")
    suspend fun candidates(): List<Candidate>
    @Query("SELECT * FROM candidates WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun recycleCandidates(): List<Candidate>
    @Insert suspend fun insertCandidate(candidate: Candidate)
    @Query("UPDATE candidates SET deletedAt = :time WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteCandidate(id: Long, time: Long)
    @Query("UPDATE candidates SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreCandidate(id: Long)
    @Query("DELETE FROM candidates WHERE id = :id")
    suspend fun permanentlyDeleteCandidate(id: Long)
    @Query("DELETE FROM candidates WHERE deletedAt IS NOT NULL")
    suspend fun emptyCandidateBin()
}

@Database(entities = [Voter::class, Candidate::class], version = 2, exportSchema = false)
abstract class VoterDatabase : RoomDatabase() {
    abstract fun dao(): VoterDao
    companion object {
        @Volatile private var INSTANCE: VoterDatabase? = null
        fun get(context: Context): VoterDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, VoterDatabase::class.java, "voters.db")
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
        }
    }
}
