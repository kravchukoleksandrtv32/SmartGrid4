package ua.kpi.smartgrid.network

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Журнал помилок: кожна помилка пишеться в Logcat і у файл errors.log
 * у внутрішній пам'яті застосунку. Журнал переглядає адміністратор.
 */
class ErrorLogger(context: Context) {

    private val file = File(context.filesDir, "errors.log")
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun log(action: String, error: Throwable, message: String) {
        val line = "${dateFormat.format(Date())} | $action | ${error.javaClass.simpleName} | $message"
        Log.e(TAG, line, error)
        file.appendText(line + "\n")
    }

    // Найновіші записи — першими
    @Synchronized
    fun read(): List<String> = if (file.exists()) file.readLines().reversed() else emptyList()

    @Synchronized
    fun clear() {
        file.writeText("")
    }

    companion object {
        private const val TAG = "SmartGridErrors"
    }
}
