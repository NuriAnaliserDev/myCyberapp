package com.example.cyberapp

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.airbnb.lottie.LottieAnimationView

class OnboardingActivity : AppCompatActivity() {

    private lateinit var onboardingManager: OnboardingManager
    private var currentStep = 1

    private lateinit var titleText: TextView
    private lateinit var bodyText: TextView
    private lateinit var lottieView: LottieAnimationView
    private lateinit var nextButton: AppCompatButton
    private lateinit var skipButton: TextView
    
    private lateinit var dot1: ImageView
    private lateinit var dot2: ImageView
    private lateinit var dot3: ImageView
    private lateinit var dot4: ImageView

    // Permission Launchers
    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if any critical permission is granted
        if (permissions[android.Manifest.permission.POST_NOTIFICATIONS] == true || 
            permissions[android.Manifest.permission.READ_PHONE_STATE] == true) {
             android.widget.Toast.makeText(this, "Ruxsatlar qabul qilindi", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        onboardingManager = OnboardingManager(this)

        // Initialize Views
        titleText = findViewById(R.id.onboarding_title)
        bodyText = findViewById(R.id.onboarding_body)
        lottieView = findViewById(R.id.onboarding_image)
        nextButton = findViewById(R.id.btn_next)
        skipButton = findViewById(R.id.btn_skip)
        
        findViewById<TextView>(R.id.app_version).text = "v${BuildConfig.VERSION_NAME}"
        
        dot1 = findViewById(R.id.dot_1)
        dot2 = findViewById(R.id.dot_2)
        dot3 = findViewById(R.id.dot_3)
        // Note: Layout might need update to include dot_4, for now we reuse dot3 or just ignore visual dot 4 if layout is fixed
        // Assuming layout has 3 dots, we will just keep it as is for step 4 or try to find dot_4 if exists.
        // If layout is fixed to 3 dots, we might need to dynamically add one or just accept 3 dots for 4 steps.
        // Let's check if we can dynamically handle dots or just stick to 3 visual dots for 4 steps (last step shares dot 3 or no dot update)
        
        // Initial State
        updateUI(1)

        nextButton.setOnClickListener {
            if (currentStep < 4) {
                currentStep++
                updateUI(currentStep)
            } else {
                finishOnboarding()
            }
        }

        skipButton.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun updateUI(step: Int) {
        when (step) {
            1 -> {
                titleText.text = getString(R.string.onboarding_title_1)
                bodyText.text = getString(R.string.onboarding_body_1)
                lottieView.setAnimation(R.raw.anim_shield) // Shield
                lottieView.playAnimation()
                nextButton.text = getString(R.string.onboarding_next)
                nextButton.setOnClickListener { 
                    currentStep = 2
                    updateUI(2)
                }
                updateDots(1)
            }
            2 -> {
                titleText.text = getString(R.string.onboarding_title_2)
                bodyText.text = getString(R.string.onboarding_body_2)
                lottieView.setAnimation(R.raw.anim_scan) // Trojan/Scan
                lottieView.playAnimation()
                nextButton.text = getString(R.string.onboarding_next)
                nextButton.setOnClickListener { 
                    currentStep = 3
                    updateUI(3)
                }
                updateDots(2)
            }
            3 -> {
                titleText.text = getString(R.string.onboarding_title_3)
                bodyText.text = getString(R.string.onboarding_body_3)
                lottieView.setAnimation(R.raw.anim_pulse) // Session/Pulse
                lottieView.playAnimation()
                nextButton.text = "Ruxsatlarni Sozlash" // Custom text
                
                nextButton.setOnClickListener {
                    currentStep = 4
                    updateUI(4)
                }
                updateDots(3)
            }
            4 -> {
                titleText.text = "Ruxsatnomalar"
                bodyText.text = "To'liq himoya uchun bizga ba'zi ruxsatlar kerak:\n• Bildirishnomalar (Xavf haqida ogohlantirish)\n• Qo'ng'iroqlar (Firibgarlarni aniqlash)\n• Ilovalar statistikasi (Xavfli ilovalarni aniqlash)"
                lottieView.setAnimation(R.raw.anim_shield) // Reuse shield or lock
                lottieView.playAnimation()
                nextButton.text = getString(R.string.onboarding_start)
                
                // Request Permissions Logic
                val permissionsToRequest = mutableListOf<String>()
                permissionsToRequest.add(android.Manifest.permission.READ_PHONE_STATE)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                
                requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
                
                // Check Usage Stats
                if (!hasUsageStatsPermission()) {
                     android.widget.Toast.makeText(this, "Iltimos, Usage Access ruxsatini bering", android.widget.Toast.LENGTH_LONG).show()
                     startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                


                nextButton.setOnClickListener {
                    finishOnboarding()
                }
                updateDots(3) // Keep at 3
            }
        }
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
    
    private fun updateDots(activeStep: Int) {
        dot1.setImageResource(if (activeStep == 1) R.drawable.ic_dot_filled else R.drawable.ic_dot_empty)
        dot2.setImageResource(if (activeStep == 2) R.drawable.ic_dot_filled else R.drawable.ic_dot_empty)
        dot3.setImageResource(if (activeStep == 3) R.drawable.ic_dot_filled else R.drawable.ic_dot_empty)
    }

    private fun finishOnboarding() {
        onboardingManager.setFirstLaunchCompleted()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
