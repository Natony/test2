package com.example.test2

import java.util.UUID

data class ConfigItem(
    val id: String = UUID.randomUUID().toString(),
    var ipAddress: String,
    var port: Int = 502
)
