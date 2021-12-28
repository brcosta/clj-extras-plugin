package com.github.brcosta.cljstuffplugin.util.kondo

import com.fasterxml.jackson.annotation.JsonProperty

data class Diagnostics(val findings: List<Finding>, val summary: Summary?)

data class Finding(
    val row: Int,
    @JsonProperty("end-row")
    val endRow: Int,
    val col: Int,
    @JsonProperty("end-col")
    val endCol: Int,
    val level: String,
    val filename: String?,
    @JsonProperty("class")
    val clazz: String?,
    val message: String,
    val type: String,
)

data class Summary(
    var files: Int?,
    val type: String?,
    val error: Int?,
    val warning: Int?,
    val info: Int?,
    val duration: Int?
)
