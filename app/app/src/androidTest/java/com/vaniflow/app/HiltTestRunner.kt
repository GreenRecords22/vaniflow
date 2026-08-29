package com.vaniflow.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom test runner for VaniFlow instrumented tests.
 *
 * Replaces the default AndroidJUnitRunner so that Hilt's DI graph is
 * initialised correctly for every @HiltAndroidTest instrumented test.
 *
 * Referenced in build.gradle.kts:
 *   testInstrumentationRunner = "com.vaniflow.app.HiltTestRunner"
 */
class HiltTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
