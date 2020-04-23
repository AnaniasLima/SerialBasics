package com.example.serialbasics

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import android.net.wifi.WifiManager
import android.os.Handler
import com.example.serialbasics.Data.Model.ConnectThread
import java.text.SimpleDateFormat
import java.util.*



class MainActivity : AppCompatActivity() {


    private var USB_SERIAL_REQUEST_INTERVAL = 30000L
    private var USB_SERIAL_TIME_TO_CONNECT_INTERVAL = 10000L

    private var usbSerialRequestHandler = Handler()

    lateinit var connectThread: ConnectThread

    private var stringGiganteMostraNaTela: String = ""
    private var linhaMostraNaTela = 0

    private var updateMostraNaTela = Runnable {
        textView.setText(stringGiganteMostraNaTela)
    }


    fun mostraNaTela(str:String) {
        stringGiganteMostraNaTela = str + "\n" + stringGiganteMostraNaTela
        updateMostraNaTela.run()
//        textView.setText(stringGiganteMostraNaTela)
    }

    public fun usbSerialContinueChecking() {
        var delayToNext: Long = USB_SERIAL_REQUEST_INTERVAL

        if ( ! ArduinoSerialDevice.isConnected ) {
            delayToNext = USB_SERIAL_TIME_TO_CONNECT_INTERVAL
        }

        mostraNaTela("agendando proximo STATUS_REQUEST para:---" + SimpleDateFormat("HH:mm:ss").format(
            Calendar.getInstance().time.time.plus(delayToNext)) + "(" + delayToNext.toString() + ")")

        Timber.i("agendando proximo STATUS_REQUEST para:--- ${SimpleDateFormat("HH:mm:ss").format(
            Calendar.getInstance().time.time.plus(delayToNext))} (${delayToNext})")
        usbSerialRequestHandler.removeCallbacks(usbSerialRunnable)
        usbSerialRequestHandler.postDelayed(usbSerialRunnable, delayToNext)
    }

    public fun usbSerialImediateChecking(delayToNext: Long) {

        mostraNaTela("agendando proximo STATUS_REQUEST para:---" + SimpleDateFormat("HH:mm:ss").format(
            Calendar.getInstance().time.time.plus(delayToNext)) + "(" + delayToNext.toString() + ")")

        Timber.i("===== Agendandamento imediato de usbSerialRunnable")
        usbSerialRequestHandler.removeCallbacks(usbSerialRunnable)
        usbSerialRequestHandler.postDelayed(usbSerialRunnable, delayToNext)
    }



//    public fun usbSerialTimeToConnectChecking() {
//        val time: Long = USB_SERIAL_TIME_TO_CONNECT_INTERVAL
//        Timber.i("agendando [${usbSerialRunnable}] proximo STATUS_REQUEST para:--- ${SimpleDateFormat("HH:mm:ss").format(
//            Calendar.getInstance().time.time.plus(time))} (${time})")
//
//        usbSerialRequestHandler.removeCallbacks(usbSerialRunnable)
//        usbSerialRequestHandler.postDelayed(usbSerialRunnable, time)
//    }

    private var usbSerialRunnable = Runnable {
        if ( ArduinoSerialDevice.isConnected ) {
            mostraNaTela("Conectado")
            Timber.i("Conectado")
            btnSendCmd1.isEnabled = true
            btnSendCmd2.isEnabled = true
        } else {
            mostraNaTela("NAO Conectado")
            Timber.i("Não Conectado")
            btnSendCmd1.isEnabled = false
            btnSendCmd2.isEnabled = false

            Thread().run {
                ArduinoSerialDevice.connect()
            }


//            connectThread = ConnectThread(ConnectThread.CONNECT)
//            connectThread.start()

//            ArduinoSerialDevice.connect()
        }

        usbSerialContinueChecking()
    }

    var onConnected = Runnable {
        Timber.i("Ativando controles para device Conectado")
        btnSendCmd1.isEnabled = true
        btnSendCmd2.isEnabled = true
    }

    var onDisconnected = Runnable {
        Timber.i("Ativando controles para device Desconectado")
        btnSendCmd1.isEnabled = false
        btnSendCmd2.isEnabled = false
    }


    val ACTION_WIFI_PERMISSION = "permission"

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        ArduinoSerialDevice.usbManager =
            applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
        ArduinoSerialDevice.myContext = applicationContext
        ArduinoSerialDevice.mainActivity = this
        ArduinoSerialDevice.usbSetFilters()


        mostraNaTela("Linha 1")

        btnSendCmd1.setOnClickListener { ArduinoSerialDevice.sendData("{\\\"cmd\\\":\\\"fw_status_rq\\\",\\\"action\\\":\\\"question\\\",\\\"timestamp\\\":\\\"1584544328020\\\",\\\"noteiroOnTimestamp\\\":\\\"\\\"}\n") }
        btnSendCmd2.setOnClickListener { ArduinoSerialDevice.sendData("x\r\n") }

        usbSerialContinueChecking()
    }



    private fun wifiSetFilters() {
        val filter = IntentFilter()
        filter.addAction(ACTION_WIFI_PERMISSION)
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        registerReceiver(broadcastReceiverWifi, filter)
    }





    private val broadcastReceiverWifi = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if ( intent != null) {
                Timber.i("WWW intent.action = ${intent.action.toString()}")
            } else {
                Timber.e("NULL intent received-----------")
            }
        }
    }

}
