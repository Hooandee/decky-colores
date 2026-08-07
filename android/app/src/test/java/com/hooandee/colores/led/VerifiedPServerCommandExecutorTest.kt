package com.hooandee.colores.led

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifiedPServerCommandExecutorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `accepted binder command fails when the shell exits nonzero`() {
        val status = temporaryFolder.newFile("status")
        val delegate = RecordingExecutor { status.writeText("1") }
        val executor = VerifiedPServerCommandExecutor(delegate, status, attempts = 1, pollIntervalMs = 0)

        assertFalse(executor.execute("i2cset command"))
    }

    @Test
    fun `accepted binder command succeeds when the shell exits zero`() {
        val status = temporaryFolder.newFile("status")
        val script = File(status.parentFile, "${status.name}.sh")
        val delegate = RecordingExecutor { status.writeText("0") }
        val executor = VerifiedPServerCommandExecutor(delegate, status, attempts = 1, pollIntervalMs = 0)

        assertTrue(executor.execute("i2cset command"))
        assertTrue(script.readText().contains("i2cset command"))
        assertTrue(delegate.command.orEmpty().contains(script.absolutePath))
        assertTrue(script.readText().contains(status.absolutePath))
        assertTrue(script.readText().contains("${status.absolutePath}.err"))
    }

    @Test
    fun `missing shell result rejects an accepted binder command`() {
        val status = temporaryFolder.newFile("status")
        val executor = VerifiedPServerCommandExecutor(RecordingExecutor(), status, attempts = 1, pollIntervalMs = 0)

        assertFalse(executor.execute("i2cset command"))
    }

    @Test
    fun `long command is executed from a prepared script instead of the binder payload`() {
        val status = temporaryFolder.newFile("status")
        val script = File(status.parentFile, "${status.name}.sh")
        val delegate = RecordingExecutor { status.writeText("0") }
        val executor = VerifiedPServerCommandExecutor(delegate, status, attempts = 1, pollIntervalMs = 0)
        val command = List(20) { "i2cset command $it" }.joinToString(" && ")

        assertTrue(executor.execute(command))
        assertFalse(delegate.command.orEmpty().contains(command))
        assertTrue(script.exists())
        assertTrue(script.readText().contains(command))
    }
}

private class RecordingExecutor(
    private val onExecute: () -> Unit = {},
) : PServerCommandExecutor {
    override val available = true
    var command: String? = null

    override fun execute(command: String): Boolean {
        this.command = command
        onExecute()
        return true
    }
}
