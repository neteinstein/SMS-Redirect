package com.neteinstein.smsredirect.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY sortOrder ASC, id DESC")
    fun getRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getRule(id: Long): RuleEntity?

    @Query("SELECT * FROM rules ORDER BY sortOrder ASC, id DESC")
    suspend fun getRulesSnapshot(): List<RuleEntity>

    @Query("SELECT COUNT(*) FROM rules")
    suspend fun getRuleCount(): Int

    @Query("SELECT DISTINCT category FROM rules WHERE category != '' ORDER BY category ASC")
    fun getCategories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RuleEntity)

    @Update
    suspend fun update(rule: RuleEntity)

    @Update
    suspend fun updateAll(rules: List<RuleEntity>)

    @Delete
    suspend fun delete(rule: RuleEntity)
}
