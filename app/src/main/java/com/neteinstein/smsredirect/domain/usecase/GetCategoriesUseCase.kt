package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.repository.RuleRepository
import kotlinx.coroutines.flow.Flow

class GetCategoriesUseCase(
    private val repository: RuleRepository,
) {
    operator fun invoke(): Flow<List<String>> = repository.getCategories()
}
