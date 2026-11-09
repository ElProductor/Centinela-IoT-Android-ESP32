package com.inacap.centinelaiot

import android.app.Application
import com.inacap.centinelaiot.data.database.DatabaseHelper
import com.inacap.centinelaiot.data.security.SessionManager
import com.inacap.centinelaiot.data.simulator.Esp32VirtualSimulator

class CentinelaApp : Application() {

    lateinit var database: DatabaseHelper
        private set

    val databaseHelper: DatabaseHelper
        get() = database

    lateinit var sessionManager: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = DatabaseHelper(this)
        sessionManager = SessionManager(this)

        if (sessionManager.isSimulatorActive) {
            Esp32VirtualSimulator.start()
        }
    }

    companion object {
        lateinit var instance: CentinelaApp
            private set
    }
}
