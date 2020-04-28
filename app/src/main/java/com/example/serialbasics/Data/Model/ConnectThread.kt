package com.example.serialbasics.Data.Model

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import com.example.serialbasics.ArduinoSerialDevice
import com.example.serialbasics.FunctionType
import com.example.serialbasics.MainActivity
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.IOException
import java.util.*

class ConnectThread (val operation:Int, val usbManager : UsbManager, val mainActivity: AppCompatActivity, val myContext: Context) : Thread() ,
    UsbSerialInterface.UsbReadCallback {
    private var EVENT_LIST: MutableList<Event> = mutableListOf()
    private var finishThread: Boolean = true
    private var usbSerialDevice: UsbSerialDevice? = null
    var pktArrayDeBytes = ByteArray(512)
    var pktInd:Int=0

    companion object {
        var CONNECT = 1
        var DISCONNECT = 0
        val WAITTIME : Long = 100L
        val MICROWAITTIME : Long = 20L
        var isConnected: Boolean  = false
        var invalidJsonPacketsReceived:Int = 0
    }

    private fun mostraNaTela(str:String) {
        (mainActivity as MainActivity).mostraNaTela(str)
    }


    // onde chegam as respostas do arduino
    override fun onReceivedData(pkt: ByteArray) {
        val tam:Int = pkt.size
        var ch:Byte

        if ( tam == 0) {
            return
        }

//        Timber.i("pktsize=${pkt.size} ")

        for ( i in 0 until tam) {
            ch  =   pkt[i]
//            Timber.i("  [$i] - %c", ch)
            if ( ch.toInt() == 0 ) break
            if ( ch.toChar() == '{') {
                if ( pktInd > 0 ) {
                    Timber.d("Vai desprezar: ${String(
                        pktArrayDeBytes, 0,
                        pktInd
                    )}")
                }
                pktInd = 0
            }
            if ( ch.toInt() in 32..126 ) {
                if ( pktInd < (pktArrayDeBytes.size - 1) ) {
                    pktArrayDeBytes[pktInd++] = ch
                    pktArrayDeBytes[pktInd] = 0
                    if ( ch.toChar() == '}') {
                        onCommandReceived(String(pktArrayDeBytes,0,pktInd))
                        pktInd = 0
                    }
                } else {
                    // ignora tudo
                    pktInd = 0
                }
            }
        }
    }


    fun onCommandReceived(commandReceived: String) {
        try {
            val eventResponse = Gson().fromJson(commandReceived, EventResponse::class.java)
            EventType.getByCommand(eventResponse.cmd)?.let {
                eventResponse.eventType = it

                if ( eventResponse.eventType == EventType.FW_NACK ) {
                    Timber.e("=============== FW_NACK =======================: ${commandReceived}")
                } else {
                    ArduinoSerialDevice.onEventResponse(eventResponse)
                }
            }
        } catch (e: Exception) {
            invalidJsonPacketsReceived++
            Timber.e("===============JSON INVALIDO (%d)=======================: ${commandReceived}", invalidJsonPacketsReceived)
            return
        }

        if ( ArduinoSerialDevice.getLogLevel(FunctionType.FX_RX) > 0  ) {
            Timber.d("commandReceived: ${commandReceived}")
        }
    }


    /**
     * set the thread to finish
     */
    fun finish() {
        finishThread = true
    }

    override fun run() {
        if ( operation ==  CONNECT) {
            if ( connectInBackground() ) {
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
                disconnectInBackground()
            }
        } else if ( operation ==  DISCONNECT) {
            disconnectInBackground()
        }
        isConnected = false
    }

    /**
     * Create an Event and add in the List of Events to be sent by serial port
     * @return true if the Event was created and able to be sent
     */
    fun requestToSend(eventType: EventType, action: String) : Boolean {
        if ( isConnected && (!finishThread )) {
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
                Timber.i("SEND ==> %s - %d (errosRX:%d)", curEvent.eventType.command, Event.pktNumber, invalidJsonPacketsReceived)
            }

            usbSerialDevice?.write(pktStr.toByteArray())
        } catch (e: Exception) {
            Timber.d("Ocorreu uma Exception ")
        }
    }


    private fun connectInBackground() : Boolean {

        isConnected = usbSerialDevice?.isOpen ?: false
        if ( isConnected ) {
            return true
        }

        try {
            val m_device    : UsbDevice? = selectDevice(0)

            if ( m_device != null ) {
                val m_connection: UsbDeviceConnection? = usbManager.openDevice(m_device)
                mostraNaTela("hasPermission = " + usbManager.hasPermission(m_device).toString())
                mostraNaTela("deviceClass = " + m_device.deviceClass.toString())
                mostraNaTela("deviceName = " + m_device.deviceName)
                mostraNaTela("vendorId = " + m_device.vendorId.toString())
                mostraNaTela("productId = " + m_device.productId.toString())
                if (m_connection != null) {
                    Timber.i("Creating usbSerialDevice")
                    usbSerialDevice = UsbSerialDevice.createUsbSerialDevice(m_device, m_connection)
                    if ( usbSerialDevice != null ) {
                        Timber.i("Opening usbSerialDevice")
                        if ( usbSerialDevice!!.open()) {
                            usbSerialDevice!!.setBaudRate(57600)
                            usbSerialDevice!!.read( this )
                        }
                    } else {
                        mostraNaTela("can´t create usbSerialDevice. createUsbSerialDevice(m_device, m_connection) Failure.")
                        Timber.e("can´t create usbSerialDevice. createUsbSerialDevice(m_device, m_connection) Failure.")
                    }
                } else {
                    mostraNaTela("can´t create m_connection. openDevice(m_device) Failure.")
                    Timber.e("can´t create m_connection. openDevice(m_device) Failure.")
                }
            }
        } catch ( e: IOException ) {
            usbSerialDevice = null
        }

        isConnected = usbSerialDevice?.isOpen ?: false

        if ( isConnected ) {
            mostraNaTela("CONECTADO COM SUCESSO")
            (mainActivity as MainActivity).usbSerialImediateChecking(100)
        }

        return isConnected
    }


    private fun disconnectInBackground() {
        isConnected = false
        if ( usbSerialDevice != null) {
            Timber.i("-------- disconnectInBackground Inicio")
            if ( usbSerialDevice!!.isOpen )  {
                usbSerialDevice!!.close()
            }
            usbSerialDevice = null
            Timber.i("-------- disconnectInBackground Fim")
            (mainActivity as MainActivity).usbSerialImediateChecking(100)
        }
    }

    private fun selectDevice(vendorRequired:Int) : UsbDevice? {
        var selectedDevice: UsbDevice? = null
        val deviceList : HashMap<String, UsbDevice>? = usbManager.deviceList
        if ( !deviceList?.isEmpty()!!) {
            var device: UsbDevice?
            println("Device list size: ${deviceList.size}")
            deviceList.forEach { entry ->
                device = entry.value
                val deviceVendorId: Int = device!!.vendorId
                mostraNaTela("Device localizado. Vendor:" + deviceVendorId.toString() + "  productId: " + device!!.productId + "  Name: " + device!!.productName)
                Timber.i("Device Vendor.Id: %d",  deviceVendorId)
                if ( (vendorRequired == 0) || (deviceVendorId == vendorRequired) ) {

                    if ( ! usbManager.hasPermission(device)) {
                        mostraNaTela("=============== Device Localizado NAO tem permissao")
                        val intent: PendingIntent = PendingIntent.getBroadcast(myContext, 0, Intent(ArduinoSerialDevice.ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT)
                        usbManager.requestPermission(device, intent)
                    } else {
                        mostraNaTela("=============== Device Localizado TEM permissao")
                        mostraNaTela("Device Selecionado")
                        Timber.i("Device Selected")
                        selectedDevice = device
                        return selectedDevice
                    }
                }
            }
        } else {
            mostraNaTela("No serial device connected")
            Timber.i("No serial device connected")
        }
        return selectedDevice
    }

}