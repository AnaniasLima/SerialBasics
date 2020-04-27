package com.example.serialbasics

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import com.example.serialbasics.Data.Model.ConnectThread
import com.example.serialbasics.Data.Model.Event
import com.example.serialbasics.Data.Model.EventType
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface.UsbReadCallback
import timber.log.Timber
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap


@SuppressLint("StaticFieldLeak")
object ArduinoSerialDevice: UsbReadCallback {

    var mainActivity: AppCompatActivity? = null
    var isConnected: Boolean  = false
    var pktArrayDeBytes = ByteArray(512)
    var pktInd:Int=0
    var usbManager  : UsbManager? = null
    var myContext: Context? = null
    val ACTION_USB_PERMISSION = "com.example.serialbasics.permission"

    var lastNoteiroTimestamp: String = ""

    var usbSerialDevice: UsbSerialDevice? = null
    var EVENT_LIST: MutableList<Event> = mutableListOf()
    var lastNoteiroOnTimestamp: String = ""
    var invalidJsonPacketsReceived:Int = 0

    var connectThread: ConnectThread? = null

    fun mostraNaTela(str:String) {
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
                if ( pktInd  > 0 ) {
                    Timber.d("Vai desprezar: ${String(pktArrayDeBytes, 0, pktInd)}")
                }
                pktInd = 0
            }
            if ( ch.toInt() in 32..126 ) {
                if ( pktInd < (pktArrayDeBytes.size - 1) ) {
                    pktArrayDeBytes[pktInd++] = ch
                    pktArrayDeBytes[pktInd] = 0
                    if ( ch.toChar() == '}') {
                        onCommandReceived(String(pktArrayDeBytes, 0, pktInd))
                        pktInd = 0
                    }
                } else {
                    // ignora tudo
                    pktInd = 0
                }
            }
        }
    }

    val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if ( intent != null && usbManager != null) {
                mostraNaTela("WWW------------------------- intent.action = " + intent.action.toString())
                when (intent.action!!) {
                    ACTION_USB_PERMISSION -> {
                        val granted: Boolean = intent.extras!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                        mostraNaTela("ACTION_USB_PERMISSION------------------------- Permmissao concedida = ${granted.toString()}")
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        (mainActivity as MainActivity).mostraNaTela("ACTION_USB_DEVICE_ATTACHED")
                        connect()
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        (mainActivity as MainActivity).mostraNaTela("ACTION_USB_DEVICE_DETACHED")
                        disconnect()
                    }
                }
            }
        }
    }



    fun connect() {
        mostraNaTela("Verificando conexão...")

        // se a thread não estiver mais rodando, libera a thread
        if (connectThread != null) {
            if (!connectThread!!.isRunning()) {
                Timber.i("connectThread != NULL mas não esta mais rodando. Fazendo connectThread=NULL")
            }
        }

        if (usbManager != null) {
            isConnected = usbSerialDevice?.isOpen ?: false
            if (isConnected) {
                mostraNaTela("Já estava connectado.")
                if (connectThread != null) {
                    if (connectThread!!.isRunning()) {
                        return
                    } else {
                        Timber.e("Estava conectadado mas connectThread não estava mais isRunning.")
                        connectThread = null
                    }
                } else {
                    Timber.e("Estava conectadado mas connectThread esta NULL.")
                    mostraNaTela("Thread estava inativa.")
                }
            }
        }

        isConnected = false
        usbSerialDevice = null

        if ( usbManager != null ) {
            if ( usbManager!!.deviceList.size > 0  ) {
                mostraNaTela("Tentando connect...")
                connectThread = ConnectThread(ConnectThread.CONNECT)
                if (connectThread != null ) {
                    Timber.i("Startando thread para tratar da conexao")
                    connectThread!!.start()
                } else {
                    Timber.e("Falha na criação da thread ")
                }
            }
        }
    }


    fun connectInBackground() : Boolean {

        if ( usbManager != null ) {
            isConnected = usbSerialDevice?.isOpen ?: false
            if ( isConnected ) {
               return true
            }
        }

        try {
            val m_device    : UsbDevice? = selectDevice(0)

            if ( m_device != null ) {
                val m_connection: UsbDeviceConnection? = usbManager!!.openDevice(m_device)
                mostraNaTela("hasPermission = " + usbManager!!.hasPermission(m_device).toString())
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


    fun disconnectInBackground() {
        if ( usbSerialDevice != null) {
            Timber.i("-------- disconnectInBackground Inicio")
            if ( usbSerialDevice!!.isOpen )  {
                usbSerialDevice!!.close()
                isConnected = false
            }
            usbSerialDevice = null
            Timber.i("-------- disconnectInBackground Fim")
            (mainActivity as MainActivity).usbSerialImediateChecking(100)
        }
    }


    fun disconnect() {

        mostraNaTela("Vai verificar usbSerialDevice em disconnect...")

        if ( connectThread != null ) {
            Timber.i("connectThread not null em disconnect vamos chamar finish")
            connectThread!!.finish()
            Timber.i("fazendo connectThread = NULL")
            connectThread = null
        } else {
            Timber.i("Disparando thread para desconectar")
            ConnectThread(ConnectThread.DISCONNECT).start()
        }

    }


    private fun selectDevice(vendorRequired:Int) : UsbDevice? {
        var selectedDevice: UsbDevice? = null
        if ( usbManager != null ) {
            val deviceList : HashMap<String, UsbDevice>? = usbManager!!.deviceList
            if ( !deviceList?.isEmpty()!!) {
                var device: UsbDevice?
                println("Device list size: ${deviceList.size}")
                deviceList.forEach { entry ->
                    device = entry.value
                    val deviceVendorId: Int = device!!.vendorId
                    mostraNaTela("Device localizado. Vendor:" + deviceVendorId.toString() + "  productId: " + device!!.productId + "  Name: " + device!!.productName)
                    Timber.i("Device Vendor.Id: %d",  deviceVendorId)
                    if ( (vendorRequired == 0) || (deviceVendorId == vendorRequired) ) {

                        if ( ! usbManager!!.hasPermission(device)) {
                            mostraNaTela("=============== Device Localizado NAO tem permissao")
                            val intent: PendingIntent = PendingIntent.getBroadcast(myContext, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT)
                            usbManager!!.requestPermission(device, intent)
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
        } else {
            mostraNaTela("first we need select usbManager")
            Timber.i("first we need select usbManager")
        }
        return selectedDevice
    }




    fun usbSetFilters() {
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        myContext!!.registerReceiver(broadcastReceiver, filter)
    }


    fun onCommandReceived(commandReceived: String) {
        Timber.d("commandReceived: ${commandReceived}")
    }


    fun sendData(eventType: EventType) {
        when(eventType) {
            EventType.FW_STATUS_RQ -> {
                connectThread!!.send("StatusRequest", Event(eventType = EventType.FW_STATUS_RQ, action = Event.QUESTION))
            }

            EventType.FW_NOTEIRO -> {
                val event = Event(eventType = EventType.FW_NOTEIRO, action = Event.QUESTION)
                lastNoteiroTimestamp = event.timeStamp
                connectThread!!.send("NoteiroRequest", event)
            }

        }


    }



}