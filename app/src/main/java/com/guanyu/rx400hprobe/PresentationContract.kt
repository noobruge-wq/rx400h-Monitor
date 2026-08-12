package com.guanyu.rx400hprobe

/**
 * V0.2.0 presentation contract.
 *
 * The renderer consumes these snapshots and never touches Bluetooth,
 * requests, decoders or vehicle truth. Per-field versions allow
 * change-driven View updates.
 */
internal data class DashboardSnapshot(
    val speedKph: Double?,
    val speedFresh: Boolean,
    val speedVersion: Long,
    val socPct: Double?,
    val socFresh: Boolean,
    val socVersion: Long,
    val batteryTempMinC: Double?,
    val batteryTempMaxC: Double?,
    val batteryTempAvgC: Double?,
    val batteryTempFresh: Boolean,
    val batteryTempVersion: Long,
    val hvPowerKw: Double?,
    val hvPowerFresh: Boolean,
    val hvPowerVersion: Long,
    val rpm: Double?,
    val rpmFresh: Boolean,
    val rpmVersion: Long,
    val coolantC: Double?,
    val coolantFresh: Boolean,
    val coolantVersion: Long,
    val adapterVoltageV: Double?,
    val adapterVoltageFresh: Boolean,
    val adapterVoltageVersion: Long,
    val icePowerKw: Double?,
    val icePowerFresh: Boolean,
    val icePowerVersion: Long,
    val idleCheckActive: Boolean,
    val idleCheckVersion: Long
)

internal data class DashboardStatus(
    val deviceName: String,
    val connection: String,
    val mode: String,
    val logging: String,
    val reconnectCount: Int,
    val notice: String?,
    val error: String?,
    val warning: Boolean
)
