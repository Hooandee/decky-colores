package com.hooandee.colores.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidPlaybackCapture
    constructor(
        context: Context,
        projection: MediaProjection,
        private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : PlaybackCapture {
        private val audioFormat =
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
        private val record = createRecord(context, projection)

        override fun start() {
            check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord is not initialized" }
            record.startRecording()
        }

        override suspend fun read(buffer: ShortArray): Int =
            withContext(dispatcher) {
                record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            }

        override fun close() {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) runCatching { record.stop() }
            record.release()
        }

        private fun bufferSizeBytes(): Int =
            maxOf(
                AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
                CHUNK_SAMPLES * Short.SIZE_BYTES,
            )

        private fun createRecord(
            context: Context,
            projection: MediaProjection,
        ): AudioRecord {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("RECORD_AUDIO permission is required for playback capture")
            }
            return AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSizeBytes())
                .setAudioPlaybackCaptureConfig(
                    AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build(),
                )
                .build()
        }

        private companion object {
            const val SAMPLE_RATE = 16_000
            const val CHUNK_SAMPLES = 1_024
        }
    }
