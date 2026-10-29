package com.dopdop.bopdop.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.dopdop.bopdop.data.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val fraction: Float) : InstallState
    /** Handed to Android's installer; waiting for it to answer. */
    data object Installing : InstallState
    /** Downloaded and ready: one tap on Install opens Android's "Update BopDop?" screen. */
    data object Ready : InstallState
    /** Waiting for the user to allow "Install unknown apps" for BopDop. */
    data object NeedsPermission : InstallState
    data class Failed(val message: String) : InstallState
}

/**
 * One-tap update: downloads the release APK for this phone's processor and hands it to Android's
 * installer, which shows its own "Update BopDop?" confirmation. Needs "Install unknown apps" for
 * BopDop the first time; the app opens that switch when it's off.
 *
 * Android only lets an app open that confirmation while the app is on screen, so it's never left
 * to chance: the confirmation is kept, opened straight away when BopDop is in front, and otherwise
 * on the next return to the app, from a notification, or from the Install button. The work runs
 * in the installer's own scope, so leaving the Updates page doesn't cancel a download.
 */
object UpdateInstaller {
    private const val TAG = "UpdateInstaller"
    private const val ACTION = "com.dopdop.bopdop.UPDATE_STATUS"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION = 7301
    /** How long Android gets to answer the install session before the classic installer takes over. */
    private const val ANSWER_MS = 15_000L

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    /** BopDop has a screen showing (set by the app's root from its lifecycle). */
    @Volatile var visible = false

    /** What the Install button opens: Android's confirmation for the session, or the classic installer. */
    @Volatile private var confirm: Intent? = null
    @Volatile private var apk: File? = null
    /** The confirmation was opened while BopDop was showing, so it's on screen now. */
    @Volatile private var shown = false

    /** The update waiting for the "Install unknown apps" switch; [resume] picks it up on return. */
    @Volatile private var waiting: AppUpdate? = null

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

    /** Start (or carry on with) updating to [update]: download, then install. Taps while busy are ignored. */
    fun start(context: Context, update: AppUpdate) {
        val app = context.applicationContext
        when (_state.value) {
            InstallState.Ready -> { confirm(app); return }
            is InstallState.Downloading, InstallState.Installing -> if (job?.isActive == true) return
            else -> {}
        }
        job = scope.launch { install(app, update) }
    }

    private suspend fun install(context: Context, update: AppUpdate) {
        val url = update.apkUrl ?: run { _state.value = InstallState.Failed("This release has no APK"); return }
        if (!ensureAllowed(context)) { waiting = update; _state.value = InstallState.NeedsPermission; return }
        waiting = null
        confirm = null; shown = false
        val file = runCatching { download(context, url) }.getOrElse {
            Log.w(TAG, "download failed", it)
            _state.value = InstallState.Failed("Download failed: ${it.message ?: "no connection"}"); return
        }
        installFile(context, file)
    }

    /** Hand a downloaded [file] to Android's installer, and make sure its confirmation reaches the screen. */
    private suspend fun installFile(context: Context, file: File) {
        apk = file
        confirm = null; shown = false
        _state.value = InstallState.Installing
        runCatching { commit(context, file) }.onFailure {
            Log.w(TAG, "install session failed, using the classic installer", it)
            offer(context, classic(context, file)); return
        }
        // Android answers through [StatusReceiver]. Some phones' installers never answer a session:
        // then the classic installer, opened on the file, does the job.
        delay(ANSWER_MS)
        if (_state.value == InstallState.Installing) {
            Log.w(TAG, "no answer from the install session in ${ANSWER_MS / 1000}s, offering the classic installer")
            offer(context, classic(context, file))
        }
    }

    /** The system installer, opened on the downloaded APK (the classic way, before sessions). */
    private fun classic(context: Context, file: File): Intent {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Keep [intent] as what Install opens, and open it now if BopDop is on screen. Otherwise a
     * notification offers it and the next return to the app opens it.
     */
    private fun offer(context: Context, intent: Intent) {
        confirm = intent
        shown = false
        _state.value = InstallState.Ready
        if (visible) open(context) else notifyReady(context, intent)
    }

    /** Install, tapped: open what Android needs to finish the update. */
    fun confirm(context: Context) {
        if (confirm == null) apk?.takeIf { it.exists() }?.let { confirm = classic(context, it) }
        if (confirm == null) { _state.value = InstallState.Idle; return }
        open(context)
    }

    private fun open(context: Context) {
        val i = confirm ?: return
        runCatching { context.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onSuccess { shown = true; context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION) }
            .onFailure { Log.w(TAG, "couldn't open the installer", it); _state.value = InstallState.Failed("Couldn't open the installer. Download the update from GitHub instead.") }
    }

    /** Back in the app: finish an update waiting on the unknown-apps switch, or one Android couldn't show. */
    suspend fun resume(context: Context) {
        visible = true
        waiting?.let { u ->
            if (context.packageManager.canRequestPackageInstalls()) start(context, u)
            else { waiting = null; _state.value = InstallState.Idle }
            return
        }
        // The confirmation came while BopDop was away (Android won't open it then): open it now.
        if (_state.value == InstallState.Ready && !shown) open(context)
    }

    private fun notifyReady(context: Context, intent: Intent) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled()) return
            if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Updates", NotificationManager.IMPORTANCE_HIGH))
            val tap = PendingIntent.getActivity(context, NOTIFICATION, Intent(intent), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            nm.notify(NOTIFICATION, androidx.core.app.NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(com.dopdop.bopdop.R.drawable.ic_launcher_mono)
                .setContentTitle("BopDop update downloaded")
                .setContentText("Tap to install")
                .setContentIntent(tap).setAutoCancel(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .build())
        }.onFailure { Log.w(TAG, "couldn't post the update notification", it) }
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
        // Leftover sessions from an earlier try would sit in the way; drop them.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out -> input.copyTo(out); session.fsync(out) }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            // Addressed to the receiver itself: it has no intent filter, so a broadcast by action alone never reaches it.
            val status = PendingIntent.getBroadcast(context, id, Intent(context, StatusReceiver::class.java).setAction(ACTION), flags)
            session.commit(status.intentSender)
        }
    }

    /** Android's answers about the install session. */
    class StatusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            Log.i(TAG, "install session status $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()}")
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val ask = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    if (ask != null) offer(context.applicationContext, ask)
                    else apk?.let { offer(context.applicationContext, classic(context.applicationContext, it)) }
                }
                PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> { confirm = null; _state.value = InstallState.Idle }
                PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                    _state.value = InstallState.Failed("This build is signed differently. Uninstall BopDop, then install the new one.")
                else -> _state.value = InstallState.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed")
            }
        }
    }
}
