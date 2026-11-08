package com.comedero.ratita

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.*

class MainActivity : AppCompatActivity() {

    private val BT_DEVICE_NAME = "Comedero-Ratita"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var btSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStreamReader: BufferedReader? = null
    private val btAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private lateinit var tvStatus: TextView
    private lateinit var etIp: EditText
    private lateinit var btnFeed: Button
    private lateinit var etInterval: EditText
    private lateinit var btnSaveInterval: Button
    private lateinit var etH1: EditText
    private lateinit var etH2: EditText
    private lateinit var btnSaveHours: Button
    private lateinit var btnConnectBt: Button

    private lateinit var btnScanWifi: Button
    private lateinit var spWifiList: Spinner
    private lateinit var etWifiSsid: EditText
    private lateinit var etWifiPass: EditText
    private lateinit var btnSaveWifi: Button

    private val wifiNetworksList = ArrayList<String>()
    private lateinit var spinnerAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        etIp = findViewById(R.id.etIp)
        btnFeed = findViewById(R.id.btnFeed)
        etInterval = findViewById(R.id.etInterval)
        btnSaveInterval = findViewById(R.id.btnSaveInterval)
        etH1 = findViewById(R.id.etH1)
        etH2 = findViewById(R.id.etH2)
        btnSaveHours = findViewById(R.id.btnSaveHours)
        btnConnectBt = findViewById(R.id.btnConnectBt)

