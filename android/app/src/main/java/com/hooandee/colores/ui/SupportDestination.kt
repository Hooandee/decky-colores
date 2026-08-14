package com.hooandee.colores.ui

internal enum class SupportPlatform {
    KOFI,
    PAYPAL,
    PATREON,
}

internal fun supportUrl(
    platform: SupportPlatform,
    creator: String,
): String {
    val handle = creator.trim().removePrefix("@").trim()
    require(handle.isNotEmpty())
    return when (platform) {
        SupportPlatform.KOFI -> "https://ko-fi.com/$handle"
        SupportPlatform.PAYPAL -> "https://paypal.me/$handle"
        SupportPlatform.PATREON -> "https://www.patreon.com/$handle"
    }
}
