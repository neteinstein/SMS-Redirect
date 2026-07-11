package com.neteinstein.smsredirect

import android.app.Application
import androidx.room.Room
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.neteinstein.smsredirect.data.analytics.FirebaseAnalyticsTracker
import com.neteinstein.smsredirect.data.analytics.FirebaseCrashReporter
import com.neteinstein.smsredirect.data.contacts.AndroidContactNameResolver
import com.neteinstein.smsredirect.data.local.AppDatabase
import com.neteinstein.smsredirect.data.repository.ForwardHistoryRepositoryImpl
import com.neteinstein.smsredirect.data.repository.RuleRepositoryImpl
import com.neteinstein.smsredirect.data.repository.ScheduledSmsHistoryRepositoryImpl
import com.neteinstein.smsredirect.data.repository.ScheduledSmsRepositoryImpl
import com.neteinstein.smsredirect.data.repository.SettingsRepositoryImpl
import com.neteinstein.smsredirect.data.scheduling.WorkManagerScheduledSmsScheduler
import com.neteinstein.smsredirect.domain.analytics.AnalyticsTracker
import com.neteinstein.smsredirect.domain.analytics.CrashReporter
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.repository.ForwardHistoryRepository
import com.neteinstein.smsredirect.domain.repository.RuleRepository
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsHistoryRepository
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository
import com.neteinstein.smsredirect.domain.scheduling.ScheduledSmsScheduler
import com.neteinstein.smsredirect.domain.usecase.AddRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.AddScheduledSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.CanForwardSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.DeleteAllHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.DeleteAllScheduledSmsHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.DeleteRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.DeleteScheduledSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.ExportRulesUseCase
import com.neteinstein.smsredirect.domain.usecase.FindMatchingRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetCategoriesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.GetForwardHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.GetForwardPrefixNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetForwardPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.GetKeepHistoryNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetKeepHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.GetKeepScheduleHistoryNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetKeepScheduleHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRulesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetScheduledSmsCategoriesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetScheduledSmsHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.GetScheduledSmsListUseCase
import com.neteinstein.smsredirect.domain.usecase.GetScheduledSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.GetSendWhileRoamingUseCase
import com.neteinstein.smsredirect.domain.usecase.GetShowNotificationsNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetShowNotificationsUseCase
import com.neteinstein.smsredirect.domain.usecase.ImportRulesUseCase
import com.neteinstein.smsredirect.domain.usecase.RecordForwardHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.RecordScheduledSmsHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.ReorderRulesUseCase
import com.neteinstein.smsredirect.domain.usecase.ReorderScheduledSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.SetDefaultPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.SetForwardPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.SetKeepHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.SetKeepScheduleHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.SetSendWhileRoamingUseCase
import com.neteinstein.smsredirect.domain.usecase.SetShowNotificationsUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateScheduledSmsUseCase

class SmsRedirectApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        val analyticsTracker: AnalyticsTracker = FirebaseAnalyticsTracker(this)
        val crashReporter: CrashReporter = FirebaseCrashReporter()
        val database =
            Room
                .databaseBuilder(this, AppDatabase::class.java, "sms_redirect.db")
                .fallbackToDestructiveMigration()
                .build()
        val repository = RuleRepositoryImpl(database.ruleDao())
        val settingsRepository = SettingsRepositoryImpl(database.appSettingsDao())
        val forwardHistoryRepository = ForwardHistoryRepositoryImpl(database.forwardHistoryDao())
        val scheduledSmsRepository = ScheduledSmsRepositoryImpl(database.scheduledSmsDao())
        val scheduledSmsHistoryRepository = ScheduledSmsHistoryRepositoryImpl(database.scheduledSmsHistoryDao())
        val findMatchingRuleUseCase = FindMatchingRuleUseCase(repository)
        val canForwardSmsUseCase = CanForwardSmsUseCase(settingsRepository)
        val getKeepHistoryNowUseCase = GetKeepHistoryNowUseCase(settingsRepository)
        val getForwardPrefixNowUseCase = GetForwardPrefixNowUseCase(settingsRepository)
        val getShowNotificationsNowUseCase = GetShowNotificationsNowUseCase(settingsRepository)
        val recordForwardHistoryUseCase = RecordForwardHistoryUseCase(forwardHistoryRepository)
        val getKeepScheduleHistoryNowUseCase = GetKeepScheduleHistoryNowUseCase(settingsRepository)
        val recordScheduledSmsHistoryUseCase = RecordScheduledSmsHistoryUseCase(scheduledSmsHistoryRepository)
        val contactNameResolver = AndroidContactNameResolver(contentResolver)
        val scheduledSmsScheduler = WorkManagerScheduledSmsScheduler(this)
        val getScheduledSmsUseCase = GetScheduledSmsUseCase(scheduledSmsRepository)
        appContainer =
            AppContainer(
                ruleRepository = repository,
                forwardHistoryRepository = forwardHistoryRepository,
                scheduledSmsRepository = scheduledSmsRepository,
                scheduledSmsHistoryRepository = scheduledSmsHistoryRepository,
                contactNameResolver = contactNameResolver,
                scheduledSmsScheduler = scheduledSmsScheduler,
                analyticsTracker = analyticsTracker,
                crashReporter = crashReporter,
                getRulesUseCase = GetRulesUseCase(repository),
                getCategoriesUseCase = GetCategoriesUseCase(repository),
                getRuleUseCase = GetRuleUseCase(repository),
                addRuleUseCase = AddRuleUseCase(repository, analyticsTracker),
                updateRuleUseCase = UpdateRuleUseCase(repository, analyticsTracker),
                deleteRuleUseCase = DeleteRuleUseCase(repository, analyticsTracker),
                reorderRulesUseCase = ReorderRulesUseCase(repository, analyticsTracker),
                exportRulesUseCase = ExportRulesUseCase(repository, analyticsTracker),
                importRulesUseCase = ImportRulesUseCase(repository, analyticsTracker),
                getSendWhileRoamingUseCase = GetSendWhileRoamingUseCase(settingsRepository),
                setSendWhileRoamingUseCase = SetSendWhileRoamingUseCase(settingsRepository, analyticsTracker),
                getKeepHistoryUseCase = GetKeepHistoryUseCase(settingsRepository),
                setKeepHistoryUseCase = SetKeepHistoryUseCase(settingsRepository, analyticsTracker),
                getDefaultPrefixUseCase = GetDefaultPrefixUseCase(settingsRepository),
                setDefaultPrefixUseCase = SetDefaultPrefixUseCase(settingsRepository),
                getDefaultPrefixNowUseCase = GetDefaultPrefixNowUseCase(settingsRepository),
                getForwardPrefixUseCase = GetForwardPrefixUseCase(settingsRepository),
                setForwardPrefixUseCase = SetForwardPrefixUseCase(settingsRepository),
                getForwardHistoryUseCase = GetForwardHistoryUseCase(forwardHistoryRepository),
                deleteAllHistoryUseCase = DeleteAllHistoryUseCase(forwardHistoryRepository),
                getShowNotificationsUseCase = GetShowNotificationsUseCase(settingsRepository),
                setShowNotificationsUseCase = SetShowNotificationsUseCase(settingsRepository, analyticsTracker),
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
                        getShowNotificationsNowUseCase = getShowNotificationsNowUseCase,
                        analyticsTracker = analyticsTracker,
                        crashReporter = crashReporter,
                    ),
                getScheduledSmsListUseCase = GetScheduledSmsListUseCase(scheduledSmsRepository),
                getScheduledSmsCategoriesUseCase = GetScheduledSmsCategoriesUseCase(scheduledSmsRepository),
                getScheduledSmsUseCase = getScheduledSmsUseCase,
                addScheduledSmsUseCase =
                    AddScheduledSmsUseCase(scheduledSmsRepository, scheduledSmsScheduler, analyticsTracker),
                updateScheduledSmsUseCase =
                    UpdateScheduledSmsUseCase(scheduledSmsRepository, scheduledSmsScheduler, analyticsTracker),
                deleteScheduledSmsUseCase =
                    DeleteScheduledSmsUseCase(scheduledSmsRepository, scheduledSmsScheduler, analyticsTracker),
                reorderScheduledSmsUseCase = ReorderScheduledSmsUseCase(scheduledSmsRepository),
                getScheduledSmsHistoryUseCase = GetScheduledSmsHistoryUseCase(scheduledSmsHistoryRepository),
                deleteAllScheduledSmsHistoryUseCase = DeleteAllScheduledSmsHistoryUseCase(scheduledSmsHistoryRepository),
                getKeepScheduleHistoryUseCase = GetKeepScheduleHistoryUseCase(settingsRepository),
                setKeepScheduleHistoryUseCase = SetKeepScheduleHistoryUseCase(settingsRepository, analyticsTracker),
                scheduledSmsSender =
                    ScheduledSmsSender(
                        getScheduledSmsUseCase = getScheduledSmsUseCase,
                        recordScheduledSmsHistoryUseCase = recordScheduledSmsHistoryUseCase,
                        getKeepScheduleHistoryNowUseCase = getKeepScheduleHistoryNowUseCase,
                        getShowNotificationsNowUseCase = getShowNotificationsNowUseCase,
                        contactNameResolver = contactNameResolver,
                        scheduler = scheduledSmsScheduler,
                        analyticsTracker = analyticsTracker,
                        crashReporter = crashReporter,
                    ),
            )
    }
}

