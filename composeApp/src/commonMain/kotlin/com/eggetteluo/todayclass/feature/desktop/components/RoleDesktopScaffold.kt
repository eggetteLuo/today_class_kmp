package com.eggetteluo.todayclass.feature.desktop.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.eggetteluo.todayclass.core.service.NavigationTextProvider
import com.eggetteluo.todayclass.feature.desktop.model.DesktopTab
import org.koin.compose.koinInject

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RoleDesktopScaffold(
    roleLabel: String,
    tabs: List<DesktopTab>,
    topBarContainerColor: Color,
    topBarContentColor: Color,
    selectedTabIndex: Int? = null,
    onTabSelected: ((Int) -> Unit)? = null,
    floatingActionButton: @Composable (() -> Unit)? = null,
    scaffoldModifier: Modifier = Modifier,
    topBar: (@Composable (selectedTabIndex: Int, currentTab: DesktopTab) -> Unit)? = null,
    content: (@Composable (selectedTabIndex: Int, currentTab: DesktopTab) -> Unit)? = null,
) {
    val navigationTextProvider = koinInject<NavigationTextProvider>()
    var internalSelectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val currentSelectedTabIndex = selectedTabIndex ?: internalSelectedTabIndex
    val currentTab = tabs[currentSelectedTabIndex]

    Scaffold(
        modifier = scaffoldModifier,
        topBar = {
            if (topBar != null) {
                topBar(currentSelectedTabIndex, currentTab)
            } else {
                TopAppBar(
                    title = { Text(currentTab.topTitle) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = topBarContainerColor,
                        titleContentColor = topBarContentColor,
                    ),
                )
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = currentSelectedTabIndex == index,
                        onClick = {
                            if (onTabSelected != null) {
                                onTabSelected(index)
                            } else {
                                internalSelectedTabIndex = index
                            }
                        },
                        label = { Text(tab.bottomLabel) },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.bottomLabel,
                            )
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            floatingActionButton?.invoke()
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (content != null) {
                content(currentSelectedTabIndex, currentTab)
            } else {
                Text(navigationTextProvider.contentText("$roleLabel${currentTab.topTitle}"))
            }
        }
    }
}
