package com.dopdop.bopdop.ui.share

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** Returns a launcher for the camera QR scanner; [onResult] gets the decoded text. */
@Composable
fun rememberQrScanner(onResult: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let(onResult) }
    return {
        launcher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Point at a BopDop code")
                .setBeepEnabled(false)
                .setOrientationLocked(false),
        )
    }
}
