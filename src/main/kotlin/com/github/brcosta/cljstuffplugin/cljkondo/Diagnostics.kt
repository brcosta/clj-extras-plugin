package com.github.brcosta.cljstuffplugin.cljkondo

import com.fasterxml.jackson.annotation.JsonProperty

data class Diagnostics(
    @field:JsonProperty("findings") val findings: List<Finding>,
    @field:JsonProperty("summary") val summary: Summary?
)

data class Finding(
    @field:JsonProperty("row")
    val row: Int,
    @field:JsonProperty("end-row")
    val endRow: Int,
    @field:JsonProperty("col")
    val col: Int,
    @field:JsonProperty("end-col")
    val endCol: Int,
    @field:JsonProperty("level")
    val level: String,
    @field:JsonProperty("filename")
    val filename: String?,
    @field:JsonProperty("class")
    val clazz: String?,
    @field:JsonProperty("message")
    val message: String,
    @field:JsonProperty("type")
    val type: String,
)

data class Summary(
    @field:JsonProperty("files")
    val files: Int?,
    @field:JsonProperty("type")
    val type: String?,
    @field:JsonProperty("error")
    val error: Int?,
    @field:JsonProperty("warning")
    val warning: Int?,
    @field:JsonProperty("info")
    val info: Int?,
    @field:JsonProperty("duration")
    val duration: Int?
)
