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
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import timber.log.Timber
import java.io.IOException


@SuppressLint("StaticFieldLeak")
object ArduinoSerialDevice: UsbSerialInterface.UsbReadCallback {

    var mainActivity: AppCompatActivity? = null
    var isConnected: Boolean  = false
    var pktArrayDeBytes = ByteArray(512)
    var pktTamanho: Int = 0
    var pktInd:Int=0

    var usbManager: UsbManager? = null
    var m_device: UsbDevice? = null
    var m_connection: UsbDeviceConnection? = null

    var myContext: Context? = null

    val ACTION_USB_PERMISSION = "permission"

//        var lastNoteiroTimestamp: String = ""


    var usbSerialDevice: UsbSerialDevice? = null
    var EVENT_LIST: MutableList<Event> = mutableListOf()
    var lastNoteiroOnTimestamp: String = ""
    var invalidJsonPacketsReceived:Int = 0
    var mcallback: UsbSerialInterface.UsbReadCallback? = null

    private val mCallback = UsbSerialInterface.UsbReadCallback {
        onReceivedData(it)
    }


    // onde chegam as respostas do arduino
    override fun onReceivedData(pkt: ByteArray) {
        var tam:Int = pkt.size
        var ch:Byte

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



    val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            if ( intent != null && usbManager != null) {
                Timber.i("WWW------------------------- intent.action = ${intent.action.toString()}")

                if (intent.action!! == ACTION_USB_PERMISSION ) {
                    val granted: Boolean = intent.extras!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                    if ( granted ) {
                        Timber.i("WWW------------------------- Permmissao concedida")
                    } else {
                        Timber.e("Serial Permission not granted")
                    }
                } else if ( intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED){
                    Timber.i("WWW------------------------- ==> ACTION_USB_DEVICE_ATTACHED")
                } else if ( intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED){
                    Timber.i("WWW------------------------- ==> ACTION_USB_DEVICE_DETACHED")

                    Thread().run {
                        disconnect()
                    }
                }
            } else {
                Timber.e("NULL intent received-----------")
            }
        }
    }


    fun connect() : Boolean {

        try {
            if ( m_device == null ) {
                m_device = selectDevice(0)
            } else {
                Timber.i("m_device already selected")
            }

            if ( m_device != null ) {
                if ( usbSerialDevice != null  ) {
                    isConnected = usbSerialDevice!!.isOpen
                } else {
                    isConnected = false
                }

                if ( ! isConnected ) {
                    if ( m_device != null) {
                        if ( m_connection == null) {
                            m_connection = usbManager!!.openDevice(m_device)
                            if (m_connection != null) {
                                if ( usbSerialDevice == null ) {
                                    Timber.e("Creating usbSerialDevice")
                                    usbSerialDevice = UsbSerialDevice.createUsbSerialDevice(m_device, m_connection)
                                    if ( usbSerialDevice != null ) {
                                        Timber.e("Opening usbSerialDevice")
                                        if ( usbSerialDevice!!.open()) {
                                            usbSerialDevice!!.setBaudRate(57600)
                                            usbSerialDevice!!.read( mcallback )
                                            isConnected = true
                                        } else {
                                            m_connection = usbManager!!.openDevice(m_device)
                                        }
                                    } else {
                                        Timber.e("can´t create usbSerialDevice. createUsbSerialDevice(m_device, m_connection) Failure.")
                                    }
                                } else {
                                    Timber.e("usbSerialDevice already in use (not null)")
                                }
                            } else {
                                Timber.e("can´t create m_connection. openDevice(m_device) Failure.")
                            }
                        } else {
                            Timber.e("m_connection already in use (not null)")
                        }
                    } else {
                        Timber.e("m_device already in use (not null)")
                    }
                }
            }

        } catch ( e: IOException ) {
            usbSerialDevice = null
            m_connection = null
            m_device = null
            isConnected = false
        } finally {
            if ( isConnected ) {
                (mainActivity as MainActivity).onConnected.run()
            }  else {
                (mainActivity as MainActivity).onDisconnected.run()
                usbSerialDevice = null
                m_connection = null
                m_device = null
            }

        }


        return false
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
                    var deviceVendorId: Int = device!!.vendorId
                    Timber.i("Device Vendor.Id: %d",  deviceVendorId)
                    if ( (vendorRequired == 0) || (deviceVendorId == vendorRequired) ) {
//                        val intent: PendingIntent = PendingIntent.getBroadcast(myContext, 0, Intent(ACTION_USB_PERMISSION), 0)
//                        usbManager!!.requestPermission(device, intent)
                        Timber.i("Device Selected")
                        selectedDevice = device
                        return selectedDevice
                    }
                }
            } else {
                Timber.i("No serial device connected")
            }
        } else {
            Timber.i("first we need select usbManager")
        }
        return selectedDevice
    }


    fun disconnect() {
        Timber.i("-------- disconnect Inicio")
        if ( usbSerialDevice!!.isOpen )  {
            usbSerialDevice!!.close()
            isConnected = false
        }
        usbSerialDevice = null
        m_connection = null
        m_device = null
        Timber.i("-------- disconnect Fim")
//        (mainActivity as MainActivity).usbSerialContinueChecking()
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


    fun sendData(input:String) {
        usbSerialDevice?.write(input.toByteArray())
        Timber.i("sendData: [%s]", input)
    }




}