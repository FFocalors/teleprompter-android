package com.zhy20.teleprompter.feature.remote.scanner

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.zhy20.teleprompter.R

/** QR-only capture surface styled for the two-phone pairing flow. */
class PairingCaptureActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        findViewById<View>(R.id.pairing_scanner_back).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        findViewById<TextView>(R.id.pairing_scanner_title).text =
            intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.scan_qr_code)
    }

    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_pairing_capture)
        applySystemBarInsets()
        return findViewById(R.id.zxing_barcode_scanner)
    }

    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.pairing_scanner_root)
        val topContent = findViewById<View>(R.id.pairing_scanner_top_content)
        val bottomContent = findViewById<View>(R.id.pairing_scanner_bottom_content)
        val topBase = topContent.paddingTop
        val bottomBase = bottomContent.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topContent.updatePadding(top = topBase + bars.top)
            bottomContent.updatePadding(bottom = bottomBase + bars.bottom)
            insets
        }
    }

    companion object {
        const val EXTRA_TITLE = "pairing_scanner_title"
    }
}
