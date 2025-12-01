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
                tvUrlBar.text = newUrl
                return false // Allow WebView to load
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
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


}
