package com.example.serialbasics

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import com.example.serialbasics.Data.Model.ConnectThread
import com.example.serialbasics.Data.Model.Event
import com.example.serialbasics.Data.Model.EventResponse
import com.example.serialbasics.Data.Model.EventType
import timber.log.Timber

enum class FunctionType {
    FX_RX,
    FX_TX
}


@SuppressLint("StaticFieldLeak")
object ArduinoSerialDevice {

    var mainActivity: AppCompatActivity? = null
    var usbManager  : UsbManager? = null
    var myContext: Context? = null
    val ACTION_USB_PERMISSION = "com.example.serialbasics.permission"

//    var lastNoteiroTimestamp: String = ""
    var lastNoteiroOnTimestamp: String = ""

    var EVENT_LIST: MutableList<Event> = mutableListOf()

    private var connectThread: ConnectThread? = null

    private var rxLogLevel = 0
    private var txLogLevel = 0


    private fun mostraNaTela(str:String) {
        (mainActivity as MainActivity).mostraNaTela(str)
    }



    fun onEventResponse(eventResponse: EventResponse) {
        if ( eventResponse.eventType == EventType.FW_PLAY ) {
            Timber.e("=============== FW_PLAY =======================: ${eventResponse.toString()}")
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

        if ( ConnectThread.isConnected ) {
            if ( connectThread == null) {
                throw IllegalStateException("Erro interno 001")
            }
            mostraNaTela("Já estava connectado.")
            return
        }

        if ( usbManager != null ) {
            if ( usbManager!!.deviceList.size > 0  ) {
                mostraNaTela("Tentando connect...")
                connectThread = ConnectThread(ConnectThread.CONNECT, usbManager!!, mainActivity!!, myContext!!)
                if (connectThread != null ) {
                    Timber.i("Startando thread para tratar da conexao")
                    connectThread!!.start()
                } else {
                    Timber.e("Falha na criação da thread ")
                }
            }
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
            ConnectThread(ConnectThread.DISCONNECT, usbManager!!, mainActivity!!, myContext!!).start()
        }

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


    fun requestToSend(eventType: EventType, action: String) : Boolean {

        if ( ConnectThread.isConnected ) {
            try {
                when(eventType) {
                    EventType.FW_STATUS_RQ -> {
                        connectThread!!.requestToSend(eventType = EventType.FW_STATUS_RQ, action=action)
                    }

                    EventType.FW_NOTEIRO -> {
                        connectThread!!.requestToSend(eventType = EventType.FW_NOTEIRO, action=action)
                    }
                    else -> {
                        // do nothing
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return false
    }

    fun setLogLevel(function : FunctionType, value: Int) {
        when ( function) {
            FunctionType.FX_RX -> {
                rxLogLevel = value
            }
            FunctionType.FX_TX -> {
                txLogLevel = value
            }
        }
    }

    fun getLogLevel(function : FunctionType) : Int {
        when ( function) {
            FunctionType.FX_RX -> {
                return(rxLogLevel)
            }
            FunctionType.FX_TX -> {
                return(txLogLevel)
            }
        }
    }

}