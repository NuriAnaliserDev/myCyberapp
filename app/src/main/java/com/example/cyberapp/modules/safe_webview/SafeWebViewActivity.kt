package com.example.cyberapp.modules.safe_webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ToggleButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.example.cyberapp.R

class SafeWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvUrlBar: TextView
    private lateinit var toggleJs: ToggleButton
    private val allowedUrls = mutableSetOf<String>()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_safe_webview)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        tvUrlBar = findViewById(R.id.tvUrlBar)
        toggleJs = findViewById(R.id.toggleJs)

        val url = intent.getStringExtra("url") ?: "about:blank"
        tvUrlBar.text = url

        // Security Settings
        val settings = webView.settings
        settings.javaScriptEnabled = false // Default OFF
        settings.domStorageEnabled = false // No LocalStorage
        settings.databaseEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false

        // Toggle JS Logic
        toggleJs.setOnCheckedChangeListener { _, isChecked ->
            settings.javaScriptEnabled = isChecked
            if (isChecked) {
                Toast.makeText(this, "⚠️ JavaScript Enabled! Be careful.", Toast.LENGTH_SHORT).show()
            }
            webView.reload()
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val newUrl = request?.url.toString()
                
                // Check if allowed by user previously
                if (allowedUrls.contains(newUrl)) return false

                // Check for phishing before loading
                val analysis = com.example.cyberapp.PhishingDetector.analyzeUrl(newUrl)
                if (analysis.isSuspicious) {
                    showPhishingAlert(newUrl, analysis)
                    return true // Block loading
                }

                tvUrlBar.text = newUrl
                return false // Allow WebView to load
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                
                if (url != null) {
                    // Check if allowed by user previously
                    if (allowedUrls.contains(url)) {
                         progressBar.visibility = View.VISIBLE
                         tvUrlBar.text = url
                         return
                    }

                    val analysis = com.example.cyberapp.PhishingDetector.analyzeUrl(url)
                    if (analysis.isSuspicious) {
                        view?.stopLoading()
                        showPhishingAlert(url, analysis)
                        return
                    }
                }

                progressBar.visibility = View.VISIBLE
                tvUrlBar.text = url
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                }
            }
        }

        webView.loadUrl(url)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }



    private fun showPhishingAlert(url: String, result: com.example.cyberapp.PhishingDetector.UrlAnalysisResult) {
        val message = result.warnings.joinToString("\n") { "• $it" }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🚨 XAVFLI SAYT!")
            .setMessage("Ushbu sayt ($url) xavfli deb topildi!\n\nSabablar:\n$message\n\nDavom etish tavsiya etilmaydi.")
            .setCancelable(false)
            .setPositiveButton("Chiqish") { _, _ ->
                finish()
            }
            .setNegativeButton("Baribir kirish (Xavfli)") { dialog, _ ->
                dialog.dismiss()
                // Add to whitelist so we don't loop
                allowedUrls.add(url)
                webView.loadUrl(url)
            }
            .show()
    }
}
