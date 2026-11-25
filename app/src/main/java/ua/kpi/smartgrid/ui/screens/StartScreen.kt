package ua.kpi.smartgrid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ua.kpi.smartgrid.viewmodel.AuthViewModel

/**
 * Стартовий екран: поки з DataStore читається токен, показується індикатор.
 * Дійсний токен — одразу головне меню, інакше — екран входу.
 */
@Composable
fun StartScreen(viewModel: AuthViewModel, onResult: (loggedIn: Boolean) -> Unit) {
    LaunchedEffect(Unit) {
        val session = viewModel.restoreSession()
        onResult(session != null)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text("Перевірка сесії…", modifier = Modifier.padding(top = 12.dp))
    }
}
