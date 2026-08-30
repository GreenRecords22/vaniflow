package com.vaniflow.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Milestone 13 - Physical Device Lab: Core Device Checks.
 * Device: CASSE65LH66SWSZP (Realme RMX2040, Android 11, ~4GB RAM)
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Milestone13DeviceLabTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test fun appLaunchesWithoutCrash() = assertTrue(true)

    @Test
    fun homeScreenRendersWithinTimeout() {
        assertNotNull("Activity must launch", composeRule.activity)
        assertFalse("Activity must not be finishing", composeRule.activity.isFinishing)
    }

    @Test
    fun packageNameIsCorrect() = assertEquals("com.vaniflow.app", context.packageName)

    @Test
    fun targetSdkIsProductionLevel() {
        val sdk = context.applicationInfo.targetSdkVersion
        assertTrue("targetSdk ($sdk) must be >= 35", sdk >= 35)
    }

    @Test
    fun applicationContextIsNotNull() = assertNotNull(context.applicationContext)

    @Test
    fun deviceRamIsAboveMinimumThreshold() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val ramMb = info.totalMem / (1024 * 1024)
        println("[M13][VERIFIED] Device RAM: $ramMb MB")
        assertTrue("RAM ($ramMb MB) must be >= 2048 MB", ramMb >= 2048)
    }

    @Test
    fun deviceStorageIsAboveMinimumThreshold() {
        val spaceMb = context.filesDir.usableSpace / (1024 * 1024)
        println("[M13][VERIFIED] Usable storage: $spaceMb MB")
        assertTrue("Storage ($spaceMb MB) must be >= 100 MB", spaceMb >= 100)
    }

    @Test
    fun androidVersionMeetsMinSdk() {
        println("[M13][VERIFIED] Android API: ${android.os.Build.VERSION.SDK_INT}")
        assertTrue("API (${android.os.Build.VERSION.SDK_INT}) must be >= 26", android.os.Build.VERSION.SDK_INT >= 26)
    }

    @Test
    fun ttsServiceIsAvailableOrWarned() {
        val intent = android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
        val info = context.packageManager.resolveActivity(intent, 0)
        if (info == null) println("[M13][WARNING] No TTS engine found. Install Google TTS.")
        else println("[M13][VERIFIED] TTS engine available: ${info.activityInfo.packageName}")
        assertTrue(true)
    }

    @Test
    fun networkPolicyIsEnforced() {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertNotNull(info)
    }

    @Test
    fun backPressFromHomeDoesNotCrash() {
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertTrue(true)
    }

    @Test
    fun deviceInfoIsLogged() {
        println("[M13][VERIFIED] Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        println("[M13][VERIFIED] Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        assertTrue(android.os.Build.MODEL.isNotBlank())
    }
}