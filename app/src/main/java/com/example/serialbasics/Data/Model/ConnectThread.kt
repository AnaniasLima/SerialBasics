package com.example.serialbasics.Data.Model

import com.example.serialbasics.ArduinoSerialDevice
import com.example.serialbasics.MainActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.util.*

class ConnectThread (val operation:Int) : Thread() {


    var EVENT_LIST: MutableList<Event> = mutableListOf()


    var finishThread: Boolean = true
    private var isRunning = false

    companion object {
        var CONNECT = 1
        var DISCONNECT = 0
        val WAITTIME : Long = 100L
        val MICROWAITTIME : Long = 20L
    }

    fun finish() {
        finishThread = true
    }

    fun isRunning() : Boolean{
        return isRunning
    }

    fun send( curEvent: Event) {

        if (curEvent.eventType == EventType.FW_NOTEIRO && curEvent.action == Event.ON) {
            ArduinoSerialDevice.lastNoteiroOnTimestamp = Date().time.toString()
            curEvent.noteiroOnTimestamp = ArduinoSerialDevice.lastNoteiroOnTimestamp
        }

        try {
            val pktStr: String = Event.getCommandData(curEvent)

            if ( (ArduinoSerialDevice.mainActivity as MainActivity).btnEchoSendOff.isEnabled ) {
                Timber.d("SEND ==> $pktStr")
            } else {
                Timber.d("SEND ==> %s - %d (errosRX:%d)", curEvent.eventType.command, Event.pktNumber, ArduinoSerialDevice.invalidJsonPacketsReceived)
            }


            ArduinoSerialDevice.usbSerialDevice?.write(pktStr.toByteArray())
        } catch (e: Exception) {
            Timber.d("Ocorreu uma Exception ")
        }
    }

    override fun run() {
        isRunning = true
        if ( operation ==  CONNECT) {
            if ( ArduinoSerialDevice.connectInBackground() ) {
                finishThread = false
                while ( ! finishThread ) {
                    if ( EVENT_LIST.isEmpty() ) {
                        sleep(WAITTIME)
                    }  else {
                        val event = EVENT_LIST[0]
                        send(event)
                        sleep(MICROWAITTIME)
                        EVENT_LIST.removeAt(0)
                    }
                }
                ArduinoSerialDevice.disconnectInBackground()
            }
        } else if ( operation ==  DISCONNECT) {
            ArduinoSerialDevice.disconnectInBackground()
        }
        isRunning = false
    }
}