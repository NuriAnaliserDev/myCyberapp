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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        onboardingManager = OnboardingManager(this)

        // Initialize Views
        titleText = findViewById(R.id.onboarding_title)
        bodyText = findViewById(R.id.onboarding_body)
        lottieView = findViewById(R.id.onboarding_image)
        nextButton = findViewById(R.id.btn_next)
        nextButton = findViewById(R.id.btn_next)
        skipButton = findViewById(R.id.btn_skip)
        
        findViewById<TextView>(R.id.app_version).text = "v${BuildConfig.VERSION_NAME}"
        
        dot1 = findViewById(R.id.dot_1)
        dot2 = findViewById(R.id.dot_2)
        dot3 = findViewById(R.id.dot_3)
        dot4 = findViewById(R.id.dot_4)

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
                lottieView.setAnimation(R.raw.anim_scan) // Shield animation
                lottieView.playAnimation()
                nextButton.text = getString(R.string.onboarding_next)
                
                updateDots(1)
            }
            2 -> {
                titleText.text = getString(R.string.onboarding_title_2)
                bodyText.text = getString(R.string.onboarding_body_2)
                lottieView.setAnimation(R.raw.anim_scan) 
                lottieView.playAnimation()
                nextButton.text = getString(R.string.onboarding_next)
                
                updateDots(2)
            }
            3 -> {
                titleText.text = getString(R.string.onboarding_title_3)
                bodyText.text = getString(R.string.onboarding_body_3)
                lottieView.setAnimation(R.raw.anim_scan)
                lottieView.playAnimation()
                nextButton.text = getString(R.string.onboarding_next)
                
                updateDots(3)
            }
            4 -> {
                titleText.text = getString(R.string.onboarding_title_4)
                bodyText.text = getString(R.string.onboarding_body_4)
                lottieView.setAnimation(R.raw.anim_scan)
                lottieView.playAnimation()
                nextButton.text = getString(R.string.onboarding_start)
                
                updateDots(4)
            }
        }
    }
    
    private fun updateDots(activeStep: Int) {
        dot1.setImageResource(if (activeStep == 1) R.drawable.ic_dot_filled else R.drawable.ic_dot_empty)
        dot2.setImageResource(if (activeStep == 2) R.drawable.ic_dot_filled else R.drawable.ic_dot_empty)
        dot3.setImageResource(if (activeStep == 3) R.drawable.ic_dot_filled else R.drawable.ic_dot_empty)
        dot4.setImageResource(if (activeStep == 4) R.drawable.ic_dot_filled else R.drawable.ic_dot_empty)
    }

    private fun finishOnboarding() {
        onboardingManager.setFirstLaunchCompleted()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
