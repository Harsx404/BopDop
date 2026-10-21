package com.dopdop.bopdop.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import com.dopdop.bopdop.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val fraction: Float) : InstallState
    data object Installing : InstallState
    /** Waiting for the user to allow "Install unknown apps" for BopDop. */
    data object NeedsPermission : InstallState
    data class Failed(val message: String) : InstallState
}

/**
 * One-tap update: downloads the release APK for this phone's processor and hands it to Android's
 * installer, which shows its own "Update BopDop?" confirmation. Needs "Install unknown apps" for
 * BopDop the first time; the app opens that switch when it's off.
 */
object UpdateInstaller {
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()
    private const val ACTION = "com.dopdop.bopdop.UPDATE_STATUS"

    /** True when Android lets BopDop install packages; otherwise opens that switch and returns false. */
    fun ensureAllowed(context: Context): Boolean {
        if (context.packageManager.canRequestPackageInstalls()) return true
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        return false
    }

    /** The update waiting for the "Install unknown apps" switch; [resume] picks it up on return. */
    @Volatile private var waiting: AppUpdate? = null

    suspend fun install(context: Context, update: AppUpdate) {
        val url = update.apkUrl ?: run { _state.value = InstallState.Failed("This release has no APK"); return }
        if (!ensureAllowed(context)) { waiting = update; _state.value = InstallState.NeedsPermission; return }
        waiting = null
        val apk = runCatching { download(context, url) }.getOrElse {
            _state.value = InstallState.Failed("Download failed: ${it.message ?: "no connection"}"); return
        }
        _state.value = InstallState.Installing
        runCatching { commit(context, apk) }.onFailure { _state.value = InstallState.Failed("Couldn't start the installer") }
    }

    /** Back in the app after the permission screen: carry on if it was allowed. */
    suspend fun resume(context: Context) {
        val u = waiting ?: return
        if (context.packageManager.canRequestPackageInstalls()) install(context, u)
        else { waiting = null; _state.value = InstallState.Idle }
    }

    private suspend fun download(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "BopDop.apk")
        _state.value = InstallState.Downloading(0f)
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val body = r.body ?: error("empty")
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) _state.value = InstallState.Downloading((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        out
    }

    private suspend fun commit(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out -> input.copyTo(out); session.fsync(out) }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val status = PendingIntent.getBroadcast(context, id, Intent(ACTION).setPackage(context.packageName), flags)
            session.commit(status.intentSender)
        }
    }

    /** Android's answers about the install session. */
    class StatusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
                }
                PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = InstallState.Idle
                PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                    _state.value = InstallState.Failed("This build is signed differently. Uninstall BopDop, then install the new one.")
                else -> _state.value = InstallState.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed")
            }
        }
    }
}
