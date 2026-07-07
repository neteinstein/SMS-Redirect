package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.RuleDao
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RuleRepositoryImpl(
    private val dao: RuleDao,
) : RuleRepository {
    override fun getRules(): Flow<List<Rule>> = dao.getRules().map { list -> list.map { it.toDomain() } }

    override suspend fun getRule(id: Long): Rule? = dao.getRule(id)?.toDomain()

    override suspend fun getRulesSnapshot(): List<Rule> = dao.getRulesSnapshot().map { it.toDomain() }

    override suspend fun getRuleCount(): Int = dao.getRuleCount()

    override fun getCategories(): Flow<List<String>> = dao.getCategories()

    override suspend fun insert(rule: Rule) {
        dao.insert(rule.toEntity())
    }

    override suspend fun update(rule: Rule) {
        dao.update(rule.toEntity())
    }

    override suspend fun updateAll(rules: List<Rule>) {
        dao.updateAll(rules.map { it.toEntity() })
    }

    override suspend fun delete(rule: Rule) {
        dao.delete(rule.toEntity())
    }
}
