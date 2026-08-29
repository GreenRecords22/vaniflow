package com.vaniflow.app.engine

import android.content.Context
import com.vaniflow.app.engine.stt.RealOfflineSTTEngine
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class RealOfflineSTTEngineTest {

    @Test
    fun testSTTEngineInstantiation() {
        val mockContext = mockk<Context>(relaxed = true)
        val sttEngine = RealOfflineSTTEngine(mockContext)
        assertNotNull(sttEngine)
        assertFalse(sttEngine.isListening.value)
    }
}
