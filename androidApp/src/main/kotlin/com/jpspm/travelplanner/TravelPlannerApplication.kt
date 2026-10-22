package com.jpspm.travelplanner

import android.app.Application
import com.jpspm.travelplanner.di.initAndroid

class TravelPlannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initAndroid(this)
    }
}
