package ua.kpi.smartgrid

import android.app.Application
import androidx.room.Room
import ua.kpi.smartgrid.data.AuthRepository
import ua.kpi.smartgrid.data.GridRepository
import ua.kpi.smartgrid.data.local.AppDatabase
import ua.kpi.smartgrid.data.local.LocalDataSource
import ua.kpi.smartgrid.data.local.TokenStore
import ua.kpi.smartgrid.data.remote.ApiClient
import ua.kpi.smartgrid.data.remote.MockServerInterceptor
import ua.kpi.smartgrid.data.remote.RemoteDataSource
import ua.kpi.smartgrid.location.LocationProvider
import ua.kpi.smartgrid.network.ApiCaller
import ua.kpi.smartgrid.network.ErrorHandler
import ua.kpi.smartgrid.network.ErrorLogger
import ua.kpi.smartgrid.network.NetworkChecker

/**
 * Клас застосунку створює всі шари один раз:
 * База Room → LocalDataSource; Retrofit → RemoteDataSource; обидва джерела → репозиторії.
 * ViewModel отримують лише репозиторії.
 */
class SmartGridApplication : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "smartgrid.db")
            .fallbackToDestructiveMigration() // структура бази змінилась порівняно з ПР2
            .build()
    }

    val tokenStore by lazy { TokenStore(this) }

    // Обробка помилок
    val errorLogger by lazy { ErrorLogger(this) }
    private val apiCaller by lazy { ApiCaller(NetworkChecker(this), ErrorHandler(errorLogger)) }

    // Джерела даних
    private val localDataSource by lazy { LocalDataSource(database) }
    private val remoteDataSource by lazy {
        RemoteDataSource(
            gridApi = ApiClient.createGridApi(tokenStore, MockServerInterceptor(database.userDao())),
            weatherApi = ApiClient.createWeatherApi(),
            caller = apiCaller
        )
    }

    // Репозиторії
    val gridRepository by lazy { GridRepository(localDataSource, remoteDataSource) }
    val authRepository by lazy { AuthRepository(remoteDataSource, tokenStore) }

    // Геолокація користувача
    val locationProvider by lazy { LocationProvider(this) }
}
