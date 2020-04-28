package com.example.serialbasics.Data.Model

import com.example.serialbasics.ArduinoSerialDevice
import com.example.serialbasics.FunctionType
import com.example.serialbasics.MainActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.util.*

class ConnectThread (val operation:Int) : Thread() {
    private var EVENT_LIST: MutableList<Event> = mutableListOf()
    private var finishThread: Boolean = true
    private var isRunning = false

    companion object {
        var CONNECT = 1
        var DISCONNECT = 0
        val WAITTIME : Long = 100L
        val MICROWAITTIME : Long = 20L
    }

    /**
     * set the thread to finish
     */
    fun finish() {
        finishThread = true
    }

    /**
     * Test if thread is running
     * @return true if thread is running
     */
    fun isRunning() : Boolean{
        return isRunning
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

    /**
     * Create an Event and add in the List of Events to be sent by serial port
     * @return true if the Event was created and able to be sent
     */
    fun requestToSend(eventType: EventType, action: String) : Boolean {
        if ( isRunning() && (!finishThread )) {
            val event = Event(eventType, action)
            EVENT_LIST.add(event)
            return true
        }
        return false
    }

    private fun send( curEvent: Event) {
        try {
            val pktStr: String = Event.getCommandData(curEvent)

            if ( ArduinoSerialDevice.getLogLevel(FunctionType.FX_TX) == 1 ) {
                Timber.d("SEND ==> $pktStr")
            } else {
                Timber.d("SEND ==> %s - %d (errosRX:%d)", curEvent.eventType.command, Event.pktNumber, ArduinoSerialDevice.invalidJsonPacketsReceived)
            }


            ArduinoSerialDevice.usbSerialDevice?.write(pktStr.toByteArray())
        } catch (e: Exception) {
            Timber.d("Ocorreu uma Exception ")
        }
    }

}