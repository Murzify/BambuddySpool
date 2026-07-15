package com.murzify.bambuddyspool

import android.content.Context
import android.nfc.NfcAdapter
import com.murzify.bambuddyspool.core.platform.NfcAvailability
import com.murzify.bambuddyspool.core.platform.NfcObservation
import com.murzify.bambuddyspool.core.platform.NfcService

/** Android NFC capability boundary; actual scan observations enter through [AndroidNfcIntentAdapter]. */
internal class AndroidNfcService(context: Context) : NfcService {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context.applicationContext)

    override val isAvailable: Boolean
        get() = availability == NfcAvailability.Available

    override val availability: NfcAvailability
        get() = when {
            adapter == null -> NfcAvailability.Unavailable
            adapter.isEnabled -> NfcAvailability.Available
            else -> NfcAvailability.Disabled
        }

    override suspend fun read(): NfcObservation = error(
        "Android NFC reads are routed from an NDEF discovery intent; no background NFC read is permitted."
    )
}
