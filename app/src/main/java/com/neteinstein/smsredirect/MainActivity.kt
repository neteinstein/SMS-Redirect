package com.neteinstein.smsredirect

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
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
import com.neteinstein.smsredirect.presentation.splash.SplashScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS
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
            val appContainer = (application as SmsRedirectApplication).appContainer
            val navController = rememberNavController()

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
                            appContainer.deleteRuleUseCase
                        )
                    )
                    val rules by homeViewModel.rules.collectAsState()
                    HomeScreen(
                        rules = rules,
                        onAddRule = { navController.navigate(Routes.ruleRoute()) },
                        onEditRule = { navController.navigate(Routes.ruleRoute(it)) },
                        onDeleteRule = homeViewModel::deleteRule
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
                            appContainer.updateRuleUseCase
                        )
                    )

                    val uiState by ruleViewModel.uiState.collectAsState()

                    val pickContactLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickContact()
                    ) { uri ->
                        if (uri != null) {
                            val number = resolvePhoneNumber(uri)
                            if (number.isNotBlank()) ruleViewModel.onPhoneChanged(number)
                        }
                    }

                    val requestContactsPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        if (granted) {
                            pickContactLauncher.launch(null)
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
                        onSenderChanged = ruleViewModel::onSenderChanged,
                        onPhoneChanged = ruleViewModel::onPhoneChanged,
                        onPickContact = {
                            if (ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.READ_CONTACTS
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                pickContactLauncher.launch(null)
                            } else {
                                requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                        onSave = ruleViewModel::saveRule
                    )
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
}
