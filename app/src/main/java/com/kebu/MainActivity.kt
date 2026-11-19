package com.kebu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kebu.ui.AddExpenseScreen
import com.kebu.ui.MonthlySummaryScreen
import com.kebu.ui.theme.KebuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KebuTheme {
                ExpenseApp()
            }
        }
    }
}

private enum class Screen(val route: String, val label: String) {
    AddExpense(route = "add_expense", label = "Registrar"),
    MonthlySummary(route = "monthly_summary", label = "Resumo"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Screen.AddExpense.route

    val selectedIndex = Screen.entries
        .indexOfFirst { it.route == currentRoute }
        .coerceAtLeast(0)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = selectedIndex) {
                Screen.entries.forEach { screen ->
                    Tab(
                        selected = currentRoute == screen.route,
                        onClick = { navController.switchTo(screen.route) },
                        text = { Text(screen.label) },
                    )
                }
            }

            NavHost(
                navController = navController,
                startDestination = Screen.AddExpense.route,
                modifier = Modifier.weight(1f),
            ) {
                composable(Screen.AddExpense.route) { AddExpenseScreen() }
                composable(Screen.MonthlySummary.route) { MonthlySummaryScreen() }
            }
        }
    }
}

private fun NavHostController.switchTo(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}