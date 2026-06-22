package com.neteinstein.smsredirect

import android.app.Application
import androidx.room.Room
import com.neteinstein.smsredirect.data.local.AppDatabase
import com.neteinstein.smsredirect.data.repository.RuleRepositoryImpl
import com.neteinstein.smsredirect.domain.usecase.AddRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.DeleteRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRulesUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateRuleUseCase

class SmsRedirectApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, AppDatabase::class.java, "sms_redirect.db").build()
        val repository = RuleRepositoryImpl(database.ruleDao())
        appContainer = AppContainer(
            getRulesUseCase = GetRulesUseCase(repository),
            getRuleUseCase = GetRuleUseCase(repository),
            addRuleUseCase = AddRuleUseCase(repository),
            updateRuleUseCase = UpdateRuleUseCase(repository),
            deleteRuleUseCase = DeleteRuleUseCase(repository)
        )
    }
}

data class AppContainer(
    val getRulesUseCase: GetRulesUseCase,
    val getRuleUseCase: GetRuleUseCase,
    val addRuleUseCase: AddRuleUseCase,
    val updateRuleUseCase: UpdateRuleUseCase,
    val deleteRuleUseCase: DeleteRuleUseCase
)
