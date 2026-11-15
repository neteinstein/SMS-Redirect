package com.neteinstein.smsredirect

import android.Manifest
import android.annotation.SuppressLint
import android.app.LocaleManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neteinstein.smsredirect.domain.model.LandingScreen
import com.neteinstein.smsredirect.domain.model.OutgoingAttachment
import com.neteinstein.smsredirect.presentation.backup.BackupScreen
import com.neteinstein.smsredirect.presentation.backup.BackupViewModel
import com.neteinstein.smsredirect.presentation.backup.BackupViewModelFactory
import com.neteinstein.smsredirect.presentation.conversation.ConversationScreen
import com.neteinstein.smsredirect.presentation.conversation.ConversationViewModel
import com.neteinstein.smsredirect.presentation.conversation.ConversationViewModelFactory
import com.neteinstein.smsredirect.presentation.history.HistoryScreen
import com.neteinstein.smsredirect.presentation.history.HistoryViewModel
import com.neteinstein.smsredirect.presentation.history.HistoryViewModelFactory
import com.neteinstein.smsredirect.presentation.home.HomeScreen
import com.neteinstein.smsredirect.presentation.home.HomeViewModel
import com.neteinstein.smsredirect.presentation.home.HomeViewModelFactory
import com.neteinstein.smsredirect.presentation.messages.ConversationListScreen
import com.neteinstein.smsredirect.presentation.messages.ConversationListViewModel
import com.neteinstein.smsredirect.presentation.messages.ConversationListViewModelFactory
import com.neteinstein.smsredirect.presentation.messages.DefaultSmsAppPrompt
import com.neteinstein.smsredirect.presentation.navigation.MainBottomBar
import com.neteinstein.smsredirect.presentation.navigation.MainTab
import com.neteinstein.smsredirect.presentation.navigation.Routes
import com.neteinstein.smsredirect.presentation.navigation.availableTabs
import com.neteinstein.smsredirect.presentation.navigation.toMainTab
import com.neteinstein.smsredirect.presentation.newmessage.NewMessageScreen
import com.neteinstein.smsredirect.presentation.newmessage.NewMessageViewModel
import com.neteinstein.smsredirect.presentation.newmessage.NewMessageViewModelFactory
import com.neteinstein.smsredirect.presentation.restore.RestoreScreen
import com.neteinstein.smsredirect.presentation.restore.RestoreViewModel
import com.neteinstein.smsredirect.presentation.restore.RestoreViewModelFactory
import com.neteinstein.smsredirect.presentation.rule.RuleScreen
import com.neteinstein.smsredirect.presentation.rule.RuleViewModel
import com.neteinstein.smsredirect.presentation.rule.RuleViewModelFactory
import com.neteinstein.smsredirect.presentation.schedule.ScheduleFormScreen
import com.neteinstein.smsredirect.presentation.schedule.ScheduleFormViewModel
import com.neteinstein.smsredirect.presentation.schedule.ScheduleFormViewModelFactory
import com.neteinstein.smsredirect.presentation.schedule.ScheduleListScreen
import com.neteinstein.smsredirect.presentation.schedule.ScheduleListViewModel
import com.neteinstein.smsredirect.presentation.schedule.ScheduleListViewModelFactory
import com.neteinstein.smsredirect.presentation.schedulehistory.ScheduleHistoryScreen
import com.neteinstein.smsredirect.presentation.schedulehistory.ScheduleHistoryViewModel
import com.neteinstein.smsredirect.presentation.schedulehistory.ScheduleHistoryViewModelFactory
import com.neteinstein.smsredirect.presentation.settings.SettingsScreen
import com.neteinstein.smsredirect.presentation.settings.SettingsViewModel
import com.neteinstein.smsredirect.presentation.settings.SettingsViewModelFactory
import com.neteinstein.smsredirect.presentation.splash.SplashScreen
import com.neteinstein.smsredirect.presentation.theme.SmsRedirectTheme
import com.neteinstein.smsredirect.presentation.update.AppUpdateViewModel
import com.neteinstein.smsredirect.presentation.update.AppUpdateViewModelFactory
import com.neteinstein.smsredirect.presentation.update.UpdateAvailableBanner
import com.neteinstein.smsredirect.presentation.update.UpdatesSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.io.File

