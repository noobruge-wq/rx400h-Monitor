package com.guanyu.rx400hprobe

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView

class DevicePickerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 10)
            return
        }
        showDevices()
    }

    private fun showDevices() {
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        val devices = try {
            adapter?.bondedDevices?.sortedBy { it.name ?: it.address }.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        if (devices.isEmpty()) {
            setContentView(TextView(this).apply {
                text = "没有可用的已配对蓝牙设备。请先在安卓系统蓝牙设置中配对 OBD 适配器。"
                textSize = 18f
                setPadding(32, 32, 32, 32)
            })
            return
        }
        val list = ListView(this)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, devices.map { it.name ?: "Unknown" })
        list.setOnItemClickListener { _, _, position, _ ->
            val device = devices[position]
            setResult(RESULT_OK, Intent().putExtra("name", device.name ?: "Unknown").putExtra("address", device.address))
            finish()
        }
        setContentView(list)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) showDevices() else finish()
    }
}
