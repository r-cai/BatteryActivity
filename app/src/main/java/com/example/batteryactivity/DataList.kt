package com.example.batteryactivity

import java.sql.Time
import java.util.*

data class DataList(
    val timestamp: Date,
    val health: Int,
    val status: Int,
    val voltage: Int,
    val temperature: Int,
    val level: Int,
    val scale: Int,
    val present: Boolean,
    val batteryLow: Boolean?,
    val plugged: Int,
    val propertyCapacity: Int,
    val propertyChargeCounter: Int,
    val propertyCurrentAverage: Int,
    val propertyEnergyCounter: Long,

    // Min. API Level 23
    val isCharging: Boolean?,

)