class MainActivity : ComponentActivity() {
    /** Granted together with the default SMS role. */
    private val smsPermissions =
        arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.RECEIVE_WAP_PUSH,
        )

    /**
     * What Redirect, Schedule and Backup need. Requested on their own only after the user has
     * declined the default SMS role; Messages and Restore need the role itself.
     */
    private val toolSmsPermissions =
        arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)

    /** Not SMS permissions; asked for right after the role is granted. */
    private val afterRolePermissions =
        arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS)

    private val composeIntentFlow = MutableStateFlow<ComposeRequest?>(null)

    private val newIntentFlow = MutableStateFlow<Intent?>(null)

    /** True when the app was opened from the monthly "open the app" reminder. */
    private val openedFromReminderFlow = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        dismissAllNotifications()
        if (savedInstanceState == null) {
            composeIntentFlow.value = ComposeRequest.from(intent)
            openedFromReminderFlow.value = intent.getBooleanExtra(KeepAliveNotifications.EXTRA_FROM_REMINDER, false)
        }

        setContent {
            SmsRedirectTheme {
                val appContainer = (application as SmsRedirectApplication).appContainer
                val features = appContainer.features
                val navController = rememberNavController()
                val landingScreen by
                    appContainer.getLandingScreenUseCase().collectAsState(initial = LandingScreen.MESSAGES)

                // Activity-scoped so the home banner and the Settings section share one state.
                // Creating it runs the launch-time update check (at most once a day).
                val appUpdateViewModel: AppUpdateViewModel? =
                    appContainer.appUpdate?.let { update ->
                        viewModel(
                            factory =
                                AppUpdateViewModelFactory(
                                    update.currentVersionName,
                                    update.getAvailableAppUpdateUseCase,
                                    update.checkForAppUpdateUseCase,
                                    update.installer,
                                ),
                        )
                    }
                val appUpdateState = appUpdateViewModel?.uiState?.collectAsState()
                val updateBanner: @Composable () -> Unit = {
                    appUpdateState?.value?.availableUpdate?.let { update ->
                        UpdateAvailableBanner(
                            versionName = update.versionName,
                            onOpenUpdates = { navController.navigate(Routes.Settings) },
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    newIntentFlow.collectLatest { intent ->
                        val deepLinkRoute = resolveDeepLinkRoute(intent)
                        if (deepLinkRoute != null) {
                            navController.navigate(deepLinkRoute) {
                                popUpTo(Routes.Home) { inclusive = false }
                            }
                            newIntentFlow.value = null
                        }
                    }
                }

                // First open from the monthly reminder: explain that the reminder (and Android's
                // clean-up of unused apps) can be switched off. Shown once, and only if the user
                // has not already exempted the app.
                var showUnusedAppInfo by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    openedFromReminderFlow.collect { fromReminder ->
                        if (fromReminder) {
                            openedFromReminderFlow.value = false
                            if (appContainer.shouldShowUnusedAppInfoUseCase()) {
                                appContainer.markUnusedAppInfoShownUseCase()
                                showUnusedAppInfo = true
                            }
                        }
                    }
                }
                if (showUnusedAppInfo) {
                    AlertDialog(
                        onDismissRequest = { showUnusedAppInfo = false },
                        title = { Text(stringResource(R.string.unused_app_info_title)) },
                        text = { Text(stringResource(R.string.unused_app_info_message)) },
                        dismissButton = {
                            TextButton(onClick = { showUnusedAppInfo = false }) {
                                Text(stringResource(R.string.unused_app_info_dismiss))
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showUnusedAppInfo = false
                                openUnusedAppSettings()
                            }) { Text(stringResource(R.string.unused_app_info_open_settings)) }
                        },
                    )
                }

                var showBatteryDialog by remember { mutableStateOf(false) }
                var pendingSmsAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                var pendingAllowsPermissionsOnly by remember { mutableStateOf(false) }
                var showDefaultSmsRationale by remember { mutableStateOf(false) }
                var showDefaultSmsDeclined by remember { mutableStateOf(false) }
                var showSmsDenied by remember { mutableStateOf(false) }
                val showSideloadDialog = remember { mutableStateOf(shouldShowSideloadWarning()) }

                val lifecycleOwnerTop = LocalLifecycleOwner.current
                var appResumeTick by remember { mutableIntStateOf(0) }
                DisposableEffect(lifecycleOwnerTop) {
                    val observer =
                        LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                appResumeTick++
                            }
                        }
                    lifecycleOwnerTop.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwnerTop.lifecycle.removeObserver(observer)
                    }
                }
                var showBatteryBanner by
                    remember(appResumeTick) {
                        mutableStateOf(isBatteryBannerEnabled() && shouldShowBatteryOptimizationDialog())
                    }

                // ---- Default SMS app gate -------------------------------------------------
                // SMS Toolbox is a messaging app. The default SMS role is always requested *before*
                // any SMS permission (Play's Default SMS handler policy), and the SMS permissions
                // come with it. Messages and Restore need the role itself and are shown disabled
                // without it; only if the user declines it are the permissions Redirect, Schedule
                // and Backup need requested on their own. Both builds behave the same.
                val defaultSmsAppChecker = appContainer.defaultSmsAppChecker
                var roleCheckTick by remember { mutableIntStateOf(0) }
                val isDefaultSmsApp =
                    remember(appResumeTick, roleCheckTick) { defaultSmsAppChecker.isDefaultSmsApp() }

                val runPendingSmsAction = {
                    val action = pendingSmsAction
                    pendingSmsAction = null
                    action?.invoke()
                }
                // Asked right after the role is granted: the role carries the SMS permissions,
                // contacts give the inbox names, notifications announce incoming messages.
                val afterRolePermissionsLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                        if (hasSmsPermissions()) runPendingSmsAction() else pendingSmsAction = null
                    }
                val permissionsOnlyLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                        if (hasToolSmsPermissions()) {
                            runPendingSmsAction()
                        } else {
                            pendingSmsAction = null
                            showSmsDenied = true
                        }
                    }
                val roleLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        roleCheckTick++
                        if (defaultSmsAppChecker.isDefaultSmsApp()) {
                            val missing = (smsPermissions + afterRolePermissions).filterNot(::isGranted)
                            if (missing.isEmpty()) runPendingSmsAction() else afterRolePermissionsLauncher.launch(missing.toTypedArray())
                        } else {
                            showDefaultSmsDeclined = true
                        }
                    }
                val launchDefaultSmsRoleRequest = {
                    val roleManager = getSystemService(RoleManager::class.java)
                    if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                        roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                    } else {
                        // No SMS role on this device (e.g. a tablet without telephony).
                        showDefaultSmsDeclined = true
                    }
                }

                /** For messaging and restore: only the default SMS app can do them at all. */
                val requireDefaultSmsApp: (() -> Unit) -> Unit = { action ->
                    if (defaultSmsAppChecker.isDefaultSmsApp() && hasSmsPermissions()) {
                        action()
                    } else {
                        pendingSmsAction = action
                        pendingAllowsPermissionsOnly = false
                        showDefaultSmsRationale = true
                    }
                }

                /**
                 * For Redirect, Schedule and Backup, which also work without the role: the role is
                 * still offered first, and only if the user declines it are the SMS permissions
                 * these tools need requested on their own.
                 */
                val requireSmsAccess: (() -> Unit) -> Unit = { action ->
                    if (hasToolSmsPermissions()) {
                        action()
                    } else {
                        pendingSmsAction = action
                        pendingAllowsPermissionsOnly = true
                        showDefaultSmsRationale = true
                    }
                }

                if (showDefaultSmsRationale) {
                    AlertDialog(
                        onDismissRequest = {
                            showDefaultSmsRationale = false
                            pendingSmsAction = null
                        },
                        title = { Text(stringResource(R.string.default_sms_rationale_title)) },
                        text = { Text(stringResource(R.string.default_sms_rationale_message)) },
                        dismissButton = {
                            TextButton(onClick = {
                                showDefaultSmsRationale = false
                                pendingSmsAction = null
                            }) { Text(stringResource(R.string.default_sms_rationale_not_now)) }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showDefaultSmsRationale = false
                                launchDefaultSmsRoleRequest()
                            }) { Text(stringResource(R.string.default_sms_rationale_confirm)) }
                        },
                    )
                }

                if (showDefaultSmsDeclined) {
                    val offerPermissionsOnly = pendingAllowsPermissionsOnly && pendingSmsAction != null
                    AlertDialog(
                        onDismissRequest = {
                            showDefaultSmsDeclined = false
                            pendingSmsAction = null
                        },
                        title = { Text(stringResource(R.string.default_sms_declined_title)) },
                        text = {
                            Text(
                                stringResource(
                                    if (offerPermissionsOnly) {
                                        R.string.default_sms_declined_message_tools
                                    } else {
                                        R.string.default_sms_declined_message
                                    },
                                ),
                            )
                        },
                        dismissButton =
                            if (offerPermissionsOnly) {
                                {
                                    TextButton(onClick = {
                                        showDefaultSmsDeclined = false
                                        permissionsOnlyLauncher.launch(toolSmsPermissions)
                                    }) { Text(stringResource(R.string.default_sms_declined_permissions_only)) }
                                }
                            } else {
                                null
                            },
                        confirmButton = {
                            TextButton(onClick = {
                                showDefaultSmsDeclined = false
                                pendingSmsAction = null
                            }) { Text(stringResource(R.string.action_ok)) }
                        },
                    )
                }

                if (showSmsDenied) {
                    AlertDialog(
                        onDismissRequest = { showSmsDenied = false },
                        title = { Text(stringResource(R.string.sms_permission_denied_title)) },
                        text = { Text(stringResource(R.string.sms_permission_denied_message)) },
                        confirmButton = {
                            TextButton(onClick = { showSmsDenied = false }) {
                                Text(stringResource(R.string.action_ok))
                            }
                        },
                    )
                }

                // Opens a pre-filled conversation for sms:/smsto: links and Share.
                LaunchedEffect(Unit) {
                    composeIntentFlow.collectLatest { compose ->
                        if (compose == null) return@collectLatest
                        composeIntentFlow.value = null
                        // Wait for the splash to hand over to Home before navigating.
                        while (navController.currentDestination?.route == Routes.Splash ||
                            navController.currentDestination == null
                        ) {
                            delay(50)
                        }
                        navController.navigate(compose.route())
                    }
                }

                if (showSideloadDialog.value) {
                    AlertDialog(
                        onDismissRequest = {
                            dismissSideloadWarning()
                            showSideloadDialog.value = false
                        },
                        title = { Text(stringResource(R.string.sideload_warning_title)) },
                        text = { Text(stringResource(R.string.sideload_warning_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                dismissSideloadWarning()
                                showSideloadDialog.value = false
                                openAppSettings()
                            }) {
                                Text(stringResource(R.string.action_ok))
                            }
                        },
                    )
                }

                if (showBatteryDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showBatteryDialog = false
                            setBatteryBannerEnabled(true)
                            showBatteryBanner = true
                        },
                        title = { Text(stringResource(R.string.battery_optimization_title)) },
                        text = { Text(stringResource(R.string.battery_optimization_message)) },
                        dismissButton = {
                            TextButton(onClick = {
                                showBatteryDialog = false
                                setBatteryBannerEnabled(true)
                                showBatteryBanner = true
                            }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showBatteryDialog = false
                                openBatteryOptimizationSettings()
                            }) {
                                Text(stringResource(R.string.action_proceed))
                            }
                        },
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.Splash,
                    enterTransition = { fadeIn(animationSpec = tween(220)) },
                    exitTransition = { fadeOut(animationSpec = tween(220)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                    popExitTransition = { fadeOut(animationSpec = tween(220)) },
                ) {
                    composable(Routes.Splash) {
                        SplashScreen()
                        LaunchedEffect(Unit) {
                            delay(1200)
                            navController.navigate(Routes.Home) {
                                popUpTo(Routes.Splash) { inclusive = true }
                            }
                            resolveDeepLinkRoute(intent)?.let { navController.navigate(it) }
                        }
                    }

                    composable(Routes.Home) {
                        val tabs = remember(features) { features.availableTabs() }
                        var selectedTab by
                            rememberSaveable {
                                mutableStateOf(features.resolveLandingScreen(landingScreen).toMainTab())
                            }

                        // First launch: offer to become the default SMS app straight away. That is
                        // what the Messages tab needs, and the role is always requested before any
                        // SMS permission (Play's Default SMS handler policy).
                        LaunchedEffect(Unit) {
                            if (!isDefaultSmsApp && !hasPromptedForDefaultSmsApp()) {
                                markPromptedForDefaultSmsApp()
                                pendingSmsAction = null
                                pendingAllowsPermissionsOnly = false
                                showDefaultSmsRationale = true
                            }
                        }

                        val requestCreate: (String) -> Unit = { route ->
                            requireSmsAccess { navController.navigate(route) }
                        }

                        // Posted on first use of Backup/Restore: without it the progress
                        // notifications would silently never appear. The result is not acted on.
                        val notificationsLauncher =
                            rememberLauncherForActivityResult(
                                ActivityResultContracts.RequestPermission(),
                            ) { }
                        val requestNotificationsIfMissing = {
                            if (!isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
                                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }

                        val bottomBar: @Composable () -> Unit = {
                            if (tabs.size > 1) {
                                MainBottomBar(
                                    selectedTab = selectedTab,
                                    onTabSelected = { selectedTab = it },
                                    onFabClick = {
                                        when (selectedTab) {
                                            MainTab.MESSAGES -> requireDefaultSmsApp { navController.navigate(Routes.NewMessage) }
                                            MainTab.REDIRECT -> requestCreate(Routes.ruleRoute())
                                            MainTab.SCHEDULE -> requestCreate(Routes.scheduleRoute())
                                            MainTab.BACKUP, MainTab.RESTORE -> Unit
                                        }
                                    },
                                    fabContentDescription =
                                        when (selectedTab) {
                                            MainTab.MESSAGES -> stringResource(R.string.messages_cd_new_message)
                                            MainTab.REDIRECT -> stringResource(R.string.home_cd_add_rule)
                                            MainTab.SCHEDULE -> stringResource(R.string.schedule_cd_add)
                                            MainTab.BACKUP, MainTab.RESTORE -> ""
                                        },
                                    showFab =
                                        when (selectedTab) {
                                            MainTab.MESSAGES -> isDefaultSmsApp
                                            MainTab.REDIRECT, MainTab.SCHEDULE -> true
                                            MainTab.BACKUP, MainTab.RESTORE -> false
                                        },
                                    tabs = tabs,
                                )
                            }
                        }

                        when (selectedTab) {
                            MainTab.MESSAGES -> {
                                val conversationListViewModel: ConversationListViewModel =
                                    viewModel(
                                        factory =
                                            ConversationListViewModelFactory(
                                                appContainer.getConversationsUseCase,
                                                appContainer.deleteConversationUseCase,
                                                appContainer.contactNameResolver,
                                            ),
                                    )
                                val conversationsState by conversationListViewModel.uiState.collectAsState()
                                // Nothing is read from the SMS store until the app holds the role.
                                // Without the role the list is shown disabled; it can still be read
                                // when the user granted SMS access for the other tools.
                                LaunchedEffect(isDefaultSmsApp) {
                                    if (isGranted(Manifest.permission.READ_SMS)) {
                                        conversationListViewModel.start()
                                    }
                                }
                                ConversationListScreen(
                                    state = conversationsState,
                                    isDefaultSmsApp = isDefaultSmsApp,
                                    onSetDefaultSmsApp = { requireDefaultSmsApp { } },
                                    onOpenConversation = { navController.navigate(Routes.conversationRoute(it)) },
                                    onDeleteConversation = conversationListViewModel::deleteConversation,
                                    onOpenSettings = { navController.navigate(Routes.Settings) },
                                    bottomBar = bottomBar,
                                    showBackgroundWarning = showBatteryBanner,
                                    onFixBackgroundWarning = { openBatteryOptimizationSettings() },
                                    topBanner = updateBanner,
                                )
                            }

                            MainTab.BACKUP -> {
                                val backupViewModel: BackupViewModel =
                                    viewModel(
                                        factory =
                                            BackupViewModelFactory(
                                                appContainer.getSmsBackupSettingsUseCase,
                                                appContainer.setBackupSubjectTemplateUseCase,
                                                appContainer.setBackupGmailLabelUseCase,
                                                appContainer.setBackupPreviousEnabledUseCase,
                                                appContainer.setBackupEnabledUseCase,
                                                appContainer.smsBackupScheduler,
                                            ),
                                    )
                                val backupState by backupViewModel.uiState.collectAsState()
                                val needsCredentials by backupViewModel.needsCredentials.collectAsState()
                                BackupScreen(
                                    state = backupState,
                                    needsCredentials = needsCredentials,
                                    onSubjectTemplateSaved = backupViewModel::onSubjectTemplateSaved,
                                    onGmailLabelSaved = backupViewModel::onGmailLabelSaved,
                                    onBackupPreviousChanged = backupViewModel::onBackupPreviousChanged,
                                    onToggleBackupEnabled = {
                                        // Backup reads the SMS store, so it needs READ_SMS -- which
                                        // comes with the default SMS role.
                                        requireSmsAccess {
                                            if (!backupState.settings.backupEnabled) requestNotificationsIfMissing()
                                            backupViewModel.onToggleBackupEnabled()
                                        }
                                    },
                                    onOpenSettings = { navController.navigate(Routes.Settings) },
                                    bottomBar = bottomBar,
                                    showBackgroundWarning = showBatteryBanner,
                                    topBanner = updateBanner,
                                    onFixBackgroundWarning = { openBatteryOptimizationSettings() },
                                )
                            }

                            MainTab.RESTORE -> {
                                val restoreViewModel: RestoreViewModel =
                                    viewModel(
                                        factory =
                                            RestoreViewModelFactory(
                                                appContainer.getSmsBackupSettingsUseCase,
                                                appContainer.smsRestoreScheduler,
                                                appContainer.defaultSmsAppChecker,
                                            ),
                                    )
                                val restoreState by restoreViewModel.uiState.collectAsState()
                                val defaultSmsRoleDenied by restoreViewModel.defaultSmsRoleDenied.collectAsState()
                                LaunchedEffect(isDefaultSmsApp) { restoreViewModel.refreshDefaultSmsApp() }
                                RestoreScreen(
                                    state = restoreState,
                                    defaultSmsRoleDenied = defaultSmsRoleDenied,
                                    onRestoreConfirmed = {
                                        // Only the default SMS app can write to the SMS store.
                                        requireDefaultSmsApp {
                                            requestNotificationsIfMissing()
                                            restoreViewModel.onRestoreConfirmed()
                                        }
                                    },
                                    onCancelRestore = restoreViewModel::onCancelRestore,
                                    onSetDefaultSmsApp = { requireDefaultSmsApp { } },
                                    onRestoreResultDismissed = restoreViewModel::onRestoreResultDismissed,
                                    onOpenSettings = { navController.navigate(Routes.Settings) },
                                    bottomBar = bottomBar,
                                    topBanner = updateBanner,
                                )
                            }

                            MainTab.REDIRECT -> {
                                val homeViewModel: HomeViewModel =
                                    viewModel(
                                        factory =
                                            HomeViewModelFactory(
                                                appContainer.getRulesUseCase,
                                                appContainer.deleteRuleUseCase,
                                                appContainer.reorderRulesUseCase,
                                                appContainer.contactNameResolver,
                                            ),
                                    )
                                val rules by homeViewModel.rules.collectAsState()
                                val contactNames by homeViewModel.contactNames.collectAsState()
                                HomeScreen(
                                    rules = rules,
                                    contactNames = contactNames,
                                    onAddRule = { requestCreate(Routes.ruleRoute()) },
                                    onOpenHistory = { navController.navigate(Routes.History) },
                                    onOpenSettings = { navController.navigate(Routes.Settings) },
                                    onEditRule = { navController.navigate(Routes.ruleRoute(it)) },
                                    onDeleteRule = homeViewModel::deleteRule,
                                    onMoveRule = homeViewModel::moveRule,
                                    onDragEnd = homeViewModel::commitReorder,
                                    bottomBar = bottomBar,
                                    showBackgroundWarning = showBatteryBanner,
                                    topBanner = updateBanner,
                                    onFixBackgroundWarning = { openBatteryOptimizationSettings() },
                                )
                            }

                            MainTab.SCHEDULE -> {
                                val scheduleListViewModel: ScheduleListViewModel =
                                    viewModel(
                                        factory =
                                            ScheduleListViewModelFactory(
                                                appContainer.getScheduledSmsListUseCase,
                                                appContainer.deleteScheduledSmsUseCase,
                                                appContainer.reorderScheduledSmsUseCase,
                                                appContainer.contactNameResolver,
                                            ),
                                    )
                                val scheduledSmsList by scheduleListViewModel.scheduledSmsList.collectAsState()
                                val contactNames by scheduleListViewModel.contactNames.collectAsState()
                                ScheduleListScreen(
                                    scheduledSmsList = scheduledSmsList,
                                    contactNames = contactNames,
                                    onAddScheduledSms = { requestCreate(Routes.scheduleRoute()) },
                                    onOpenHistory = { navController.navigate(Routes.ScheduleHistory) },
                                    onOpenSettings = { navController.navigate(Routes.Settings) },
                                    onEditScheduledSms = { navController.navigate(Routes.scheduleRoute(it)) },
                                    onDeleteScheduledSms = scheduleListViewModel::deleteScheduledSms,
                                    onMoveScheduledSms = scheduleListViewModel::moveScheduledSms,
                                    onDragEnd = scheduleListViewModel::commitReorder,
                                    bottomBar = bottomBar,
                                    showBackgroundWarning = showBatteryBanner,
                                    topBanner = updateBanner,
                                    onFixBackgroundWarning = { openBatteryOptimizationSettings() },
                                )
                            }
                        }
                    }

                    composable(
                        route = Routes.NewMessageWithArgs,
                        arguments =
                            listOf(
                                navArgument(Routes.DraftArg) { nullableArgs() },
                                navArgument(Routes.AttachmentArg) { nullableArgs() },
                                navArgument(Routes.AttachmentTypeArg) { nullableArgs() },
                            ),
                    ) { backStackEntry ->
                        val args = backStackEntry.arguments
                        val newMessageViewModel: NewMessageViewModel = viewModel(factory = NewMessageViewModelFactory())
                        val state by newMessageViewModel.uiState.collectAsState()
                        // ACTION_PICK on phone numbers grants read access to the one row picked,
                        // so no READ_CONTACTS prompt is needed here.
                        val pickPhoneLauncher =
                            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                                result.data?.data?.let(::resolvePickedPhone)?.let { (number, name) ->
                                    newMessageViewModel.onContactPicked(number, name)
                                }
                            }
                        if (!isDefaultSmsApp) {
                            DefaultSmsAppPrompt(onSetDefaultSmsApp = { requireDefaultSmsApp { } })
                        } else {
                            NewMessageScreen(
                                state = state,
                                onInputChanged = newMessageViewModel::onInputChanged,
                                onCommitInput = { newMessageViewModel.commitInput() },
                                onRemoveRecipient = newMessageViewModel::onRecipientRemoved,
                                onPickContact = {
                                    runCatching {
                                        pickPhoneLauncher.launch(
                                            Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE),
                                        )
                                    }
                                },
                                onNext = {
                                    if (newMessageViewModel.commitInput()) {
                                        val addresses = newMessageViewModel.uiState.value.effectiveAddresses
                                        navController.navigate(
                                            Routes.conversationRoute(
                                                addresses,
                                                draft = args?.getString(Routes.DraftArg),
                                                attachment = args?.getString(Routes.AttachmentArg),
                                                attachmentType = args?.getString(Routes.AttachmentTypeArg),
                                            ),
                                        ) { popUpTo(Routes.NewMessageWithArgs) { inclusive = true } }
                                    }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }

                    composable(
                        route = Routes.ConversationWithArgs,
                        arguments =
                            listOf(
                                navArgument(Routes.ThreadIdArg) {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                },
                                navArgument(Routes.AddressesArg) { nullableArgs() },
                                navArgument(Routes.DraftArg) { nullableArgs() },
                                navArgument(Routes.AttachmentArg) { nullableArgs() },
                                navArgument(Routes.AttachmentTypeArg) { nullableArgs() },
                            ),
                    ) { backStackEntry ->
                        val args = backStackEntry.arguments
                        val threadId = args?.getLong(Routes.ThreadIdArg) ?: 0L
                        if (!isDefaultSmsApp) {
                            DefaultSmsAppPrompt(onSetDefaultSmsApp = { requireDefaultSmsApp { } })
                            return@composable
                        }
                        val conversationViewModel: ConversationViewModel =
                            viewModel(
                                factory =
                                    ConversationViewModelFactory(
                                        threadId = threadId,
                                        addresses =
                                            args
                                                ?.getString(Routes.AddressesArg)
                                                ?.split(Routes.ADDRESS_SEPARATOR)
                                                ?.filter { it.isNotBlank() }
                                                .orEmpty(),
                                        draft = args?.getString(Routes.DraftArg),
                                        attachment =
                                            args?.getString(Routes.AttachmentArg)?.let { uri ->
                                                outgoingAttachmentFor(uri.toUri(), args.getString(Routes.AttachmentTypeArg))
                                            },
                                        getConversationMessagesUseCase = appContainer.getConversationMessagesUseCase,
                                        getConversationAddressesUseCase = appContainer.getConversationAddressesUseCase,
                                        getOrCreateConversationUseCase = appContainer.getOrCreateConversationUseCase,
                                        markConversationReadUseCase = appContainer.markConversationReadUseCase,
                                        sendMessageUseCase = appContainer.sendMessageUseCase,
                                        deleteMessageUseCase = appContainer.deleteMessageUseCase,
                                        contactNameResolver = appContainer.contactNameResolver,
                                        downloadMmsUseCase = appContainer.downloadMmsUseCase,
                                    ),
                            )
                        val state by conversationViewModel.uiState.collectAsState()

                        // While this conversation is on screen its messages are read as they
                        // arrive, so they get no notification.
                        val lifecycleOwner = LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner, state.threadId) {
                            val observer =
                                LifecycleEventObserver { _, event ->
                                    when (event) {
                                        Lifecycle.Event.ON_RESUME -> {
                                            MessageNotifications.visibleThreadId = state.threadId
                                            MessageNotifications.cancel(this@MainActivity, state.threadId)
                                            conversationViewModel.onVisible(true)
                                        }

                                        Lifecycle.Event.ON_PAUSE -> {
                                            MessageNotifications.visibleThreadId = null
                                            conversationViewModel.onVisible(false)
                                        }

                                        else -> {
                                            Unit
                                        }
                                    }
                                }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                                MessageNotifications.visibleThreadId = null
                            }
                        }

                        val pickImageLauncher =
                            rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                                uri?.let { outgoingAttachmentFor(it, null) }?.let(conversationViewModel::onAttachmentPicked)
                            }
                        val pickFileLauncher =
                            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                                uri?.let { outgoingAttachmentFor(it, null) }?.let(conversationViewModel::onAttachmentPicked)
                            }

                        ConversationScreen(
                            state = state,
                            onBack = { navController.popBackStack() },
                            onDraftChanged = conversationViewModel::onDraftChanged,
                            onSend = conversationViewModel::send,
                            onPickImage = {
                                pickImageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                                )
                            },
                            onPickFile = { runCatching { pickFileLauncher.launch(MMS_ATTACHMENT_TYPES) } },
                            onRemoveAttachment = conversationViewModel::onAttachmentRemoved,
                            onOpenAttachment = { attachment -> openAttachment(attachment.uri, attachment.contentType) },
                            onRetry = conversationViewModel::retry,
                            onDelete = conversationViewModel::delete,
                            onDownload = conversationViewModel::download,
                            onErrorShown = conversationViewModel::onErrorShown,
                        )
                    }

                    composable(Routes.Settings) {
                        val settingsViewModel: SettingsViewModel =
                            viewModel(
                                factory =
                                    SettingsViewModelFactory(
                                        appContainer.getSendWhileRoamingUseCase,
                                        appContainer.setSendWhileRoamingUseCase,
                                        appContainer.getDefaultPrefixUseCase,
                                        appContainer.setDefaultPrefixUseCase,
                                        appContainer.getForwardPrefixUseCase,
                                        appContainer.setForwardPrefixUseCase,
                                        appContainer.getShowNotificationsUseCase,
                                        appContainer.setShowNotificationsUseCase,
                                        appContainer.getLandingScreenUseCase,
                                        appContainer.setLandingScreenUseCase,
                                        appContainer.exportBackupUseCase,
                                        appContainer.importBackupUseCase,
                                        appContainer.getSmsBackupSettingsUseCase,
                                        appContainer.verifyGmailCredentialsUseCase,
                                        appContainer.clearGmailCredentialsUseCase,
                                        appContainer.smsBackupScheduler,
                                        appContainer.getMessagingSettingsUseCase,
                                        appContainer.updateMessagingSettingsUseCase,
                                    ),
                            )
                        val messagingSettings by settingsViewModel.messagingSettings.collectAsState()
                        val sendWhileRoaming by settingsViewModel.sendWhileRoaming.collectAsState()
                        val showNotifications by settingsViewModel.showNotifications.collectAsState()
                        val defaultPrefix by settingsViewModel.defaultPrefix.collectAsState()
                        val forwardPrefix by settingsViewModel.forwardPrefix.collectAsState()
                        val settingsLandingScreen by settingsViewModel.landingScreen.collectAsState()
                        val backupSettings by settingsViewModel.backupSettings.collectAsState()
                        val gmailVerification by settingsViewModel.gmailVerification.collectAsState()

                        val context = this@MainActivity

                        val lifecycleOwner = LocalLifecycleOwner.current
                        var resumeTick by remember { mutableIntStateOf(0) }
                        DisposableEffect(lifecycleOwner) {
                            val observer =
                                LifecycleEventObserver { _, event ->
                                    if (event == Lifecycle.Event.ON_RESUME) {
                                        resumeTick++
                                    }
                                }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }
                        val currentLanguageLabel =
                            remember(resumeTick) { currentAppLanguageLabel() }

                        var showNotificationsRationale by remember { mutableStateOf(false) }
                        val notificationsPermissionLauncher =
                            rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.RequestPermission(),
                            ) { granted ->
                                settingsViewModel.setShowNotifications(granted)
                            }

                        if (showNotificationsRationale) {
                            AlertDialog(
                                onDismissRequest = { showNotificationsRationale = false },
                                title = { Text(stringResource(R.string.notifications_permission_rationale_title)) },
                                text = { Text(stringResource(R.string.notifications_permission_rationale_message)) },
                                dismissButton = {
                                    TextButton(onClick = { showNotificationsRationale = false }) {
                                        Text(stringResource(R.string.action_cancel))
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showNotificationsRationale = false
                                        notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }) {
                                        Text(stringResource(R.string.action_proceed))
                                    }
                                },
                            )
                        }

                        val importLauncher =
                            rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.GetContent(),
                            ) { uri: Uri? ->
                                uri?.let {
                                    try {
                                        val json =
                                            context.contentResolver
                                                .openInputStream(it)
                                                ?.bufferedReader()
                                                ?.use { reader -> reader.readText() }
                                        if (json != null) {
                                            settingsViewModel.importBackup(json)
                                        }
                                    } catch (_: Exception) {
                                        val toast =
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.settings_import_error),
                                                Toast.LENGTH_SHORT,
                                            )
                                        toast.show()
                                    }
                                }
                            }

                        LaunchedEffect(Unit) {
                            settingsViewModel.exportEvent.collectLatest { json ->
                                val file = File(context.cacheDir, "sms-redirect-backup.json")
                                file.writeText(json)
                                val uri =
                                    androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file,
                                    )
                                val shareIntent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, null),
                                )
                            }
                        }

                        LaunchedEffect(Unit) {
                            settingsViewModel.importResult.collectLatest { result ->
                                val rulesText =
                                    context.resources.getQuantityString(
                                        R.plurals.settings_import_success_rules,
                                        result.importedRules,
                                        result.importedRules,
                                    )
                                val messagesText =
                                    context.resources.getQuantityString(
                                        R.plurals.settings_import_success_messages,
                                        result.importedScheduledSms,
                                        result.importedScheduledSms,
                                    )
                                val toast =
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.settings_import_success,
                                            rulesText,
                                            messagesText,
                                        ),
                                        Toast.LENGTH_SHORT,
                                    )
                                toast.show()
                            }
                        }

                        SettingsScreen(
                            sendWhileRoaming = sendWhileRoaming,
                            showNotifications = showNotifications,
                            forwardPrefix = forwardPrefix,
                            defaultPrefix = defaultPrefix,
                            currentLanguageLabel = currentLanguageLabel,
                            landingScreen = settingsLandingScreen,
                            onLandingScreenChanged = settingsViewModel::setLandingScreen,
                            gmailAddress = backupSettings.gmailAddress,
                            hasGmailAppPassword = backupSettings.hasAppPassword,
                            gmailVerification = gmailVerification,
                            onSaveGmailCredentials = settingsViewModel::saveGmailCredentials,
                            onClearGmailCredentials = settingsViewModel::clearGmailCredentials,
                            onOpenAppPasswordHelp = { openGoogleAppPasswordsPage() },
                            onSendWhileRoamingChanged = settingsViewModel::setSendWhileRoaming,
                            onShowNotificationsChanged = { enabled ->
                                if (enabled) {
                                    showNotificationsRationale = true
                                } else {
                                    settingsViewModel.setShowNotifications(false)
                                }
                            },
                            onForwardPrefixSaved = settingsViewModel::setForwardPrefix,
                            onDefaultPrefixChanged = settingsViewModel::setDefaultPrefix,
                            onExportBackup = settingsViewModel::exportBackup,
                            onImportBackup = { importLauncher.launch("application/json") },
                            onOpenLanguageSettings = { openAppLocaleSettings() },
                            onBack = { navController.popBackStack() },
                            messagingSettings = messagingSettings,
                            onSmsDeliveryReportsChanged = settingsViewModel::setSmsDeliveryReports,
                            onMmsDeliveryReportsChanged = settingsViewModel::setMmsDeliveryReports,
                            onMmsReadReportsChanged = settingsViewModel::setMmsReadReports,
                            availableLandingScreens = features.availableLandingScreens,
                            showMessagingSettings = features.sendsSms,
                            updatesSection =
                                appUpdateViewModel?.let { updateViewModel ->
                                    {
                                        val updateState by updateViewModel.uiState.collectAsState()
                                        UpdatesSection(
                                            state = updateState,
                                            onCheckNow = updateViewModel::checkNow,
                                            onInstall = updateViewModel::installUpdate,
                                            onOpenReleaseNotes = { url -> openUrl(url) },
                                            onOpenInstallGuide = { openUrl(INSTALL_GUIDE_URL) },
                                        )
                                    }
                                },
                        )
                    }

                    composable(
                        route = Routes.History,
                    ) {
                        val historyViewModel: HistoryViewModel =
                            viewModel(
                                factory =
                                    HistoryViewModelFactory(
                                        appContainer.getForwardHistoryUseCase,
                                        appContainer.getKeepHistoryUseCase,
                                        appContainer.setKeepHistoryUseCase,
                                        appContainer.deleteAllHistoryUseCase,
                                        appContainer.contactNameResolver,
                                    ),
                            )
                        val forwardHistory by historyViewModel.forwardHistory.collectAsState()
                        val contactNames by historyViewModel.contactNames.collectAsState()
                        val keepHistory by historyViewModel.keepHistory.collectAsState()
                        val showDisableConfirmation by historyViewModel.showDisableConfirmation.collectAsState()
                        val showClearConfirmation by historyViewModel.showClearConfirmation.collectAsState()
                        HistoryScreen(
                            forwardHistory = forwardHistory,
                            contactNames = contactNames,
                            keepHistory = keepHistory,
                            showDisableConfirmation = showDisableConfirmation,
                            showClearConfirmation = showClearConfirmation,
                            onKeepHistoryChanged = historyViewModel::onKeepHistoryToggled,
                            onConfirmDisable = historyViewModel::confirmDisableHistory,
                            onDismissDisable = historyViewModel::dismissDisableConfirmation,
                            onClearHistoryClicked = historyViewModel::onClearHistoryClicked,
                            onConfirmClear = historyViewModel::confirmClearHistory,
                            onDismissClear = historyViewModel::dismissClearConfirmation,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(
                        route = Routes.ScheduleHistory,
                    ) {
                        val scheduleHistoryViewModel: ScheduleHistoryViewModel =
                            viewModel(
                                factory =
                                    ScheduleHistoryViewModelFactory(
                                        appContainer.getScheduledSmsHistoryUseCase,
                                        appContainer.getKeepScheduleHistoryUseCase,
                                        appContainer.setKeepScheduleHistoryUseCase,
                                        appContainer.deleteAllScheduledSmsHistoryUseCase,
                                        appContainer.contactNameResolver,
                                    ),
                            )
                        val scheduledSmsHistory by scheduleHistoryViewModel.scheduledSmsHistory.collectAsState()
                        val contactNames by scheduleHistoryViewModel.contactNames.collectAsState()
                        val keepScheduleHistory by scheduleHistoryViewModel.keepScheduleHistory.collectAsState()
                        val showDisableConfirmation by scheduleHistoryViewModel.showDisableConfirmation.collectAsState()
                        val showClearConfirmation by scheduleHistoryViewModel.showClearConfirmation.collectAsState()
                        ScheduleHistoryScreen(
                            scheduledSmsHistory = scheduledSmsHistory,
                            contactNames = contactNames,
                            keepScheduleHistory = keepScheduleHistory,
                            showDisableConfirmation = showDisableConfirmation,
                            showClearConfirmation = showClearConfirmation,
                            onKeepScheduleHistoryChanged = scheduleHistoryViewModel::onKeepScheduleHistoryToggled,
                            onConfirmDisable = scheduleHistoryViewModel::confirmDisableHistory,
                            onDismissDisable = scheduleHistoryViewModel::dismissDisableConfirmation,
                            onClearHistoryClicked = scheduleHistoryViewModel::onClearHistoryClicked,
                            onConfirmClear = scheduleHistoryViewModel::confirmClearHistory,
                            onDismissClear = scheduleHistoryViewModel::dismissClearConfirmation,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(
                        route = Routes.RuleWithArg,
                        arguments =
                            listOf(
                                navArgument(Routes.RuleIdArg) {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                },
                            ),
                    ) { backStackEntry ->
                        val ruleId = backStackEntry.arguments?.getLong(Routes.RuleIdArg) ?: 0L
                        val ruleViewModel: RuleViewModel =
                            viewModel(
                                factory =
                                    RuleViewModelFactory(
                                        appContainer.getRuleUseCase,
                                        appContainer.addRuleUseCase,
                                        appContainer.updateRuleUseCase,
                                        appContainer.ruleRepository,
                                        appContainer.getDefaultPrefixNowUseCase,
                                        appContainer.getCategoriesUseCase,
                                        appContainer.contactNameResolver,
                                        defaultRuleNameProvider = { index ->
                                            getString(R.string.rule_default_name_format, index)
                                        },
                                    ),
                            )

                        val uiState by ruleViewModel.uiState.collectAsState()

                        val pickSenderContactLauncher =
                            rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.PickContact(),
                            ) { uri ->
                                if (uri != null) {
                                    val contact = resolveContact(uri)
                                    if (contact != null && contact.first.isNotBlank()) {
                                        ruleViewModel.onSenderContactPicked(contact.first, contact.second)
                                    }
                                }
                            }

                        val pickTargetContactLauncher =
                            rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.PickContact(),
                            ) { uri ->
                                if (uri != null) {
                                    val contact = resolveContact(uri)
                                    if (contact != null && contact.first.isNotBlank()) {
                                        ruleViewModel.onTargetContactPicked(contact.first, contact.second)
                                    }
                                }
                            }

                        val requestSenderContactsPermissionLauncher =
                            rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.RequestPermission(),
                            ) { granted ->
                                if (granted) {
                                    pickSenderContactLauncher.launch(null)
                                }
                            }

                        val requestTargetContactsPermissionLauncher =
                            rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.RequestPermission(),
                            ) { granted ->
                                if (granted) {
                                    pickTargetContactLauncher.launch(null)
                                }
                            }

                        var pendingContactsAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                        var showContactsRationale by remember { mutableStateOf(false) }

                        if (showContactsRationale) {
                            AlertDialog(
                                onDismissRequest = {
                                    showContactsRationale = false
                                    pendingContactsAction = null
                                },
                                title = { Text(stringResource(R.string.contacts_permission_rationale_title)) },
                                text = { Text(stringResource(R.string.contacts_permission_rationale_message)) },
                                dismissButton = {
                                    TextButton(onClick = {
                                        showContactsRationale = false
                                        pendingContactsAction = null
                                    }) {
                                        Text(stringResource(R.string.action_cancel))
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showContactsRationale = false
                                        pendingContactsAction?.invoke()
                                        pendingContactsAction = null
                                    }) {
                                        Text(stringResource(R.string.action_proceed))
                                    }
                                },
                            )
                        }

                        LaunchedEffect(ruleId) {
                            ruleViewModel.loadRule(ruleId)
                        }

                        LaunchedEffect(uiState.saved) {
                            if (uiState.saved) {
                                val wasNew = uiState.id == 0L
                                ruleViewModel.clearSavedFlag()
                                navController.popBackStack()
                                if (wasNew && shouldShowBatteryOptimizationDialog()) {
                                    showBatteryDialog = true
                                }
                            }
                        }

                        RuleScreen(
                            uiState = uiState,
                            onNameChanged = ruleViewModel::onNameChanged,
                            onCategoryChanged = ruleViewModel::onCategoryChanged,
                            onSenderChanged = ruleViewModel::onSenderChanged,
                            onTargetChanged = ruleViewModel::onTargetChanged,
                            onPickSenderContact = {
                                if (ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.READ_CONTACTS,
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    pickSenderContactLauncher.launch(null)
                                } else {
                                    pendingContactsAction = {
                                        requestSenderContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                    showContactsRationale = true
                                }
                            },
                            onPickTargetContact = {
                                if (ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.READ_CONTACTS,
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    pickTargetContactLauncher.launch(null)
                                } else {
                                    pendingContactsAction = {
                                        requestTargetContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                    showContactsRationale = true
                                }
                            },
                            onSave = {
                                ruleViewModel.saveRule(
                                    invalidTargetError = getString(R.string.rule_error_invalid_target),
                                    duplicateRuleError = getString(R.string.rule_error_duplicate),
                                )
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(
                        route = Routes.ScheduleWithArg,
                        arguments =
                            listOf(
                                navArgument(Routes.ScheduleIdArg) {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                },
                            ),
                    ) { backStackEntry ->
                        val scheduleId = backStackEntry.arguments?.getLong(Routes.ScheduleIdArg) ?: 0L
                        val scheduleFormViewModel: ScheduleFormViewModel =
                            viewModel(
                                factory =
                                    ScheduleFormViewModelFactory(
                                        appContainer.getScheduledSmsUseCase,
                                        appContainer.addScheduledSmsUseCase,
                                        appContainer.updateScheduledSmsUseCase,
                                        appContainer.getScheduledSmsCategoriesUseCase,
                                        appContainer.scheduledSmsRepository,
                                        appContainer.getDefaultPrefixNowUseCase,
                                        appContainer.contactNameResolver,
                                        defaultScheduledSmsNameProvider = { index ->
                                            getString(R.string.schedule_default_name_format, index)
                                        },
                                    ),
                            )

                        val uiState by scheduleFormViewModel.uiState.collectAsState()

                        val pickContactLauncher =
                            rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.PickContact(),
                            ) { uri ->
                                if (uri != null) {
                                    val contact = resolveContact(uri)
                                    if (contact != null && contact.first.isNotBlank()) {
                                        scheduleFormViewModel.onContactPicked(contact.first, contact.second)
                                    }
                                }
                            }

                        val requestContactsPermissionLauncher =
                            rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.RequestPermission(),
                            ) { granted ->
                                if (granted) {
                                    pickContactLauncher.launch(null)
                                }
                            }

                        var showContactsRationale by remember { mutableStateOf(false) }

                        if (showContactsRationale) {
                            AlertDialog(
                                onDismissRequest = { showContactsRationale = false },
                                title = { Text(stringResource(R.string.contacts_permission_rationale_title)) },
                                text = { Text(stringResource(R.string.contacts_permission_rationale_message)) },
                                dismissButton = {
                                    TextButton(onClick = { showContactsRationale = false }) {
                                        Text(stringResource(R.string.action_cancel))
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showContactsRationale = false
                                        requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }) {
                                        Text(stringResource(R.string.action_proceed))
                                    }
                                },
                            )
                        }

                        LaunchedEffect(scheduleId) {
                            scheduleFormViewModel.loadScheduledSms(scheduleId)
                        }

                        LaunchedEffect(uiState.saved) {
                            if (uiState.saved) {
                                val wasNew = uiState.id == 0L
                                scheduleFormViewModel.clearSavedFlag()
                                navController.popBackStack()
                                if (wasNew && shouldShowBatteryOptimizationDialog()) {
                                    showBatteryDialog = true
                                }
                            }
                        }

                        ScheduleFormScreen(
                            uiState = uiState,
                            onNameChanged = scheduleFormViewModel::onNameChanged,
                            onCategoryChanged = scheduleFormViewModel::onCategoryChanged,
                            onContactChanged = scheduleFormViewModel::onContactChanged,
                            onPickContact = {
                                if (ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.READ_CONTACTS,
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    pickContactLauncher.launch(null)
                                } else {
                                    showContactsRationale = true
                                }
                            },
                            onMessageChanged = scheduleFormViewModel::onMessageChanged,
                            onDateSelected = scheduleFormViewModel::onDateSelected,
                            onRepeatTypeChanged = scheduleFormViewModel::onRepeatTypeChanged,
                            onStopAtSelected = scheduleFormViewModel::onStopAtSelected,
                            onSave = {
                                scheduleFormViewModel.saveScheduledSms(
                                    invalidContactError = getString(R.string.schedule_error_invalid_contact),
                                    missingMessageError = getString(R.string.schedule_error_missing_message),
                                    missingDateError = getString(R.string.schedule_error_missing_date),
                                )
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    /**
     * Resolves a contact URI to a pair of (phoneNumber, displayName).
     */
    private fun resolveContact(contactUri: android.net.Uri): Pair<String, String>? {
        val projection =
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
            )
        val contactData =
            contentResolver
                .query(
                    contactUri,
                    projection,
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                        val name =
                            cursor
                                .getString(
                                    cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME),
                                ).orEmpty()
                        Pair(id, name)
                    } else {
                        null
                    }
                } ?: return null

        val (contactId, displayName) = contactData

        val number =
            contentResolver
                .query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                    arrayOf(contactId),
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else ""
                }.orEmpty()

        return if (number.isNotBlank()) Pair(number, displayName) else null
    }

    private fun hasSmsPermissions(): Boolean = smsPermissions.all(::isGranted)

    private fun hasToolSmsPermissions(): Boolean = toolSmsPermissions.all(::isGranted)

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasPromptedForDefaultSmsApp(): Boolean =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_DEFAULT_SMS_PROMPTED, false)

    private fun markPromptedForDefaultSmsApp() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putBoolean(KEY_DEFAULT_SMS_PROMPTED, true) }
    }

    /** Number and display name of a row picked from ACTION_PICK on phone numbers. */
    private fun resolvePickedPhone(uri: Uri): Pair<String, String?>? =
        runCatching {
            contentResolver
                .query(
                    uri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0).orEmpty() to cursor.getString(1) else null
                }
        }.getOrNull()?.takeIf { it.first.isNotBlank() }

    /** Type and display name of something picked to send, read from its provider. */
    private fun outgoingAttachmentFor(
        uri: Uri,
        knownType: String?,
    ): OutgoingAttachment? {
        if (!isShareableAttachment(uri.scheme, uri.encodedAuthority, packageName)) return null
        val type = knownType?.takeIf { it.contains('/') && !it.contains('*') } ?: contentResolver.getType(uri) ?: return null
        val name =
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
        return OutgoingAttachment(uri = uri.toString(), contentType = type, name = name)
    }

    private fun openAttachment(
        uri: String,
        contentType: String,
    ) {
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri.toUri(), contentType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, R.string.conversation_no_app_to_open, Toast.LENGTH_SHORT).show() }
    }

    private fun shouldShowBatteryOptimizationDialog(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager ?: return false
        return !powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isBatteryBannerEnabled(): Boolean =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_BATTERY_BANNER_ENABLED, false)

    private fun setBatteryBannerEnabled(enabled: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putBoolean(KEY_BATTERY_BANNER_ENABLED, enabled)
        }
    }

    /**
     * Requests exemption from battery optimization so scheduled/incoming SMS forwarding keeps
     * working while the device is in Doze or App Standby. This is a legitimate use of the
     * BatteryLife-flagged API: the app's sole purpose is background SMS forwarding, which Play
     * Store's content policy explicitly allows as an exception (messaging apps whose core
     * function requires reliable background delivery).
     */
    @SuppressLint("BatteryLife")
    private fun openBatteryOptimizationSettings() {
        val intent =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            }
        runCatching { startActivity(intent) }
    }

    /**
     * Android offers no way to hand the SMS role to a *specific* other app, so the user is taken
     * to the system's default-apps screen to pick their usual messaging app again.
     */
    private fun openDefaultAppsSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun shouldShowSideloadWarning(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SIDELOAD_DISMISSED, false)) return false
        return !isInstalledFromPlayStore()
    }

    private fun isInstalledFromPlayStore(): Boolean =
        runCatching {
            val sourceInfo = packageManager.getInstallSourceInfo(packageName)
            sourceInfo.installingPackageName == "com.android.vending"
        }.getOrDefault(false)

    private fun dismissSideloadWarning() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putBoolean(KEY_SIDELOAD_DISMISSED, true)
        }
    }

    private fun openAppSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
        runCatching { startActivity(intent) }
    }

    private fun openGoogleAppPasswordsPage() {
        openUrl(GOOGLE_APP_PASSWORDS_URL)
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    private fun openAppLocaleSettings() {
        val intent =
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
        runCatching { startActivity(intent) }
    }

    private fun currentAppLanguageLabel(): String {
        val locales = getSystemService(LocaleManager::class.java)?.applicationLocales
        val locale = locales?.takeIf { !it.isEmpty }?.get(0)
        return locale?.displayName ?: getString(R.string.settings_language_system_default)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dismissAllNotifications()
        newIntentFlow.value = intent
        composeIntentFlow.value = ComposeRequest.from(intent)
        openedFromReminderFlow.value = intent.getBooleanExtra(KeepAliveNotifications.EXTRA_FROM_REMINDER, false)
        // A launch delivered to an already-started activity does not go through onStart.
        restartKeepAliveCountdown()
    }

    override fun onStart() {
        super.onStart()
        restartKeepAliveCountdown()
    }

    /** Opening the app is what keeps Android from treating it as unused: restart the countdown. */
    private fun restartKeepAliveCountdown() {
        (application as SmsRedirectApplication).appContainer.keepAliveReminderScheduler.schedule()
    }

    /**
     * Clears the app's status notifications on open, but not unread-message notifications: those
     * go when their conversation is read.
     */
    private fun dismissAllNotifications() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.activeNotifications
            .filterNot { MessageNotifications.isMessageNotification(it.notification) }
            .forEach { notificationManager.cancel(it.tag, it.id) }
    }

    /** Opens "Manage app if unused" for this app, or the app's details page if that is unavailable. */
    private fun openUnusedAppSettings() {
        val uri = "package:$packageName".toUri()
        try {
            startActivity(Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, uri))
        } catch (e: android.content.ActivityNotFoundException) {
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)) }
        }
    }

    private fun resolveDeepLinkRoute(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "smsredirect" || uri.host != "app") return null
        val features = (application as SmsRedirectApplication).appContainer.features
        return when (uri.path) {
            "/${Routes.History}" -> {
                Routes.History.takeIf { features.forwarding }
            }

            "/${Routes.ScheduleHistory}" -> {
                Routes.ScheduleHistory.takeIf { features.scheduling }
            }

            "/${Routes.Conversation}" -> {
                uri
                    .getQueryParameter(Routes.ThreadIdArg)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 }
                    ?.let(Routes::conversationRoute)
            }

            else -> {
                null
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "sms_redirect_prefs"
        private const val GOOGLE_APP_PASSWORDS_URL = "https://myaccount.google.com/apppasswords"
        private const val INSTALL_GUIDE_URL =
            "https://github.com/neteinstein/SMS-Redirect/blob/main/docs/install-apk.md"
        private const val KEY_SIDELOAD_DISMISSED = "sideload_warning_dismissed"
        private const val KEY_BATTERY_BANNER_ENABLED = "battery_banner_enabled"
        private const val KEY_DEFAULT_SMS_PROMPTED = "default_sms_app_prompted"

        /** What a picture message can carry; the MMSC rejects most other types. */
        private val MMS_ATTACHMENT_TYPES = arrayOf("image/*", "video/*", "audio/*", "text/x-vcard", "text/vcard")

        /** Sent by [ComposeSmsActivity] for sms:/smsto: links and Share. */
        const val ACTION_COMPOSE = "com.neteinstein.smsredirect.action.COMPOSE"
        const val EXTRA_COMPOSE_ADDRESSES = "compose_addresses"
        const val EXTRA_COMPOSE_BODY = "compose_body"
        const val EXTRA_COMPOSE_ATTACHMENT = "compose_attachment"
        const val EXTRA_COMPOSE_ATTACHMENT_TYPE = "compose_attachment_type"
    }
}

/** A "send a message" request forwarded by [ComposeSmsActivity]. */
private data class ComposeRequest(
    val addresses: List<String>,
    val body: String?,
    val attachment: String?,
    val attachmentType: String?,
) {
    fun route(): String =
        if (addresses.isEmpty()) {
            Routes.newMessageRoute(body, attachment, attachmentType)
        } else {
            Routes.conversationRoute(addresses, body, attachment, attachmentType)
        }

    companion object {
        fun from(intent: Intent?): ComposeRequest? {
            if (intent?.action != MainActivity.ACTION_COMPOSE) return null
            return ComposeRequest(
                addresses = intent.getStringArrayListExtra(MainActivity.EXTRA_COMPOSE_ADDRESSES).orEmpty(),
                body = intent.getStringExtra(MainActivity.EXTRA_COMPOSE_BODY),
                attachment = intent.getStringExtra(MainActivity.EXTRA_COMPOSE_ATTACHMENT),
                attachmentType = intent.getStringExtra(MainActivity.EXTRA_COMPOSE_ATTACHMENT_TYPE),
            )
        }
    }
}

private fun androidx.navigation.NavArgumentBuilder.nullableArgs() {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}
