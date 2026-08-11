package com.guanyu.rx400hprobe

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

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
            adapter?.bondedDevices
                ?.map { device ->
                    DeviceEntry(
                        name = device.name ?: "Unknown",
                        address = device.address
                    )
                }
                ?.sortedBy { it.name }
                .orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        if (devices.isEmpty()) {
            val message = TextView(this).apply {
                text = "没有可用的已配对蓝牙设备。请先在安卓系统蓝牙设置中配对 OBD 适配器。"
                textSize = 18f
            }
            val scroll = ScrollView(this).apply {
                isFillViewport = true
                addView(message)
            }
            applySafeInsets(scroll, horizontalDp = 24, verticalDp = 24)
            setContentView(scroll)
            return
        }
        val list = ListView(this).apply { clipToPadding = false }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, devices.map { it.name })
        list.setOnItemClickListener { _, _, position, _ ->
            val device = devices[position]
            setResult(RESULT_OK, Intent().putExtra("name", device.name).putExtra("address", device.address))
            finish()
        }
        applySafeInsets(list, horizontalDp = 0, verticalDp = 8)
        setContentView(list)
    }

    private fun applySafeInsets(view: View, horizontalDp: Int, verticalDp: Int) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            target.setPadding(
                safe.left + dp(horizontalDp),
                safe.top + dp(verticalDp),
                safe.right + dp(horizontalDp),
                safe.bottom + dp(verticalDp)
            )
            insets
        }
        view.post { ViewCompat.requestApplyInsets(view) }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private data class DeviceEntry(val name: String, val address: String)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) showDevices() else finish()
    }
}
