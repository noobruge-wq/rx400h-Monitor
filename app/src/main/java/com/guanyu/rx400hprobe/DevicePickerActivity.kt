package com.guanyu.rx400hprobe

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ScrollView
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

    @Suppress("DEPRECATION")
    private fun applySafeInsets(view: View, horizontalDp: Int, verticalDp: Int) {
        val horizontal = dp(horizontalDp)
        val vertical = dp(verticalDp)
        view.setOnApplyWindowInsetsListener { target, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                target.setPadding(safe.left + horizontal, safe.top + vertical,
                    safe.right + horizontal, safe.bottom + vertical)
            } else {
                var left = insets.systemWindowInsetLeft
                var top = insets.systemWindowInsetTop
                var right = insets.systemWindowInsetRight
                var bottom = minOf(insets.systemWindowInsetBottom, insets.stableInsetBottom)
                if (Build.VERSION.SDK_INT >= 28) {
                    insets.displayCutout?.let { cutout ->
                        left = maxOf(left, cutout.safeInsetLeft)
                        top = maxOf(top, cutout.safeInsetTop)
                        right = maxOf(right, cutout.safeInsetRight)
                        bottom = maxOf(bottom, cutout.safeInsetBottom)
                    }
                }
                target.setPadding(left + horizontal, top + vertical, right + horizontal, bottom + vertical)
            }
            insets
        }
        view.post { view.requestApplyInsets() }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private data class DeviceEntry(val name: String, val address: String)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) showDevices() else finish()
    }
}
