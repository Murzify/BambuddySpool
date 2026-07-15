package com.murzify.bambuddyspool

import android.app.Application
import com.murzify.bambuddyspool.core.platform.SecureStorage
import com.murzify.bambuddyspool.core.security.createAndroidSecureStorage

/** Minimal Android process entry point; product initialization remains in the shared graph. */
class BambuddyApplication : Application() {
    /** Created lazily to avoid holding a token plaintext in the application object. */
    val secureStorage: SecureStorage by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createAndroidSecureStorage(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        enableStrictMode()
    }
}
