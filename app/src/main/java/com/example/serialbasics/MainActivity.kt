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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.R.attr.name





class MainActivity : AppCompatActivity(), UsbSerialInterface.UsbReadCallback {

    lateinit var m_usbManager: UsbManager
    var m_device: UsbDevice? = null
    var m_serial: UsbSerialDevice? = null
    var m_connection: UsbDeviceConnection? = null


    val ACTION_USB_PERMISSION = "permission"
    val ACTION_WIFI_PERMISSION = "permission"


    var pktArrayDeBytes = ByteArray(512)
    var pktTamanho: Int = 0
    var pktInd:Int=0;


    private val mCallback = UsbSerialInterface.UsbReadCallback {
        onReceivedData(it)
    }

    // onde chegam as respostas do arduino
    override fun onReceivedData(pkt: ByteArray) {
        var tam:Int = pkt.size
//        var i:Int=0
        var ch:Byte
//        var mandou:Int = 0

        if ( tam == 0) {
            return
        }

//        println("Tam = $tam   pktsize=${pkt.size}")

        for ( i in 0 until tam) {
            ch  =   pkt[i]
            if ( ch.toInt() == 0 ) break;
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


    public fun onCommandReceived(commandReceived: String) {
        Timber.d("commandReceived: ${commandReceived}")
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

//          Timber.e("------ [${SimpleDateFormat("dd/MM/yy-HHmmss", Locale.US).format(Date())}] ------")

        Timber.i("WWW1 Memoria disponível: %d", Runtime.getRuntime().freeMemory())
        Timber.i("WWW1 Memoria disponível: %d", Runtime.getRuntime().freeMemory())
        Timber.i("WWW1 Memoria disponível: %d", Runtime.getRuntime().freeMemory())
        Timber.i("WWW1 Memoria disponível: %d", Runtime.getRuntime().freeMemory())
        Runtime.getRuntime().gc()
//        System.gc()
        Timber.i("WWW1 Memoria disponível: %d", Runtime.getRuntime().freeMemory())
        Timber.i("WWW1 Memoria disponível: %d", Runtime.getRuntime().freeMemory())
        Timber.i("WWW1 Memoria disponível: %d", Runtime.getRuntime().freeMemory())
        Timber.i("WWW1 Memoria disponível: %d", Runtime.getRuntime().freeMemory())
        Runtime.getRuntime().gc()
        Timber.i("WWW1 Memoria disponível: %d", Runtime.getRuntime().freeMemory())
        Timber.i("WWW1 Memoria disponível: %d", Runtime.getRuntime().freeMemory())
        Timber.i("WWW1 Memoria disponível: %d", Runtime.getRuntime().freeMemory())
        Timber.i("WWW1 Memoria disponível: %d", Runtime.getRuntime().freeMemory())



        m_usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        usbSetFilters()

        wifiSetFilters()

        on.setOnClickListener { sendData("{\\\"cmd\\\":\\\"fw_status_rq\\\",\\\"action\\\":\\\"question\\\",\\\"timestamp\\\":\\\"1584544328020\\\",\\\"noteiroOnTimestamp\\\":\\\"\\\"}\n") }
        off.setOnClickListener { sendData("x\r\n") }
        disconnect.setOnClickListener { disconnect() }
        connect.setOnClickListener { startUsbConnecting() }
    }


    private fun usbSetFilters() {
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(broadcastReceiver, filter)
    }

    private fun wifiSetFilters() {
        val filter = IntentFilter()
        filter.addAction(ACTION_WIFI_PERMISSION)
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        registerReceiver(broadcastReceiverWifi, filter)
    }


    private fun startUsbConnecting() {
        val usbDevices : HashMap<String, UsbDevice>? = m_usbManager.deviceList
        if ( !usbDevices?.isEmpty()!!) {
            var keep = true
            usbDevices.forEach { entry ->
                m_device = entry.value
                var deviceVendorId: Int = m_device!!.vendorId
                Timber.i("Device Vendor.Id: %d",  deviceVendorId)
                if ( deviceVendorId == 9025) {


                    val intent: PendingIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
                    m_usbManager.requestPermission(m_device, intent)


                    keep = false
                    Timber.i("Connection Successful")
                } else {
                    m_connection = null
                    m_device = null
                    Timber.i("Unable to connect device. Different VendorId")
                }

                if ( ! keep ) {
                    return
                }
            }
        } else {
            Timber.i("No serial device connected")
        }
    }

    private fun sendData(input:String) {
        m_serial?.write(input.toByteArray())
        Timber.i("sendData: [%s]", input)
    }

    private fun disconnect() {
        m_serial?.close()
        Timber.i("disconnect")
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            if ( intent != null) {
                Timber.i("WWW intent.action = ${intent.action.toString()}")

                if (intent.action!! == ACTION_USB_PERMISSION ) {
                    val granted: Boolean = intent.extras!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                    if ( granted ) {
                        m_connection = m_usbManager.openDevice(m_device)
                        m_serial = UsbSerialDevice.createUsbSerialDevice(m_device, m_connection)
                        if ( m_serial != null ) {
                            if ( m_serial!!.open()) {
                                m_serial!!.setBaudRate(57600)

                                m_serial!!.read( mCallback)


//                                m_serial!!.setDataBits(UsbSerialInterface.DATA_BITS_8)
//                                m_serial!!.setStopBits(UsbSerialInterface.STOP_BITS_1)
//                                m_serial!!.setParity(UsbSerialInterface.PARITY_NONE)
//                                m_serial!!.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)
                            } else {
                                Timber.e("Serial Port  not open")
                            }
                        } else {
                            Timber.e("Serial Port is null")

                        }

                    } else {
                        Timber.e("Serial Permission not granted")
                    }
                } else if ( intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED){
                    startUsbConnecting()
                } else if ( intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED){
                    disconnect()
                }


            } else {
                Timber.e("NULL intent received-----------")
            }

        }
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
