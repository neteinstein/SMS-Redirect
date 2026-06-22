package com.neteinstein.smsredirect

import android.app.Application
import androidx.room.Room
import com.neteinstein.smsredirect.data.local.AppDatabase
import com.neteinstein.smsredirect.data.repository.RuleRepositoryImpl
import com.neteinstein.smsredirect.data.repository.SettingsRepositoryImpl
import com.neteinstein.smsredirect.domain.repository.RuleRepository
import com.neteinstein.smsredirect.domain.usecase.AddRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.CanForwardSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.DeleteRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.FindMatchingRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetSendWhileRoamingUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRulesUseCase
import com.neteinstein.smsredirect.domain.usecase.ReorderRulesUseCase
import com.neteinstein.smsredirect.domain.usecase.SetSendWhileRoamingUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateRuleUseCase

class SmsRedirectApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, AppDatabase::class.java, "sms_redirect.db")
            .fallbackToDestructiveMigration()
            .build()
        val repository = RuleRepositoryImpl(database.ruleDao())
        val settingsRepository = SettingsRepositoryImpl(database.appSettingsDao())
        val findMatchingRuleUseCase = FindMatchingRuleUseCase(repository)
        val canForwardSmsUseCase = CanForwardSmsUseCase(settingsRepository)
        appContainer = AppContainer(
            ruleRepository = repository,
            getRulesUseCase = GetRulesUseCase(repository),
            getRuleUseCase = GetRuleUseCase(repository),
            addRuleUseCase = AddRuleUseCase(repository),
            updateRuleUseCase = UpdateRuleUseCase(repository),
            deleteRuleUseCase = DeleteRuleUseCase(repository),
            reorderRulesUseCase = ReorderRulesUseCase(repository),
            getSendWhileRoamingUseCase = GetSendWhileRoamingUseCase(settingsRepository),
            setSendWhileRoamingUseCase = SetSendWhileRoamingUseCase(settingsRepository),
            findMatchingRuleUseCase = findMatchingRuleUseCase,
            canForwardSmsUseCase = canForwardSmsUseCase,
            smsForwarder = SmsForwarder(findMatchingRuleUseCase, canForwardSmsUseCase)
        )
    }
}

data class AppContainer(
    val ruleRepository: RuleRepository,
    val getRulesUseCase: GetRulesUseCase,
    val getRuleUseCase: GetRuleUseCase,
    val addRuleUseCase: AddRuleUseCase,
    val updateRuleUseCase: UpdateRuleUseCase,
    val deleteRuleUseCase: DeleteRuleUseCase,
    val reorderRulesUseCase: ReorderRulesUseCase,
    val getSendWhileRoamingUseCase: GetSendWhileRoamingUseCase,
    val setSendWhileRoamingUseCase: SetSendWhileRoamingUseCase,
    val findMatchingRuleUseCase: FindMatchingRuleUseCase,
    val canForwardSmsUseCase: CanForwardSmsUseCase,
    val smsForwarder: SmsForwarder
)
