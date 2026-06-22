package com.neteinstein.smsredirect

import android.Manifest
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

            if (showBatteryDialog.value) {
                AlertDialog(
                    onDismissRequest = { showBatteryDialog.value = false },
                    title = { Text("Disable battery optimization") },
                    text = { Text("To keep SMS forwarding reliable, disable battery optimization for this app.") },
                    dismissButton = {
                        TextButton(onClick = { showBatteryDialog.value = false }) {
                            Text("Cancel")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showBatteryDialog.value = false
                            openBatteryOptimizationSettings()
                        }) {
                            Text("Open screen")
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
                            appContainer.setSendWhileRoamingUseCase
                        )
                    )
                    val sendWhileRoaming by settingsViewModel.sendWhileRoaming.collectAsState()
                    SettingsScreen(
                        sendWhileRoaming = sendWhileRoaming,
                        onSendWhileRoamingChanged = settingsViewModel::setSendWhileRoaming
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
                            appContainer.ruleRepository
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
                        onSave = ruleViewModel::saveRule
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
        return requiredPermissions
            .filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            .map { it.removePrefix("android.permission.") }
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
}
