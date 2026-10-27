package com.cordobot.scanvault

import android.app.Application
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cordobot.scanvault.presentation.ScanVaultTheme
import com.cordobot.scanvault.presentation.history.HistoryScreen
import com.cordobot.scanvault.presentation.result.ResultScreen
import com.cordobot.scanvault.presentation.scanner.ScannerScreen
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ScanVaultApp : Application()

private const val SCAN = "scan"
private const val HISTORY = "history"
private const val RESULT = "result/{id}"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            ScanVaultTheme {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val route = backStack?.destination?.route

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (route != RESULT) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                                listOf(
                                    Triple(SCAN, "Escanear", Icons.Default.QrCodeScanner),
                                    Triple(HISTORY, "Historial", Icons.Default.History)
                                ).forEach { (destination, label, icon) ->
                                    NavigationBarItem(
                                        selected = route == destination,
                                        onClick = {
                                            navController.navigate(destination) {
                                                popUpTo(SCAN)
                                                launchSingleTop = true
                                            }
                                        },
                                        icon = { Icon(icon, contentDescription = null) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController,
                        startDestination = SCAN,
                        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                        enterTransition = { fadeIn() },
                        exitTransition = { fadeOut() }
                    ) {
                        composable(SCAN) { ScannerScreen(onResult = { id -> navController.navigate("result/$id") }) }
                        composable(HISTORY) { HistoryScreen(onOpen = { id -> navController.navigate("result/$id") }) }
                        composable(
                            RESULT,
                            arguments = listOf(navArgument("id") { type = NavType.LongType }),
                            enterTransition = { slideInVertically { it } + fadeIn() },
                            popExitTransition = { slideOutVertically { it } + fadeOut() }
                        ) {
                            ResultScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
