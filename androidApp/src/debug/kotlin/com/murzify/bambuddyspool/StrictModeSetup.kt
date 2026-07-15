package com.murzify.bambuddyspool

import android.os.StrictMode

/** Debug-only detector for accidental main-thread I/O and leaked closable resources. */
internal fun enableStrictMode() {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectAll()
            .penaltyLog()
            .build()
    )
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectAll()
            .penaltyLog()
            .build()
    )
}
