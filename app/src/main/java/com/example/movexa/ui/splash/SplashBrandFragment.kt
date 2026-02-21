package com.example.movexa.ui.splash

import android.os.Bundle
import android.view.View
import com.example.movexa.R
import com.example.movexa.databinding.FragmentSplashBrandBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.utils.AnimationUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

/**
 * Second splash screen showing the full brand identity:
 * Logo + "Movexa" + tagline + subtitle.
 * Plays staggered fade-in animations and then navigates to Login.
 *
 * Flow: SplashLogoFragment → SplashBrandFragment → LoginFragment
 */
class SplashBrandFragment : BaseFragment<FragmentSplashBrandBinding>(
    FragmentSplashBrandBinding::inflate
) {

    companion object {
        private const val BRAND_ANIMATION_DURATION = 500L
        private const val STAGGER_DELAY = 150L
        private const val BRAND_DISPLAY_DURATION = 2500L
    }

    override fun initViews() {
        hideToolbar()
        hideBottomNav()

        // Start with views invisible for animation
        binding.brandContainer.alpha = 0f
        binding.tvSubtitle.alpha = 0f
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startBrandSequence()
    }

    private fun startBrandSequence() {
        // Animate brand container (logo + name + tagline)
        AnimationUtils.scaleUpFadeIn(
            view = binding.brandContainer,
            duration = BRAND_ANIMATION_DURATION
        )

        // Staggered fade-in for subtitle
        AnimationUtils.fadeIn(
            view = binding.tvSubtitle,
            duration = BRAND_ANIMATION_DURATION
        )

        // After display duration, navigate to login
        viewLifecycleOwner.lifecycleScope.launch {
            delay(BRAND_DISPLAY_DURATION)
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        navigateTo(R.id.action_splashBrand_to_login)
    }
}
