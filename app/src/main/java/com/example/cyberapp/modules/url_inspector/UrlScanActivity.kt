package com.example.cyberapp.modules.url_inspector

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.cyberapp.R
import com.example.cyberapp.network.RetrofitClient
import com.example.cyberapp.network.UrlCheckRequest
import kotlinx.coroutines.launch

class UrlScanActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvUrl: TextView
    private lateinit var tvVerdict: TextView
    private lateinit var tvReasons: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnOpenAnyway: Button
    private lateinit var btnGoBack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_url_scan)

        tvTitle = findViewById(R.id.tvTitle)
        tvUrl = findViewById(R.id.tvUrl)
        tvVerdict = findViewById(R.id.tvVerdict)
        tvReasons = findViewById(R.id.tvReasons)
        progressBar = findViewById(R.id.progressBar)
        btnOpenAnyway = findViewById(R.id.btnOpenAnyway)
        btnGoBack = findViewById(R.id.btnGoBack)

        val url = intent.dataString

        if (url != null) {
            tvUrl.text = url
            checkUrl(url)
        } else {
            tvUrl.text = "No URL found"
            progressBar.visibility = View.GONE
        }

        btnGoBack.setOnClickListener {
            finish()
        }

        btnOpenAnyway.setOnClickListener {
            val intent = Intent(this, com.example.cyberapp.modules.safe_webview.SafeWebViewActivity::class.java)
            intent.putExtra("url", url)
            startActivity(intent)
            finish()
        }
    }

    private fun checkUrl(url: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.checkUrl(UrlCheckRequest(url))
                
                progressBar.visibility = View.GONE
                tvVerdict.text = "Verdict: ${response.verdict.uppercase()}"
                
                if (response.reasons.isNotEmpty()) {
                    tvReasons.text = response.reasons.joinToString("\n")
                } else {
                    tvReasons.text = "No threats detected."
                }

                if (response.verdict == "safe") {
                    tvVerdict.setTextColor(getColor(android.R.color.holo_green_dark))
                    btnOpenAnyway.visibility = View.VISIBLE
                    btnOpenAnyway.text = "Open Link"
                    btnOpenAnyway.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
                } else {
                    tvVerdict.setTextColor(getColor(android.R.color.holo_red_dark))
                    btnOpenAnyway.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                tvVerdict.text = "Error: ${e.message}"
                tvVerdict.setTextColor(getColor(android.R.color.holo_orange_dark))
            }
        }
    }
}
