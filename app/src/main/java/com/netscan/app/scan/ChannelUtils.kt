package com.netscan.app.scan

/** Conversión de frecuencia Wi-Fi a banda y canal (lógica pura, testeable). */
object ChannelUtils {

    fun bandFor(freq: Int): String = when {
        freq in 2400..2500 -> "2.4 GHz"
        freq in 5000..5900 -> "5 GHz"
        freq in 5925..7125 -> "6 GHz"
        else -> "?"
    }

    fun channelFor(freq: Int): Int = when {
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2412) / 5 + 1
        freq in 5000..5900 -> (freq - 5000) / 5
        freq in 5925..7125 -> (freq - 5950) / 5 + 1
        else -> 0
    }
}
