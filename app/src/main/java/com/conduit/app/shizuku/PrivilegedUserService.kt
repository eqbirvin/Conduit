package com.conduit.app.shizuku

import android.util.Log
import java.io.IOException
import kotlin.system.exitProcess

/**
 * Shizuku user service. Shizuku instantiates this class by name in its own process,
 * so it runs with shell (adb) or root identity — never in Conduit's app process.
 */
class PrivilegedUserService : IPrivilegedService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: Array<String>): Int {
        return try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.e(TAG, "Privileged command exited with $exitCode: ${output.trim()}")
            }
            exitCode
        } catch (e: IOException) {
            Log.e(TAG, "Privileged command failed to start", e)
            -1
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.e(TAG, "Privileged command interrupted", e)
            -1
        }
    }

    private companion object {
        const val TAG = "PrivilegedUserService"
    }
}
