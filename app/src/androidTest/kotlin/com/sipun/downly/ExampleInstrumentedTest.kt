package com.sipun.downly

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.airbnb.lottie.LottieCompositionFactory

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.sipun.downly", appContext.packageName)
    }

    @Test
    fun testLoadLottieCompositions() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        val downloadResult = LottieCompositionFactory.fromRawResSync(appContext, R.raw.about_download)
        assertNotNull("about_download composition should not be null", downloadResult.value)
        assertNull("about_download should have no exception: ${downloadResult.exception?.message}", downloadResult.exception)

        val heartResult = LottieCompositionFactory.fromRawResSync(appContext, R.raw.about_heart)
        assertNotNull("about_heart composition should not be null", heartResult.value)
        assertNull("about_heart should have no exception: ${heartResult.exception?.message}", heartResult.exception)
    }
}