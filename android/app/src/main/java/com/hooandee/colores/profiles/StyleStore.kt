package com.hooandee.colores.profiles

import android.content.Context
import androidx.core.content.edit

class StyleStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
) {
    constructor(context: Context) : this(
        read = { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getString(KEY, null) },
        write = { value -> context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit { putString(KEY, value) } },
    )

    fun load(): List<SavedStyle> = read()?.let { runCatching { SavedStyles.importDocument(it).first }.getOrNull() } ?: emptyList()

    fun save(styles: List<SavedStyle>) = write(SavedStyles.document(styles))

    private companion object {
        const val FILE_NAME = "styles"
        const val KEY = "document"
    }
}
