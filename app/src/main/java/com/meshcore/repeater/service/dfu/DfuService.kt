package com.meshcore.repeater.service.dfu

import android.app.Activity
import com.meshcore.repeater.BuildConfig
import com.meshcore.repeater.MainActivity
import no.nordicsemi.android.dfu.DfuBaseService

/**
 * Vordergrund-Dienst der offiziellen Nordic Android DFU Library (v1.26). Die Bibliothek erledigt Verbindung,
 * Buttonless-Sprung in den Bootloader, Init-Paket, Upload mit Packet Receipt Notifications und Validierung;
 * die Benachrichtigung führt zurück in die App.
 */
class DfuService : DfuBaseService() {
    override fun getNotificationTarget(): Class<out Activity> = MainActivity::class.java
    override fun isDebug(): Boolean = BuildConfig.DEBUG
}
