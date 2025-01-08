package com.github.brcosta.cljstuffplugin.cljkondo

import com.fasterxml.jackson.annotation.JsonProperty

data class Diagnostics(
    @JsonProperty("findings")
    val findings: List<Finding>,

    @JsonProperty("summary")
    val summary: Summary?
)

data class Finding(
    @JsonProperty("row")
    val row: Int,
    @JsonProperty("end-row")
    val endRow: Int,
    @JsonProperty("col")
    val col: Int,
    @JsonProperty("end-col")
    val endCol: Int,
    @JsonProperty("level")
    val level: String,
    @JsonProperty("filename")
    val filename: String?,
    @JsonProperty("class")
    val clazz: String?,
    @JsonProperty("message")
    val message: String,
    @JsonProperty("type")
    val type: String,
)

data class Summary(
    @JsonProperty("files")
    var files: Int?,
    @JsonProperty("type")
    val type: String?,
    @JsonProperty("error")
    val error: Int?,
    @JsonProperty("warning")
    val warning: Int?,
    @JsonProperty("info")
    val info: Int?,
    @JsonProperty("duration")
    val duration: Int?
)
