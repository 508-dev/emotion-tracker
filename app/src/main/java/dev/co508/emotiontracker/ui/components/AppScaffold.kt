package dev.co508.emotiontracker.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.co508.emotiontracker.R
import dev.co508.emotiontracker.ui.navigation.AppNavHost
import dev.co508.emotiontracker.ui.navigation.Destination
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    /** Incremented each time a notification tap asks to land on the wheel. */
    openWheelRequests: StateFlow<Int>,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Reset to the wheel (the main screen) when a reminder notification is
    // tapped while the app is in the background on another screen. On a cold
    // start the wheel is already the start destination, so the remember()
    // baseline swallows that case and nothing needs to happen.
    var lastOpenWheelRequest by remember { mutableIntStateOf(openWheelRequests.value) }
    LaunchedEffect(openWheelRequests) {
        openWheelRequests.collect { request ->
            if (request != lastOpenWheelRequest) {
                lastOpenWheelRequest = request
                navController.navigate(Destination.Wheel.route) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                // The wheel screen itself has no title bar (see topBar below),
                // so the app's name only shows up here, once the drawer is
                // opened.
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                )
                Destination.entries.forEach { destination ->
                    NavigationDrawerItem(
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                        selected = currentRoute == destination.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            // Screens can also be reached outside the drawer
                            // (e.g. the wheel's "Open journal" link), which
                            // pushes onto the back stack without the
                            // saveState/restoreState bookkeeping below. If
                            // the destination is already alive on the stack
                            // from one of those routes, popUpTo +
                            // launchSingleTop silently no-ops instead of
                            // returning to it — Navigation only considers it
                            // "already on top" and skips the navigation
                            // entirely. Popping straight to it sidesteps
                            // that; only fall back to the normal
                            // save/restore dance when it isn't on the stack
                            // yet.
                            if (currentRoute != destination.route &&
                                !navController.popBackStack(destination.route, inclusive = false)
                            ) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                // The wheel is the main screen and wants full-bleed vertical
                // space — no title bar there, just an always-reachable
                // hamburger icon; the title itself only shows once the
                // drawer is opened (see drawerContent above).
                val isWheel = currentRoute == Destination.Wheel.route
                TopAppBar(
                    title = { if (!isWheel) Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.nav_menu_content_description),
                            )
                        }
                    },
                    colors =
                        if (isWheel) {
                            TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        } else {
                            TopAppBarDefaults.topAppBarColors()
                        },
                )
            },
        ) { innerPadding ->
            AppNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
        }
    }
}