        btnScanWifi = findViewById(R.id.btnScanWifi)
        spWifiList = findViewById(R.id.spWifiList)
        etWifiSsid = findViewById(R.id.etWifiSsid)
        etWifiPass = findViewById(R.id.etWifiPass)
        btnSaveWifi = findViewById(R.id.btnSaveWifi)

        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, wifiNetworksList)
        spWifiList.adapter = spinnerAdapter
        wifiNetworksList.add("-- Redes detectadas --")
        spinnerAdapter.notifyDataSetChanged()

        spWifiList.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos > 0) {
                    val sel = wifiNetworksList[pos].split(" ")[0]
                    etWifiSsid.setText(sel)
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        checkPermissions()

        btnConnectBt.setOnClickListener { connectBluetooth() }

        btnFeed.setOnClickListener {
            sendCommand("ALIMENTAR", "/alimentar")
        }

        btnSaveInterval.setOnClickListener {
            val mins = etInterval.text.toString().trim()
            if (mins.isNotEmpty()) {
                sendCommand("INTERVALO:$mins", "/config?intervalo=$mins")
                Toast.makeText(this, "Guardando intervalo de $mins min...", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveHours.setOnClickListener {
            val h1 = etH1.text.toString().trim()
            val h2 = etH2.text.toString().trim()
            if (h1.isNotEmpty() && h2.isNotEmpty()) {
                sendCommand("HORAS:$h1,$h2", "/config?h1=$h1&h2=$h2")
                Toast.makeText(this, "Guardando horarios ($h1:00 y $h2:00)...", Toast.LENGTH_SHORT).show()
            }
        }

        btnScanWifi.setOnClickListener {
            scanWifiNetworks()
        }

        btnSaveWifi.setOnClickListener {
            val ssid = etWifiSsid.text.toString().trim()
            val pass = etWifiPass.text.toString().trim()
            if (ssid.isEmpty()) {
                Toast.makeText(this, "Escribe o selecciona el nombre de la red", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val encodedSsid = URLEncoder.encode(ssid, "UTF-8")
            val encodedPass = URLEncoder.encode(pass, "UTF-8")
            sendCommand("SET_WIFI:$ssid,$pass", "/set_wifi?ssid=$encodedSsid&pass=$encodedPass")
            Toast.makeText(this, "Guardando Wi-Fi '$ssid' en memoria Flash...", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                1
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectBluetooth() {
        if (btAdapter == null || !btAdapter.isEnabled) {
            Toast.makeText(this, "Enciende el Bluetooth de tu smartphone", Toast.LENGTH_SHORT).show()
            return
        }

        tvStatus.text = "Buscando $BT_DEVICE_NAME..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pairedDevices: Set<BluetoothDevice>? = btAdapter.bondedDevices
                val device = pairedDevices?.find { it.name == BT_DEVICE_NAME }

                if (device != null) {
                    btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    btSocket?.connect()
                    outputStream = btSocket?.outputStream
                    inputStreamReader = BufferedReader(InputStreamReader(btSocket?.inputStream))
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "● Conectado por Bluetooth a $BT_DEVICE_NAME"
                        Toast.makeText(this@MainActivity, "Conexión Bluetooth lista!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "● No emparejado (Vincula '$BT_DEVICE_NAME' en Ajustes)"
                        Toast.makeText(this@MainActivity, "Vincula '$BT_DEVICE_NAME' primero en Ajustes Bluetooth", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "● Error Bluetooth (Usando Wi-Fi)"
                }
            }
        }
    }

    private fun scanWifiNetworks() {
        Toast.makeText(this, "Buscando redes cercanas...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            // Intentar escanear por Bluetooth primero
            if (outputStream != null && inputStreamReader != null) {
                try {
                    outputStream?.write("SCAN_WIFI\n".toByteArray())
                    outputStream?.flush()

                    val tempNetworks = ArrayList<String>()
                    var line: String?
                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < 8000) {
                        if (inputStreamReader?.ready() == true) {
                            line = inputStreamReader?.readLine() ?: break
                            if (line.startsWith("[RED]")) {
                                tempNetworks.add(line.replace("[RED] ", ""))
                            } else if (line.contains("[FIN_SCAN]")) {
                                break
                            }
                        }
                    }

                    if (tempNetworks.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            wifiNetworksList.clear()
                            wifiNetworksList.add("-- Redes detectadas --")
                            wifiNetworksList.addAll(tempNetworks)
                            spinnerAdapter.notifyDataSetChanged()
                            Toast.makeText(this@MainActivity, "Se encontraron ${tempNetworks.size} redes Wi-Fi", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                } catch (e: Exception) {}
            }

            // Fallback por Wi-Fi HTTP
            val ip = etIp.text.toString().trim()
            if (ip.isNotEmpty()) {
                try {
                    val url = URL("http://$ip/scan_wifi")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    if (conn.responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().readText()
                        val jsonArr = JSONArray(response)
                        val tempNetworks = ArrayList<String>()
                        for (i in 0 until jsonArr.length()) {
                            val obj = jsonArr.getJSONObject(i)
                            tempNetworks.add("${obj.getString("ssid")} (${obj.getInt("rssi")} dBm)")
                        }
                        withContext(Dispatchers.Main) {
                            wifiNetworksList.clear()
                            wifiNetworksList.add("-- Redes detectadas --")
                            wifiNetworksList.addAll(tempNetworks)
                            spinnerAdapter.notifyDataSetChanged()
                            Toast.makeText(this@MainActivity, "Se encontraron ${tempNetworks.size} redes Wi-Fi", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No se pudo escanear. Escribe el SSID manualmente.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun sendCommand(btCmd: String, httpEndpoint: String) {
        CoroutineScope(Dispatchers.IO).launch {
            if (outputStream != null) {
                try {
                    outputStream?.write((btCmd + "\n").toByteArray())
                    outputStream?.flush()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Enviado por Bluetooth!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                } catch (e: Exception) {
                    outputStream = null
                }
            }

            val ip = etIp.text.toString().trim()
            if (ip.isNotEmpty()) {
                try {
                    val url = URL("http://$ip$httpEndpoint")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    if (conn.responseCode == 200) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Enviado por Wi-Fi con éxito!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error al enviar comando", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            btSocket?.close()
        } catch (e: Exception) {}
    }
}
