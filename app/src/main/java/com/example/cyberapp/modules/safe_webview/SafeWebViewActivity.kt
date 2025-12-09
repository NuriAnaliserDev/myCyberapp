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
        
        // Additional security settings
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        
        // Mixed content protection (Android 5.0+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        
        // Disable geolocation
        settings.setGeolocationEnabled(false)
        
        // Disable autofill
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            webView.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO)
        }

        // Toggle JS Logic
        toggleJs.setOnCheckedChangeListener { _, isChecked ->
            settings.javaScriptEnabled = isChecked
            if (isChecked) {
                Toast.makeText(this, getString(R.string.javascript_enabled_warning), Toast.LENGTH_SHORT).show()
            }
            webView.reload()
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val newUrl = request?.url.toString()
                
                // Validate URL scheme - only allow http/https
                val uri = android.net.Uri.parse(newUrl)
                val scheme = uri.scheme?.lowercase()
                if (scheme !in listOf("http", "https")) {
                    Toast.makeText(this@SafeWebViewActivity, getString(R.string.unsafe_url_scheme, scheme ?: "unknown"), Toast.LENGTH_LONG).show()
                    return true // Block loading
                }
                
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
            .setTitle(getString(R.string.dangerous_site_title))
            .setMessage(getString(R.string.dangerous_site_message, url, message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.exit_site)) { _, _ ->
                finish()
            }
            .setNegativeButton(getString(R.string.enter_anyway_dangerous)) { dialog, _ ->
                dialog.dismiss()
                // Add to whitelist so we don't loop
                allowedUrls.add(url)
                webView.loadUrl(url)
            }
            .show()
    }
    
    override fun onDestroy() {
        // Clean up WebView to prevent memory leaks
        webView.destroy()
        super.onDestroy()
    }
}
