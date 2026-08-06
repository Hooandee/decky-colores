package com.hooandee.colores.audio

interface PlaybackCapture : AutoCloseable {
    fun start()

    suspend fun read(buffer: ShortArray): Int

    override fun close()
}
