package com.example.serialbasics.Data.Model

import com.example.serialbasics.ArduinoSerialDevice
import timber.log.Timber
import java.util.*

class ConnectThread (val operation:Int) : Thread() {

    var finishThread: Boolean = true
    private var isRunning = false

    companion object {
        var CONNECT = 1
        var DISCONNECT = 0
        val WAITTIME : Long = 100L
    }

    fun finish() {
        finishThread = true
    }

    fun isRunning() : Boolean{
        return isRunning
    }

    fun send( request: String, curEvent: Event) {

        if (curEvent.eventType == EventType.FW_NOTEIRO && curEvent.action == Event.ON) {
            ArduinoSerialDevice.lastNoteiroOnTimestamp = Date().time.toString()
            curEvent.noteiroOnTimestamp = ArduinoSerialDevice.lastNoteiroOnTimestamp
        }

        try {
            val pktStr: String = Event.getCommandData(curEvent)
            Timber.d("$request!: $pktStr")
            ArduinoSerialDevice.usbSerialDevice?.write(pktStr.toByteArray())
        } catch (e: Exception) {
            Timber.d("$request!: Ocorreu uma Exception ")
        }
    }

    override fun run() {
        isRunning = true
        if ( operation ==  CONNECT) {
            if ( ArduinoSerialDevice.connectInBackground() ) {
                finishThread = false
                while ( ! finishThread ) {
                    sleep(WAITTIME)
                }
                ArduinoSerialDevice.disconnectInBackground()
            }
        } else if ( operation ==  DISCONNECT) {
            ArduinoSerialDevice.disconnectInBackground()
        }
        isRunning = false
    }
}