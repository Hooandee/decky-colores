package com.hooandee.colores.settings

enum class AppLanguage(
    val languageTag: String,
) {
    SYSTEM(""),
    SPANISH("es"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromLanguageTag(tag: String?): AppLanguage =
            when (tag?.substringBefore('-')?.lowercase()) {
                "es" -> SPANISH
                "en" -> ENGLISH
                else -> SYSTEM
            }
    }
}