data class AppContainer(
    val ruleRepository: RuleRepository,
    val forwardHistoryRepository: ForwardHistoryRepository,
    val scheduledSmsRepository: ScheduledSmsRepository,
    val scheduledSmsHistoryRepository: ScheduledSmsHistoryRepository,
    val contactNameResolver: ContactNameResolver,
    val scheduledSmsScheduler: ScheduledSmsScheduler,
    val analyticsTracker: AnalyticsTracker,
    val crashReporter: CrashReporter,
    val getRulesUseCase: GetRulesUseCase,
    val getCategoriesUseCase: GetCategoriesUseCase,
    val getRuleUseCase: GetRuleUseCase,
    val addRuleUseCase: AddRuleUseCase,
    val updateRuleUseCase: UpdateRuleUseCase,
    val deleteRuleUseCase: DeleteRuleUseCase,
    val reorderRulesUseCase: ReorderRulesUseCase,
    val exportRulesUseCase: ExportRulesUseCase,
    val importRulesUseCase: ImportRulesUseCase,
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
    val deleteAllHistoryUseCase: DeleteAllHistoryUseCase,
    val getShowNotificationsUseCase: GetShowNotificationsUseCase,
    val setShowNotificationsUseCase: SetShowNotificationsUseCase,
    val findMatchingRuleUseCase: FindMatchingRuleUseCase,
    val canForwardSmsUseCase: CanForwardSmsUseCase,
    val smsForwarder: SmsForwarder,
    val getScheduledSmsListUseCase: GetScheduledSmsListUseCase,
    val getScheduledSmsCategoriesUseCase: GetScheduledSmsCategoriesUseCase,
    val getScheduledSmsUseCase: GetScheduledSmsUseCase,
    val addScheduledSmsUseCase: AddScheduledSmsUseCase,
    val updateScheduledSmsUseCase: UpdateScheduledSmsUseCase,
    val deleteScheduledSmsUseCase: DeleteScheduledSmsUseCase,
    val reorderScheduledSmsUseCase: ReorderScheduledSmsUseCase,
    val getScheduledSmsHistoryUseCase: GetScheduledSmsHistoryUseCase,
    val deleteAllScheduledSmsHistoryUseCase: DeleteAllScheduledSmsHistoryUseCase,
    val getKeepScheduleHistoryUseCase: GetKeepScheduleHistoryUseCase,
    val setKeepScheduleHistoryUseCase: SetKeepScheduleHistoryUseCase,
    val scheduledSmsSender: ScheduledSmsSender,
)
