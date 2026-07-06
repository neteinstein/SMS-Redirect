package com.neteinstein.smsredirect

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
import com.neteinstein.smsredirect.presentation.navigation.Routes
import com.neteinstein.smsredirect.presentation.permissions.PermissionItem
import com.neteinstein.smsredirect.presentation.permissions.PermissionsScreen
import com.neteinstein.smsredirect.presentation.rule.RuleScreen
import com.neteinstein.smsredirect.presentation.rule.RuleViewModel
import com.neteinstein.smsredirect.presentation.rule.RuleViewModelFactory
import com.neteinstein.smsredirect.presentation.settings.SettingsScreen
import com.neteinstein.smsredirect.presentation.settings.SettingsViewModel
import com.neteinstein.smsredirect.presentation.settings.SettingsViewModelFactory
import com.neteinstein.smsredirect.presentation.splash.SplashScreen
import com.neteinstein.smsredirect.presentation.theme.SmsRedirectTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val requiredPermissions =
        arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.POST_NOTIFICATIONS,
        )

    private val newIntentFlow = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        dismissAllNotifications()

        setContent {
            SmsRedirectTheme {
                val appContainer = (application as SmsRedirectApplication).appContainer
                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    newIntentFlow.collectLatest { intent ->
                        if (intent != null && isHistoryDeepLink(intent)) {
                            navController.navigate(Routes.History) {
                                popUpTo(Routes.Home) { inclusive = false }
                            }
                            newIntentFlow.value = null
                        }
                    }
                }

                val showBatteryDialog = remember { mutableStateOf(shouldShowBatteryOptimizationDialog()) }
                val showSideloadDialog = remember { mutableStateOf(shouldShowSideloadWarning()) }

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

                if (showBatteryDialog.value) {
                    AlertDialog(
                        onDismissRequest = { showBatteryDialog.value = false },
                        title = { Text(stringResource(R.string.battery_optimization_title)) },
                        text = { Text(stringResource(R.string.battery_optimization_message)) },
                        dismissButton = {
                            TextButton(onClick = { showBatteryDialog.value = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showBatteryDialog.value = false
                                openBatteryOptimizationSettings()
                            }) {
                                Text(stringResource(R.string.action_open_screen))
                            }
                        },
                    )
                }

                NavHost(navController = navController, startDestination = Routes.Splash) {
                    composable(Routes.Splash) {
                        SplashScreen()
                        LaunchedEffect(Unit) {
                            delay(1200)
                            val destination =
                                if (allPermissionsGranted()) Routes.Home else Routes.Permissions
                            navController.navigate(destination) {
                                popUpTo(Routes.Splash) { inclusive = true }
                            }
                            if (destination == Routes.Home && isHistoryDeepLink(intent)) {
                                navController.navigate(Routes.History)
                            }
                        }
                    }

                    composable(Routes.Permissions) {
                        val lifecycleOwner = LocalLifecycleOwner.current
                        var permissionCheckTick by remember { mutableIntStateOf(0) }
                        DisposableEffect(lifecycleOwner) {
                            val observer =
                                LifecycleEventObserver { _, event ->
                                    if (event == Lifecycle.Event.ON_RESUME) {
                                        permissionCheckTick++
                                    }
                                }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        val permissionItems = remember(permissionCheckTick) { buildPermissionItems() }

                        val permissionLauncher =
                            rememberLauncherForActivityResult(
                                ActivityResultContracts.RequestMultiplePermissions(),
                            ) { permissionCheckTick++ }

                        LaunchedEffect(permissionItems) {
                            if (permissionItems.all { it.isGranted }) {
                                navController.navigate(Routes.Home) {
                                    popUpTo(Routes.Permissions) { inclusive = true }
                                }
                            }
                        }

                        PermissionsScreen(
                            permissions = permissionItems,
                            onRequestPermission = { permissionId ->
                                if (permissionId == NOTIFICATION_ACCESS_ID) {
                                    openNotificationListenerSettings()
                                } else if (permissionId == Manifest.permission.RECEIVE_SMS) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.RECEIVE_SMS,
                                            Manifest.permission.READ_SMS,
                                        ),
                                    )
                                } else {
                                    permissionLauncher.launch(arrayOf(permissionId))
                                }
                            },
                        )
                    }

                    composable(Routes.Home) {
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
                            onAddRule = { navController.navigate(Routes.ruleRoute()) },
                            onOpenHistory = { navController.navigate(Routes.History) },
                            onOpenSettings = { navController.navigate(Routes.Settings) },
                            onEditRule = { navController.navigate(Routes.ruleRoute(it)) },
                            onDeleteRule = homeViewModel::deleteRule,
                            onMoveRule = homeViewModel::moveRule,
                            onDragEnd = homeViewModel::commitReorder,
                        )
                    }

                    composable(Routes.Settings) {
                        val settingsViewModel: SettingsViewModel =
                            viewModel(
                                factory =
                                    SettingsViewModelFactory(
                                        appContainer.getSendWhileRoamingUseCase,
                                        appContainer.setSendWhileRoamingUseCase,
                                        appContainer.getKeepHistoryUseCase,
                                        appContainer.setKeepHistoryUseCase,
                                        appContainer.getDefaultPrefixUseCase,
                                        appContainer.setDefaultPrefixUseCase,
                                        appContainer.getForwardPrefixUseCase,
                                        appContainer.setForwardPrefixUseCase,
                                        appContainer.getShowNotificationsUseCase,
                                        appContainer.setShowNotificationsUseCase,
                                    ),
                            )
                        val sendWhileRoaming by settingsViewModel.sendWhileRoaming.collectAsState()
                        val keepHistory by settingsViewModel.keepHistory.collectAsState()
                        val showNotifications by settingsViewModel.showNotifications.collectAsState()
                        val defaultPrefix by settingsViewModel.defaultPrefix.collectAsState()
                        val forwardPrefix by settingsViewModel.forwardPrefix.collectAsState()
                        SettingsScreen(
                            sendWhileRoaming = sendWhileRoaming,
                            keepHistory = keepHistory,
                            showNotifications = showNotifications,
                            forwardPrefix = forwardPrefix,
                            defaultPrefix = defaultPrefix,
                            onSendWhileRoamingChanged = settingsViewModel::setSendWhileRoaming,
                            onKeepHistoryChanged = settingsViewModel::setKeepHistory,
                            onShowNotificationsChanged = settingsViewModel::setShowNotifications,
                            onForwardPrefixSaved = settingsViewModel::setForwardPrefix,
                            onDefaultPrefixChanged = settingsViewModel::setDefaultPrefix,
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
                                        appContainer.contactNameResolver,
                                    ),
                            )
                        val forwardHistory by historyViewModel.forwardHistory.collectAsState()
                        val contactNames by historyViewModel.contactNames.collectAsState()
                        HistoryScreen(
                            forwardHistory = forwardHistory,
                            contactNames = contactNames,
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

                        LaunchedEffect(ruleId) {
                            ruleViewModel.loadRule(ruleId)
                        }

                        LaunchedEffect(uiState.saved) {
                            if (uiState.saved) {
                                ruleViewModel.clearSavedFlag()
                                navController.popBackStack()
                            }
                        }

                        RuleScreen(
                            uiState = uiState,
                            onNameChanged = ruleViewModel::onNameChanged,
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
                                    requestSenderContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
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
                                    requestTargetContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
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

    private fun allPermissionsGranted(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        } && isNotificationListenerEnabled()

    private fun buildPermissionItems(): List<PermissionItem> =
        listOf(
            PermissionItem(
                id = Manifest.permission.RECEIVE_SMS,
                label = getString(R.string.permission_sms_label),
                description = getString(R.string.permission_sms_description),
                isGranted =
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
                        PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                        PackageManager.PERMISSION_GRANTED,
            ),
            PermissionItem(
                id = Manifest.permission.SEND_SMS,
                label = getString(R.string.permission_send_sms_label),
                description = getString(R.string.permission_send_sms_description),
                isGranted =
                    ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
                        PackageManager.PERMISSION_GRANTED,
            ),
            PermissionItem(
                id = Manifest.permission.READ_CONTACTS,
                label = getString(R.string.permission_contacts_label),
                description = getString(R.string.permission_contacts_description),
                isGranted =
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
                        PackageManager.PERMISSION_GRANTED,
            ),
            PermissionItem(
                id = Manifest.permission.READ_PHONE_STATE,
                label = getString(R.string.permission_phone_state_label),
                description = getString(R.string.permission_phone_state_description),
                isGranted =
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) ==
                        PackageManager.PERMISSION_GRANTED,
            ),
            PermissionItem(
                id = Manifest.permission.POST_NOTIFICATIONS,
                label = getString(R.string.permission_notifications_label),
                description = getString(R.string.permission_notifications_description),
                isGranted =
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED,
            ),
            PermissionItem(
                id = NOTIFICATION_ACCESS_ID,
                label = getString(R.string.permission_notification_access_label),
                description = getString(R.string.permission_notification_access_description),
                isGranted = isNotificationListenerEnabled(),
            ),
        )

    private fun currentMissingPermissions(): List<String> {
        val missing =
            requiredPermissions
                .filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }.map { it.removePrefix("android.permission.") }
                .toMutableList()

        if (!isNotificationListenerEnabled()) {
            missing.add("NOTIFICATION_ACCESS")
        }

        return missing
    }

    private fun shouldShowBatteryOptimizationDialog(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager ?: return false
        return !powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openBatteryOptimizationSettings() {
        val intent =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
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
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SIDELOAD_DISMISSED, true)
            .apply()
    }

    private fun openAppSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        runCatching { startActivity(intent) }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val componentName = ComponentName(this, RcsNotificationListener::class.java)
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this)
        return packageName in enabledListeners
    }

    private fun openNotificationListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        runCatching { startActivity(intent) }
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

    private fun isHistoryDeepLink(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        return uri.scheme == "smsredirect" && uri.host == "app" && uri.path == "/${Routes.History}"
    }

    companion object {
        private const val PREFS_NAME = "sms_redirect_prefs"
        private const val KEY_SIDELOAD_DISMISSED = "sideload_warning_dismissed"
        private const val NOTIFICATION_ACCESS_ID = "notification_access"
    }
}
