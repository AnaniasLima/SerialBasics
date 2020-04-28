package com.example.serialbasics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.icu.text.DateFormat.getDateTimeInstance
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import android.net.wifi.WifiManager
import android.os.Handler
import android.util.Log
import com.example.serialbasics.Data.Model.ConnectThread
import com.example.serialbasics.Data.Model.EventType
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*



class MainActivity : AppCompatActivity() {

    private var USB_SERIAL_REQUEST_INTERVAL = 30000L
    private var USB_SERIAL_TIME_TO_CONNECT_INTERVAL = 10000L
    private var usbSerialRequestHandler = Handler()
    private var stringGiganteMostraNaTela: String = ""

    private var mostraNaTelaHandler = Handler()
    private var updateMostraNaTela = Runnable {
        textView.setText(stringGiganteMostraNaTela)
    }

    fun mostraNaTela(str:String) {
        val strHora1 = SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().time)
        val newString = "$strHora1 - $str"

        Timber.i(newString)

        stringGiganteMostraNaTela = "  $newString\n$stringGiganteMostraNaTela"

        mostraNaTelaHandler.removeCallbacks(updateMostraNaTela)
        mostraNaTelaHandler.postDelayed(updateMostraNaTela, 10)
    }

    fun usbSerialContinueChecking() {
        var delayToNext: Long = USB_SERIAL_REQUEST_INTERVAL

        if ( ! ConnectThread.isConnected ) {
            delayToNext = USB_SERIAL_TIME_TO_CONNECT_INTERVAL
            mostraNaTela("agendando proximo STATUS_REQUEST para:---" + SimpleDateFormat("HH:mm:ss").format(
                Calendar.getInstance().time.time.plus(delayToNext)) + "(" + delayToNext.toString() + ")")
        }


        usbSerialRequestHandler.removeCallbacks(usbSerialRunnable)
        usbSerialRequestHandler.postDelayed(usbSerialRunnable, delayToNext)
    }

    fun usbSerialImediateChecking(delayToNext: Long) {

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

    private var usbSerialRunnable = Runnable {
        if ( ConnectThread.isConnected ) {
//            mostraNaTela("usbSerialRunnable Conectado")
            btnStatusRequest.isEnabled = true
            btnStatusNoteiro.isEnabled = true
        } else {
            mostraNaTela("usbSerialRunnable NAO Conectado")
            btnStatusRequest.isEnabled = false
            btnStatusNoteiro.isEnabled = false
            ArduinoSerialDevice.connect()
        }

        usbSerialContinueChecking()
    }

    private var loopDeStatusRequest = Runnable {
        for (i in 1.. 10) {
            ArduinoSerialDevice.sendData(EventType.FW_STATUS_RQ)
            Thread.sleep(30)
        }
    }

    private var loopNoteiroRequest = Runnable {
        for (i in 1.. 3) {
            ArduinoSerialDevice.sendData(EventType.FW_NOTEIRO)
            Thread.sleep(300)
        }
    }


//    var onConnected = Runnable {
//        Timber.i("Ativando controles para device Conectado")
//        btnSendCmd1.isEnabled = true
//        btnSendCmd2.isEnabled = true
//    }
//
//    var onDisconnected = Runnable {
//        Timber.i("Ativando controles para device Desconectado")
//        btnSendCmd1.isEnabled = false
//        btnSendCmd2.isEnabled = false
//    }


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


        btnStatusRequest.setOnClickListener {
            Thread {
                loopDeStatusRequest.run()
            }.start()
        }


        btnStatusNoteiro.setOnClickListener {
            Thread {
                loopNoteiroRequest.run()
            }.start()
        }



        btnClear.setOnClickListener {
            stringGiganteMostraNaTela = ""
            textView.setText(stringGiganteMostraNaTela)
        }

        btntag.setOnClickListener {
            mostraNaTela("")
            mostraNaTela("")
        }

        btnEchoSend.setOnClickListener {
            if ( ArduinoSerialDevice.getLogLevel(FunctionType.FX_TX) == 0) {
                btnEchoSend.text = getString(R.string.sendOff)
                ArduinoSerialDevice.setLogLevel(FunctionType.FX_TX, 1)
            }  else {
                btnEchoSend.text = getString(R.string.sendOn)
                ArduinoSerialDevice.setLogLevel(FunctionType.FX_TX, 0)
            }
        }

        btnEchoReceive.setOnClickListener {
            if ( ArduinoSerialDevice.getLogLevel(FunctionType.FX_RX) == 0) {
                btnEchoReceive.text = getString(R.string.receiveOff)
                ArduinoSerialDevice.setLogLevel(FunctionType.FX_RX, 1)
            }  else {
                btnEchoReceive.text = getString(R.string.receiveOn)
                ArduinoSerialDevice.setLogLevel(FunctionType.FX_RX, 0)
            }
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
