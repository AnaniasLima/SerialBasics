package com.example.serialbasics.Data.Model

import com.example.serialbasics.ArduinoSerialDevice

class ConnectThread internal constructor(val operation:Int) : Thread() {

    companion object {
        var CONNECT = 1
        var DISCONNECT = 0
    }

    override fun run() {
        if ( operation ==  CONNECT) {
            ArduinoSerialDevice.connect()
        } else {
            ArduinoSerialDevice.disconnect()
        }

    }
}