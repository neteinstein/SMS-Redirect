package com.neteinstein.smsredirect

import android.Manifest
import android.annotation.SuppressLint
import android.app.LocaleManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.neteinstein.smsredirect.presentation.history.HistoryScreen
import com.neteinstein.smsredirect.presentation.history.HistoryViewModel
import com.neteinstein.smsredirect.presentation.history.HistoryViewModelFactory
import com.neteinstein.smsredirect.presentation.home.HomeScreen
import com.neteinstein.smsredirect.presentation.home.HomeViewModel
import com.neteinstein.smsredirect.presentation.home.HomeViewModelFactory
import com.neteinstein.smsredirect.presentation.navigation.MainBottomBar
import com.neteinstein.smsredirect.presentation.navigation.MainTab
import com.neteinstein.smsredirect.presentation.navigation.Routes
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.io.File

class MainActivity : ComponentActivity() {
    private val smsPermissions =
        arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
        )

    private val newIntentFlow = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        dismissAllNotifications()

        setContent {
            SmsRedirectTheme {
                val appContainer = (application as SmsRedirectApplication).appContainer
                val navController = rememberNavController()

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

                var showBatteryDialog by remember { mutableStateOf(false) }
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
                        var selectedTab by rememberSaveable { mutableStateOf(MainTab.REDIRECT) }

                        var pendingCreateRoute by remember { mutableStateOf<String?>(null) }
                        var showSmsRationale by remember { mutableStateOf(false) }
                        var showSmsDenied by remember { mutableStateOf(false) }

                        val smsPermissionLauncher =
                            rememberLauncherForActivityResult(
                                ActivityResultContracts.RequestMultiplePermissions(),
                            ) { results ->
                                val route = pendingCreateRoute
                                pendingCreateRoute = null
                                if (results.values.all { it } && route != null) {
                                    navController.navigate(route)
                                } else {
                                    showSmsDenied = true
                                }
                            }

                        val requestCreate: (String) -> Unit = { route ->
                            if (hasSmsPermissions()) {
                                navController.navigate(route)
                            } else {
                                pendingCreateRoute = route
                                showSmsRationale = true
                            }
                        }

                        if (showSmsRationale) {
                            AlertDialog(
                                onDismissRequest = {
                                    showSmsRationale = false
                                    pendingCreateRoute = null
                                },
                                title = { Text(stringResource(R.string.sms_permission_rationale_title)) },
                                text = { Text(stringResource(R.string.sms_permission_rationale_message)) },
                                dismissButton = {
                                    TextButton(onClick = {
                                        showSmsRationale = false
                                        pendingCreateRoute = null
                                    }) {
                                        Text(stringResource(R.string.action_cancel))
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showSmsRationale = false
                                        smsPermissionLauncher.launch(smsPermissions)
                                    }) {
                                        Text(stringResource(R.string.action_proceed))
                                    }
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

                        val bottomBar: @Composable () -> Unit = {
                            MainBottomBar(
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                                onFabClick = {
                                    when (selectedTab) {
                                        MainTab.REDIRECT -> requestCreate(Routes.ruleRoute())
                                        MainTab.SCHEDULE -> requestCreate(Routes.scheduleRoute())
                                    }
                                },
                                fabContentDescription =
                                    when (selectedTab) {
                                        MainTab.REDIRECT -> stringResource(R.string.home_cd_add_rule)
                                        MainTab.SCHEDULE -> stringResource(R.string.schedule_cd_add)
                                    },
                            )
                        }

                        when (selectedTab) {
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
                                    onFixBackgroundWarning = { openBatteryOptimizationSettings() },
                                )
                            }
                        }
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
                                        appContainer.exportBackupUseCase,
                                        appContainer.importBackupUseCase,
                                    ),
                            )
                        val sendWhileRoaming by settingsViewModel.sendWhileRoaming.collectAsState()
                        val showNotifications by settingsViewModel.showNotifications.collectAsState()
                        val defaultPrefix by settingsViewModel.defaultPrefix.collectAsState()
                        val forwardPrefix by settingsViewModel.forwardPrefix.collectAsState()

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

    private fun hasSmsPermissions(): Boolean =
        smsPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
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
    }

    private fun dismissAllNotifications() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }

    private fun resolveDeepLinkRoute(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "smsredirect" || uri.host != "app") return null
        return when (uri.path) {
            "/${Routes.History}" -> Routes.History
            "/${Routes.ScheduleHistory}" -> Routes.ScheduleHistory
            else -> null
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "sms_redirect_prefs"
        private const val KEY_SIDELOAD_DISMISSED = "sideload_warning_dismissed"
        private const val KEY_BATTERY_BANNER_ENABLED = "battery_banner_enabled"
    }
}
