package com.neteinstein.smsredirect.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.neteinstein.smsredirect.R

@Composable
fun MainBottomBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == MainTab.REDIRECT,
            onClick = { onTabSelected(MainTab.REDIRECT) },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Forward,
                    contentDescription = stringResource(R.string.main_tab_redirect),
                )
            },
            label = { Text(stringResource(R.string.main_tab_redirect)) },
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.SCHEDULE,
            onClick = { onTabSelected(MainTab.SCHEDULE) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = stringResource(R.string.main_tab_schedule),
                )
            },
            label = { Text(stringResource(R.string.main_tab_schedule)) },
        )
    }
}
