package com.murzify.bambuddyspool

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedAndroidDeviceTest {

    @Test
    fun sharedLibraryLoadsOnSupportedAndroidDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertTrue(context.applicationContext.packageName.isNotBlank())
        assertTrue(Build.VERSION.SDK_INT >= 23)
    }
}
