package com.example.movexa.ui.splash

import android.os.Bundle
import android.view.View
import com.example.movexa.R
import com.example.movexa.databinding.FragmentSplashLogoBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.utils.AnimationUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

/**
 * First splash screen showing the Movexa logo with a scale+fade animation.
 * After a brief delay, navigates to the brand splash screen.
 *
 * Flow: SplashLogoFragment → SplashBrandFragment → LoginFragment
 */
class SplashLogoFragment : BaseFragment<FragmentSplashLogoBinding>(
    FragmentSplashLogoBinding::inflate
) {

    companion object {
        private const val LOGO_ANIMATION_DURATION = 600L
        private const val SPLASH_DISPLAY_DURATION = 1800L
    }

    override fun initViews() {
        hideToolbar()
        hideBottomNav()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startSplashSequence()
    }

    private fun startSplashSequence() {
        // Animate logo: scale up + fade in
        AnimationUtils.scaleUpFadeIn(
            view = binding.ivLogo,
            duration = LOGO_ANIMATION_DURATION
        )

        // After display duration, navigate to brand splash
        viewLifecycleOwner.lifecycleScope.launch {
            delay(SPLASH_DISPLAY_DURATION)
            navigateToSplashBrand()
        }
    }

    private fun navigateToSplashBrand() {
        navigateTo(R.id.action_splashLogo_to_splashBrand)
    }
}
