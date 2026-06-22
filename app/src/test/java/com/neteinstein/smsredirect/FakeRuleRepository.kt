package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeRuleRepository(
    initial: List<Rule> = emptyList(),
) : RuleRepository {
    private val rules = MutableStateFlow(initial)

    override fun getRules(): Flow<List<Rule>> = rules

    override suspend fun getRule(id: Long): Rule? = rules.value.firstOrNull { it.id == id }

    override suspend fun getRulesSnapshot(): List<Rule> = rules.value

    override suspend fun getRuleCount(): Int = rules.value.size

    override suspend fun insert(rule: Rule) {
        val nextId = (rules.value.maxOfOrNull { it.id } ?: 0L) + 1L
        rules.update { listOf(rule.copy(id = nextId)) + it }
    }

    override suspend fun update(rule: Rule) {
        rules.update { list -> list.map { if (it.id == rule.id) rule else it } }
    }

    override suspend fun updateAll(rules: List<Rule>) {
        this.rules.value = rules
    }

    override suspend fun delete(rule: Rule) {
        rules.update { list -> list.filterNot { it.id == rule.id } }
    }
}
