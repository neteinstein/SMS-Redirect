package com.neteinstein.smsredirect.domain.repository

import com.neteinstein.smsredirect.domain.model.Rule
import kotlinx.coroutines.flow.Flow

interface RuleRepository {
    fun getRules(): Flow<List<Rule>>

    suspend fun getRule(id: Long): Rule?

    suspend fun getRulesSnapshot(): List<Rule>

    suspend fun getRuleCount(): Int

    fun getCategories(): Flow<List<String>>

    suspend fun insert(rule: Rule)

    suspend fun update(rule: Rule)

    suspend fun updateAll(rules: List<Rule>)

    suspend fun delete(rule: Rule)
}
