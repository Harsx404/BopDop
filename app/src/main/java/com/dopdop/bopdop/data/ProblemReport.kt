package com.dopdop.bopdop.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.dopdop.bopdop.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

/**
 * "Report a problem" (F56): BopDop's own recent log, the last crash, and the phone and app
 * versions in one text file, handed to the share sheet so it can go to whoever helps. Only this
 * app's log is read (Android allows no more), and sign-ins are never written to it.
 */
object ProblemReport {
    private fun crashFile(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    /** Keep the stack trace of a crash for the next report, then let Android handle the crash as usual. */
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { crashFile(app).writeText("${LocalDateTime.now()} on ${thread.name}\n${e.stackTraceToString()}") }
            previous?.uncaughtException(thread, e)
        }
    }

    suspend fun build(ctx: Context, note: String): File = withContext(Dispatchers.IO) {
        val s = LibraryStore.settings
        val log = runCatching {
            val p = ProcessBuilder("logcat", "-d", "-v", "time", "--pid=${android.os.Process.myPid()}", "-t", "3000").redirectErrorStream(true).start()
            p.inputStream.bufferedReader().readText()
        }.getOrElse { "(log unavailable: ${it.message})" }
        val text = buildString {
            appendLine("BopDop problem report · ${LocalDateTime.now()}")
            appendLine()
            if (note.isNotBlank()) { appendLine("What happened:"); appendLine(note.trim()); appendLine() }
            appendLine("App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Phone ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.SUPPORTED_ABIS.firstOrNull()}")
            appendLine("Settings: quality ${s.audioWifi}/${s.audioMobile} · sources ${s.sources().filter(s::sourceOn).joinToString(">") { if (it.startsWith("http")) "addon" else it }} · add-ons ${s.addons.size} · mascot ${s.mascot} · reduce motion ${s.reduceMotion} · spotify ${com.dopdop.bopdop.data.spotify.SpotifyAccount.signedIn}")
            crashFile(ctx).takeIf { it.exists() }?.let { appendLine(); appendLine("Last crash:"); appendLine(it.readText()) }
            appendLine(); appendLine("Log:"); append(log)
        }
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        File(dir, "BopDop-report.txt").apply { writeText(text) }
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "BopDop problem report")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Send report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
