package com.neteinstein.smsredirect

import android.app.Application
import androidx.room.Room
import com.neteinstein.smsredirect.data.contacts.AndroidContactNameResolver
import com.neteinstein.smsredirect.data.local.AppDatabase
import com.neteinstein.smsredirect.data.repository.ForwardHistoryRepositoryImpl
import com.neteinstein.smsredirect.data.repository.RuleRepositoryImpl
import com.neteinstein.smsredirect.data.repository.SettingsRepositoryImpl
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.repository.ForwardHistoryRepository
import com.neteinstein.smsredirect.domain.repository.RuleRepository
import com.neteinstein.smsredirect.domain.usecase.AddRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.CanForwardSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.DeleteRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.FindMatchingRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.GetForwardHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.GetForwardPrefixNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetForwardPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.GetKeepHistoryNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetKeepHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRulesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetSendWhileRoamingUseCase
import com.neteinstein.smsredirect.domain.usecase.RecordForwardHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.ReorderRulesUseCase
import com.neteinstein.smsredirect.domain.usecase.SetDefaultPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.SetForwardPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.SetKeepHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.SetSendWhileRoamingUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateRuleUseCase

class SmsRedirectApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database =
            Room
                .databaseBuilder(this, AppDatabase::class.java, "sms_redirect.db")
                .fallbackToDestructiveMigration()
                .build()
        val repository = RuleRepositoryImpl(database.ruleDao())
        val settingsRepository = SettingsRepositoryImpl(database.appSettingsDao())
        val forwardHistoryRepository = ForwardHistoryRepositoryImpl(database.forwardHistoryDao())
        val findMatchingRuleUseCase = FindMatchingRuleUseCase(repository)
        val canForwardSmsUseCase = CanForwardSmsUseCase(settingsRepository)
        val getKeepHistoryNowUseCase = GetKeepHistoryNowUseCase(settingsRepository)
        val getForwardPrefixNowUseCase = GetForwardPrefixNowUseCase(settingsRepository)
        val recordForwardHistoryUseCase = RecordForwardHistoryUseCase(forwardHistoryRepository)
        val contactNameResolver = AndroidContactNameResolver(contentResolver)
        appContainer =
            AppContainer(
                ruleRepository = repository,
                forwardHistoryRepository = forwardHistoryRepository,
                contactNameResolver = contactNameResolver,
                getRulesUseCase = GetRulesUseCase(repository),
                getRuleUseCase = GetRuleUseCase(repository),
                addRuleUseCase = AddRuleUseCase(repository),
                updateRuleUseCase = UpdateRuleUseCase(repository),
                deleteRuleUseCase = DeleteRuleUseCase(repository),
                reorderRulesUseCase = ReorderRulesUseCase(repository),
                getSendWhileRoamingUseCase = GetSendWhileRoamingUseCase(settingsRepository),
                setSendWhileRoamingUseCase = SetSendWhileRoamingUseCase(settingsRepository),
                getKeepHistoryUseCase = GetKeepHistoryUseCase(settingsRepository),
                setKeepHistoryUseCase = SetKeepHistoryUseCase(settingsRepository),
                getDefaultPrefixUseCase = GetDefaultPrefixUseCase(settingsRepository),
                setDefaultPrefixUseCase = SetDefaultPrefixUseCase(settingsRepository),
                getDefaultPrefixNowUseCase = GetDefaultPrefixNowUseCase(settingsRepository),
                getForwardPrefixUseCase = GetForwardPrefixUseCase(settingsRepository),
                setForwardPrefixUseCase = SetForwardPrefixUseCase(settingsRepository),
                getForwardHistoryUseCase = GetForwardHistoryUseCase(forwardHistoryRepository),
                findMatchingRuleUseCase = findMatchingRuleUseCase,
                canForwardSmsUseCase = canForwardSmsUseCase,
                smsForwarder =
                    SmsForwarder(
                        findMatchingRuleUseCase = findMatchingRuleUseCase,
                        canForwardSmsUseCase = canForwardSmsUseCase,
                        getKeepHistoryNowUseCase = getKeepHistoryNowUseCase,
                        recordForwardHistoryUseCase = recordForwardHistoryUseCase,
                        getForwardPrefixNowUseCase = getForwardPrefixNowUseCase,
                        contactNameResolver = contactNameResolver,
                    ),
            )
    }
}

data class AppContainer(
    val ruleRepository: RuleRepository,
    val forwardHistoryRepository: ForwardHistoryRepository,
    val contactNameResolver: ContactNameResolver,
    val getRulesUseCase: GetRulesUseCase,
    val getRuleUseCase: GetRuleUseCase,
    val addRuleUseCase: AddRuleUseCase,
    val updateRuleUseCase: UpdateRuleUseCase,
    val deleteRuleUseCase: DeleteRuleUseCase,
    val reorderRulesUseCase: ReorderRulesUseCase,
    val getSendWhileRoamingUseCase: GetSendWhileRoamingUseCase,
    val setSendWhileRoamingUseCase: SetSendWhileRoamingUseCase,
    val getKeepHistoryUseCase: GetKeepHistoryUseCase,
    val setKeepHistoryUseCase: SetKeepHistoryUseCase,
    val getDefaultPrefixUseCase: GetDefaultPrefixUseCase,
    val setDefaultPrefixUseCase: SetDefaultPrefixUseCase,
    val getDefaultPrefixNowUseCase: GetDefaultPrefixNowUseCase,
    val getForwardPrefixUseCase: GetForwardPrefixUseCase,
    val setForwardPrefixUseCase: SetForwardPrefixUseCase,
    val getForwardHistoryUseCase: GetForwardHistoryUseCase,
    val findMatchingRuleUseCase: FindMatchingRuleUseCase,
    val canForwardSmsUseCase: CanForwardSmsUseCase,
    val smsForwarder: SmsForwarder,
)
