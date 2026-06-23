package com.carhacker.kit

import android.app.Application

class CarHackerApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize any global state here
        instance = this
    }
    
    companion object {
        lateinit var instance: CarHackerApp
            private set
    }
}
