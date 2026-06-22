package com.neteinstein.smsredirect

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.neteinstein.smsredirect.presentation.home.HomeScreen
import com.neteinstein.smsredirect.presentation.home.HomeViewModel
import com.neteinstein.smsredirect.presentation.home.HomeViewModelFactory
import com.neteinstein.smsredirect.presentation.navigation.Routes
import com.neteinstein.smsredirect.presentation.rule.RuleScreen
import com.neteinstein.smsredirect.presentation.rule.RuleViewModel
import com.neteinstein.smsredirect.presentation.rule.RuleViewModelFactory
import com.neteinstein.smsredirect.presentation.settings.SettingsScreen
import com.neteinstein.smsredirect.presentation.settings.SettingsViewModel
import com.neteinstein.smsredirect.presentation.settings.SettingsViewModelFactory
import com.neteinstein.smsredirect.presentation.splash.SplashScreen
import com.neteinstein.smsredirect.presentation.theme.SmsRedirectTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_PHONE_STATE
    )

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
        if (requiredPermissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            SmsRedirectTheme {
            val appContainer = (application as SmsRedirectApplication).appContainer
            val navController = rememberNavController()
            val showBatteryDialog = remember { mutableStateOf(shouldShowBatteryOptimizationDialog()) }
            val showSideloadDialog = remember { mutableStateOf(shouldShowSideloadWarning()) }

            if (showSideloadDialog.value) {
                AlertDialog(
                    onDismissRequest = { dismissSideloadWarning(); showSideloadDialog.value = false },
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
                    }
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
                    }
                )
            }

            val showNotificationAccessDialog = remember {
                mutableStateOf(!isNotificationListenerEnabled())
            }

            if (showNotificationAccessDialog.value) {
                AlertDialog(
                    onDismissRequest = { showNotificationAccessDialog.value = false },
                    title = { Text(stringResource(R.string.notification_access_title)) },
                    text = { Text(stringResource(R.string.notification_access_message)) },
                    dismissButton = {
                        TextButton(onClick = { showNotificationAccessDialog.value = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showNotificationAccessDialog.value = false
                            openNotificationListenerSettings()
                        }) {
                            Text(stringResource(R.string.action_open_screen))
                        }
                    }
                )
            }

            NavHost(navController = navController, startDestination = Routes.Splash) {
                composable(Routes.Splash) {
                    SplashScreen()
                    LaunchedEffect(Unit) {
                        delay(1200)
                        navController.navigate(Routes.Home) {
                            popUpTo(Routes.Splash) { inclusive = true }
                        }
                    }
                }

                composable(Routes.Home) {
                    val homeViewModel: HomeViewModel = viewModel(
                        factory = HomeViewModelFactory(
                            appContainer.getRulesUseCase,
                            appContainer.deleteRuleUseCase,
                            appContainer.reorderRulesUseCase
                        )
                    )
                    val rules by homeViewModel.rules.collectAsState()
                    val lifecycleOwner = LocalLifecycleOwner.current
                    var permissionCheckTick by remember { mutableIntStateOf(0) }
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                permissionCheckTick++
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    val missingPerms = remember(permissionCheckTick) { currentMissingPermissions() }
                    HomeScreen(
                        rules = rules,
                        missingPermissions = missingPerms,
                        onAddRule = { navController.navigate(Routes.ruleRoute()) },
                        onOpenSettings = { navController.navigate(Routes.Settings) },
                        onEditRule = { navController.navigate(Routes.ruleRoute(it)) },
                        onDeleteRule = homeViewModel::deleteRule,
                        onMoveRule = homeViewModel::moveRule,
                        onDragEnd = homeViewModel::commitReorder
                    )
                }

                composable(Routes.Settings) {
                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModelFactory(
                            appContainer.getSendWhileRoamingUseCase,
                            appContainer.setSendWhileRoamingUseCase,
                            appContainer.getKeepHistoryUseCase,
                            appContainer.setKeepHistoryUseCase,
                            appContainer.getDefaultPrefixUseCase,
                            appContainer.setDefaultPrefixUseCase,
                            appContainer.getForwardPrefixUseCase,
                            appContainer.setForwardPrefixUseCase,
                            appContainer.getForwardHistoryUseCase
                        )
                    )
                    val lifecycleOwner = LocalLifecycleOwner.current
                    var permissionCheckTick by remember { mutableIntStateOf(0) }
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                permissionCheckTick++
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    val missingPerms = remember(permissionCheckTick) { currentMissingPermissions() }
                    val sendWhileRoaming by settingsViewModel.sendWhileRoaming.collectAsState()
                    val keepHistory by settingsViewModel.keepHistory.collectAsState()
                    val defaultPrefix by settingsViewModel.defaultPrefix.collectAsState()
                    val forwardPrefix by settingsViewModel.forwardPrefix.collectAsState()
                    val forwardHistory by settingsViewModel.forwardHistory.collectAsState()
                    SettingsScreen(
                        missingPermissions = missingPerms,
                        sendWhileRoaming = sendWhileRoaming,
                        keepHistory = keepHistory,
                        forwardPrefix = forwardPrefix,
                        defaultPrefix = defaultPrefix,
                        forwardHistory = forwardHistory,
                        onSendWhileRoamingChanged = settingsViewModel::setSendWhileRoaming,
                        onKeepHistoryChanged = settingsViewModel::setKeepHistory,
                        onForwardPrefixSaved = settingsViewModel::setForwardPrefix,
                        onDefaultPrefixChanged = settingsViewModel::setDefaultPrefix,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.RuleWithArg,
                    arguments = listOf(navArgument(Routes.RuleIdArg) {
                        type = NavType.LongType
                        defaultValue = 0L
                    })
                ) { backStackEntry ->
                    val ruleId = backStackEntry.arguments?.getLong(Routes.RuleIdArg) ?: 0L
                    val ruleViewModel: RuleViewModel = viewModel(
                        factory = RuleViewModelFactory(
                            appContainer.getRuleUseCase,
                            appContainer.addRuleUseCase,
                            appContainer.updateRuleUseCase,
                            appContainer.ruleRepository,
                            appContainer.getDefaultPrefixNowUseCase,
                            defaultRuleNameProvider = { index ->
                                getString(R.string.rule_default_name_format, index)
                            }
                        )
                    )

                    val uiState by ruleViewModel.uiState.collectAsState()

                    val pickSenderContactLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickContact()
                    ) { uri ->
                        if (uri != null) {
                            val number = resolvePhoneNumber(uri)
                            if (number.isNotBlank()) ruleViewModel.onSenderChanged(number)
                        }
                    }

                    val pickTargetContactLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickContact()
                    ) { uri ->
                        if (uri != null) {
                            val number = resolvePhoneNumber(uri)
                            if (number.isNotBlank()) ruleViewModel.onTargetChanged(number)
                        }
                    }

                    val requestSenderContactsPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        if (granted) {
                            pickSenderContactLauncher.launch(null)
                        }
                    }

                    val requestTargetContactsPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
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
                                    Manifest.permission.READ_CONTACTS
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
                                    Manifest.permission.READ_CONTACTS
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                pickTargetContactLauncher.launch(null)
                            } else {
                                requestTargetContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                        onSave = { ruleViewModel.saveRule(
                            invalidTargetError = getString(R.string.rule_error_invalid_target),
                            duplicateRuleError = getString(R.string.rule_error_duplicate)
                        ) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            }
        }
    }

    private fun resolvePhoneNumber(contactUri: android.net.Uri): String {
        val contactId = contentResolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts._ID),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: return ""

        return contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else ""
        }.orEmpty()
    }

    private fun currentMissingPermissions(): List<String> {
        val missing = requiredPermissions
            .filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            .map { it.removePrefix("android.permission.") }
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
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }

    private fun shouldShowSideloadWarning(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SIDELOAD_DISMISSED, false)) return false
        return !isInstalledFromPlayStore()
    }

    private fun isInstalledFromPlayStore(): Boolean {
        return runCatching {
            val sourceInfo = packageManager.getInstallSourceInfo(packageName)
            sourceInfo.installingPackageName == "com.android.vending"
        }.getOrDefault(false)
    }

    private fun dismissSideloadWarning() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SIDELOAD_DISMISSED, true)
            .apply()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
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

    companion object {
        private const val PREFS_NAME = "sms_redirect_prefs"
        private const val KEY_SIDELOAD_DISMISSED = "sideload_warning_dismissed"
    }
}
