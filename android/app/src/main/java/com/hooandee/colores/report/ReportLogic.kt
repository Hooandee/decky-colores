package com.hooandee.colores.report

val REPORT_CATEGORIES = listOf("color", "brightness", "effects", "sensors", "audio", "ambilight", "profiles", "learning", "other")

fun canSubmitReport(text: String): Boolean = text.isNotBlank()
