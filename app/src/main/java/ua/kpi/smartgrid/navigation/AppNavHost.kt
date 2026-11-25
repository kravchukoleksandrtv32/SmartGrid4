package ua.kpi.smartgrid.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import ua.kpi.smartgrid.SmartGridApplication
import ua.kpi.smartgrid.ui.screens.AdminScreen
import ua.kpi.smartgrid.ui.screens.CalculatorScreen
import ua.kpi.smartgrid.ui.screens.LoginScreen
import ua.kpi.smartgrid.ui.screens.MainMenuScreen
import ua.kpi.smartgrid.ui.screens.MapScreen
import ua.kpi.smartgrid.ui.screens.MeasurementFormScreen
import ua.kpi.smartgrid.ui.screens.MeasurementsScreen
import ua.kpi.smartgrid.ui.screens.ModesScreen
import ua.kpi.smartgrid.ui.screens.MonitoringScreen
import ua.kpi.smartgrid.ui.screens.ObjectDetailScreen
import ua.kpi.smartgrid.ui.screens.ObjectsScreen
import ua.kpi.smartgrid.ui.screens.RegisterScreen
import ua.kpi.smartgrid.ui.screens.StartScreen
import ua.kpi.smartgrid.viewmodel.AdminViewModel
import ua.kpi.smartgrid.viewmodel.AuthViewModel
import ua.kpi.smartgrid.viewmodel.MeasurementViewModel
import ua.kpi.smartgrid.viewmodel.ModesViewModel
import ua.kpi.smartgrid.viewmodel.MonitoringViewModel
import ua.kpi.smartgrid.viewmodel.ObjectsViewModel

/**
 * Граф навігації: усі екрани та переходи між ними.
 */
@Composable
fun AppNavHost(navController: NavHostController) {
    // Репозиторії беремо з класу застосунку
    val app = LocalContext.current.applicationContext as SmartGridApplication

    // ViewModel, спільні для кількох екранів
    val authViewModel: AuthViewModel = viewModel { AuthViewModel(app.authRepository) }
    val measurementViewModel: MeasurementViewModel = viewModel { MeasurementViewModel(app.gridRepository) }
    // Одна ViewModel для списку, деталей і мапи — вибір і фільтри спільні
    val objectsViewModel: ObjectsViewModel = viewModel { ObjectsViewModel(app.gridRepository) }

    val session by authViewModel.session.collectAsState()
    val sessionExpired by authViewModel.sessionExpired.collectAsState()

    // Перехід на екран входу з очищенням стеку
    val goToLogin = {
        navController.navigate(Screen.Login.route) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = Screen.Start.route) {

        composable(Screen.Start.route) {
            StartScreen(authViewModel) { loggedIn ->
                if (loggedIn) {
                    navController.navigate(Screen.Menu.route) {
                        popUpTo(Screen.Start.route) { inclusive = true }
                    }
                } else {
                    goToLogin()
                }
            }
        }

        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    // Переходимо в меню і прибираємо екран входу зі стеку
                    navController.navigate(Screen.Menu.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onRegisterClick = { navController.navigate(Screen.Register.route) }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = authViewModel,
                onRegistered = { navController.popBackStack() }, // назад до входу
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Menu.route) {
            MainMenuScreen(
                session = session,
                viewModel = measurementViewModel,
                onMonitoring = { navController.navigate(Screen.Monitoring.route) },
                onObjects = { navController.navigate(Screen.Objects.route) },
                onMap = { navController.navigate(Screen.Map.route) },
                onModes = { navController.navigate(Screen.Modes.route) },
                onCalculator = { navController.navigate(Screen.Calculator.route) },
                onMeasurements = { navController.navigate(Screen.Measurements.route) },
                onNewMeasurement = { navController.navigate(Screen.MeasurementForm.create(0)) },
                onAdmin = { navController.navigate(Screen.Admin.route) },
                onLogout = { authViewModel.logout { goToLogin() } }
            )
        }

        composable(Screen.Monitoring.route) {
            // ViewModel прив'язана до цього екрана: при виході потоки телеметрії зупиняються
            val monitoringViewModel: MonitoringViewModel = viewModel { MonitoringViewModel(app.gridRepository) }
            MonitoringScreen(viewModel = monitoringViewModel, onBack = { navController.popBackStack() })
        }

        composable(Screen.Objects.route) {
            ObjectsScreen(
                viewModel = objectsViewModel,
                locationProvider = app.locationProvider,
                onOpenDetails = { id -> navController.navigate(Screen.ObjectDetail.create(id)) },
                onOpenMap = { navController.navigate(Screen.Map.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ObjectDetail.route,
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: 0
            ObjectDetailScreen(
                id = id,
                viewModel = objectsViewModel,
                onShowOnMap = {
                    objectsViewModel.select(id)
                    navController.navigate(Screen.Map.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Map.route) {
            MapScreen(
                viewModel = objectsViewModel,
                locationProvider = app.locationProvider,
                onOpenDetails = { id -> navController.navigate(Screen.ObjectDetail.create(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Modes.route) {
            val modesViewModel: ModesViewModel = viewModel { ModesViewModel(app.gridRepository) }
            ModesScreen(
                viewModel = modesViewModel,
                isAdmin = session?.isAdmin == true,
                onBack = { navController.popBackStack() }
            )
        }

        // Екран з практичної роботи №1
        composable(Screen.Calculator.route) {
            CalculatorScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Measurements.route) {
            MeasurementsScreen(
                viewModel = measurementViewModel,
                onAdd = { navController.navigate(Screen.MeasurementForm.create(0)) },
                onEdit = { id -> navController.navigate(Screen.MeasurementForm.create(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MeasurementForm.route,
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: 0
            MeasurementFormScreen(
                id = id,
                viewModel = measurementViewModel,
                onDone = { navController.popBackStack() }
            )
        }

        composable(Screen.Admin.route) {
            val adminViewModel: AdminViewModel =
                viewModel { AdminViewModel(app.authRepository, app.gridRepository, app.errorLogger) }
            AdminScreen(viewModel = adminViewModel, onBack = { navController.popBackStack() })
        }
    }

    // Сервер відхилив прострочений токен — повертаємо користувача на екран входу
    LaunchedEffect(sessionExpired) {
        if (sessionExpired) goToLogin()
    }
}
