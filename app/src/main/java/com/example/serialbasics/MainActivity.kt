package com.example.serialbasics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import android.net.wifi.WifiManager
import android.os.Handler
import android.util.Log
import com.example.serialbasics.Data.Model.ConnectThread
import java.text.SimpleDateFormat
import java.util.*



class MainActivity : AppCompatActivity() {

    private var USB_SERIAL_REQUEST_INTERVAL = 30000L
    private var USB_SERIAL_TIME_TO_CONNECT_INTERVAL = 10000L
    private var usbSerialRequestHandler = Handler()
//    lateinit var connectThread: ConnectThread
    private var stringGiganteMostraNaTela: String = ""

    private var mostraNaTelaHandler = Handler()
    private var updateMostraNaTela = Runnable {
        textView.setText(stringGiganteMostraNaTela)
    }


    fun mostraNaTela(str:String) {
        var strHora = SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().time)
        var newString = "$strHora - $str"

        Timber.i(newString)

        stringGiganteMostraNaTela = "  $newString\n$stringGiganteMostraNaTela"

        mostraNaTelaHandler.removeCallbacks(updateMostraNaTela)
        mostraNaTelaHandler.postDelayed(updateMostraNaTela, 10)
    }

    public fun usbSerialContinueChecking() {
        var delayToNext: Long = USB_SERIAL_REQUEST_INTERVAL

        if ( ! ArduinoSerialDevice.isConnected ) {
            delayToNext = USB_SERIAL_TIME_TO_CONNECT_INTERVAL
        }

        mostraNaTela("agendando proximo STATUS_REQUEST para:---" + SimpleDateFormat("HH:mm:ss").format(
            Calendar.getInstance().time.time.plus(delayToNext)) + "(" + delayToNext.toString() + ")")

        usbSerialRequestHandler.removeCallbacks(usbSerialRunnable)
        usbSerialRequestHandler.postDelayed(usbSerialRunnable, delayToNext)
    }

    public fun usbSerialImediateChecking(delayToNext: Long) {

        mostraNaTela("agendando STATUS_REQUEST para:---" + SimpleDateFormat("HH:mm:ss").format(
            Calendar.getInstance().time.time.plus(delayToNext)) + "(" + delayToNext.toString() + ")")

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

    var ZZC = Runnable {
        ArduinoSerialDevice.connect()
    }

    private var usbSerialRunnable = Runnable {
        Timber.i("Entrando em usbSerialRunnable")
        if ( ArduinoSerialDevice.isConnected ) {
            mostraNaTela("usbSerialRunnable Conectado")
            btnSendCmd1.isEnabled = true
            btnSendCmd2.isEnabled = true
        } else {
            mostraNaTela("usbSerialRunnable NAO Conectado")
            btnSendCmd1.isEnabled = false
            btnSendCmd2.isEnabled = false

            Timber.i("usbSerialRunnable Vai ativar thread connect")
//            Thread().run {
//                ArduinoSerialDevice.connect()
//            }


//            connectThread = ConnectThread(ConnectThread.CONNECT)
//            connectThread.start()

            ConnectThread(ConnectThread.CONNECT).start()

            Timber.i("usbSerialRunnable Vai ativar thread connect OK")
//            ArduinoSerialDevice.connect()
        }

        Timber.i("usbSerialRunnable chamando usbSerialContinueChecking")
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

        Log.i("MainActivity","========== __onCreate__   ===========" )


        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        mostraNaTela("Iniciando MainActivity")

        ArduinoSerialDevice.usbManager =
            applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
        ArduinoSerialDevice.myContext = applicationContext
        ArduinoSerialDevice.mainActivity = this
        ArduinoSerialDevice.usbSetFilters()

        btnSendCmd1.setOnClickListener { ArduinoSerialDevice.sendData("{\\\"cmd\\\":\\\"fw_status_rq\\\",\\\"action\\\":\\\"question\\\",\\\"timestamp\\\":\\\"1584544328020\\\",\\\"noteiroOnTimestamp\\\":\\\"\\\"}\n") }
        btnSendCmd2.setOnClickListener { ArduinoSerialDevice.sendData("x\r\n") }
        btnClear.setOnClickListener {
            stringGiganteMostraNaTela = ""
            textView.setText(stringGiganteMostraNaTela)
        }
        btntag.setOnClickListener {
            mostraNaTela("")
            mostraNaTela("")
        }

        Timber.i("Vai iniciar processo de pooling para ver o estado do conexao USB-SERIAL")
        usbSerialImediateChecking(100)
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
