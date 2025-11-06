package com.janad.vioraedit

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class VioraEditApp: Application() {
 override fun onCreate() {
     super.onCreate()
     Timber.plant(Timber.DebugTree())
 }
}