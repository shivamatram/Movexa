package com.example.movexa.ui.public_tracking

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movexa.R
import com.example.movexa.data.model.PublicTrackingState
import com.example.movexa.data.model.PublicTripInfo
import com.example.movexa.databinding.FragmentEnterTrackingBinding
import com.example.movexa.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  ENTER TRACKING FRAGMENT
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * The first screen in the Public Customer Tracking System.
 * Allows unauthenticated customers to enter a tracking ID and search for their delivery.
 *
 * ┌──────────────────────────────────────────────────┐
 * │               Track Your Delivery                │
 * │  Enter your tracking ID to see live updates      │
 * │                                                  │
 * │  ┌────────────────────────────────────────────┐  │
 * │  │  Enter Tracking ID         TRK-2026-ABCD  │  │
 * │  └────────────────────────────────────────────┘  │
 * │  [ ■■■■■■   Track Delivery   ■■■■■■ ]           │
 * │                                                  │
 * │  Recent Searches                    Clear All    │
 * │  ┌─ TRK-XXX1  In Transit  2h ago  ─ ✕ ─────┐  │
 * │  ┌─ TRK-XXX2  Delivered   1d ago  ─ ✕ ─────┐  │
 * └──────────────────────────────────────────────────┘
 *
 * ─── Features ─────────────────────────────────────────────────────
 *
 *  ● Input validation with real-time feedback
 *  ● IME action (keyboard search key) support
 *  ● Error state display (invalid/expired/network)
 *  ● Loading state with progress indicator
 *  ● Recent searches stored in SharedPreferences
 *  ● Smooth entrance animations
 *  ● Auto-navigation to LiveTracking on success
 *  ● Character counter and formatting
 *  ● Input debouncing to prevent rapid-fire searches
 *  ● Keyboard hide on search trigger
 *  ● Error card with icon, title, message, and retry button
 *  ● Clear all recent searches with confirmation
 *  ● Swipe-to-remove recent search (via remove button)
 *  ● Empty recent searches state handling
 *
 * ─── Architecture ─────────────────────────────────────────────────
 *
 *  ● Uses shared [PublicTrackingViewModel] via activityViewModels()
 *  ● Does NOT extend MainActivity's loading/toolbar helpers
 *    (this is a standalone public-facing module)
 *  ● Navigation via [nav_public_tracking.xml] graph
 *
 * ─── Security ─────────────────────────────────────────────────────
 *
 *  ● No authentication required
 *  ● No access to internal trip data or company info
 *  ● Input is sanitised (trimmed + uppercased) before search
 *  ● Recent searches are stored locally only (SharedPreferences)
 *
 * @since 2026-02-22
 */
class EnterTrackingFragment : BaseFragment<FragmentEnterTrackingBinding>(
    FragmentEnterTrackingBinding::inflate
) {

    // ═══════════════════════════════════════════════════════════════
    //  PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    /** Shared ViewModel — survives across all 3 public tracking screens */
    private val viewModel: PublicTrackingViewModel by activityViewModels()

    /** Adapter for recent search items */
    private lateinit var recentSearchAdapter: RecentSearchAdapter

    /** Tracks whether entrance animations have played */
    private var hasAnimated = false

    /** Prevents duplicate navigation events */
    private var hasNavigated = false

    /** Debounce — prevent rapid-fire search taps */
    private var lastSearchTime = 0L

    // ═══════════════════════════════════════════════════════════════
    //  CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    companion object {
        /** Minimum interval between consecutive searches (ms) */
        private const val SEARCH_DEBOUNCE_MS = 800L

        /** Minimum tracking ID length to enable the Track button */
        private const val MIN_TRACKING_ID_LENGTH = 4

        /** Maximum tracking ID length */
        private const val MAX_TRACKING_ID_LENGTH = 30

        /** Animation durations */
        private const val ANIM_HERO_DURATION = 600L
        private const val ANIM_CARD_DURATION = 500L
        private const val ANIM_STAGGER_DELAY = 150L
        private const val ANIM_ERROR_DURATION = 400L
        private const val ANIM_FADE_DURATION = 300L
    }

    // ═══════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hasNavigated = false
    }

    override fun onResume() {
        super.onResume()
        // Reset navigation flag when returning from other screens
        hasNavigated = false
    }

    // ═══════════════════════════════════════════════════════════════
    //  INIT VIEWS
    // ═══════════════════════════════════════════════════════════════

    override fun initViews() {
        // ── Initialize shared ViewModel ─────────────────────────
        viewModel.initialize(requireContext())

        // ── Configure recent search RecyclerView ────────────────
        setupRecentSearchRecyclerView()

        // ── Configure input field ───────────────────────────────
        setupInputField()

        // ── Initial state ───────────────────────────────────────
        updateButtonState(isEnabled = false)
        binding.cardError.visibility = View.GONE
        binding.layoutLoading.visibility = View.GONE

        // ── Play entrance animations ────────────────────────────
        if (!hasAnimated) {
            playEntranceAnimations()
            hasAnimated = true
        }

        // ── Back press handling ─────────────────────────────────
        setupBackPressHandler()
    }

    /**
     * Configure the RecyclerView for recent searches.
     */
    private fun setupRecentSearchRecyclerView() {
        recentSearchAdapter = RecentSearchAdapter(
            onItemClick = { recentSearch ->
                // Fill the input and trigger search
                binding.etTrackingId.setText(recentSearch.trackingId)
                binding.etTrackingId.setSelection(recentSearch.trackingId.length)
                triggerSearch()
            },
            onRemoveClick = { recentSearch ->
                viewModel.removeRecentSearch(recentSearch.trackingId)
            }
        )

        binding.rvRecentSearches.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentSearchAdapter
            isNestedScrollingEnabled = false
        }
    }

    /**
     * Configure the tracking ID input field with text watcher and IME action.
     */
    private fun setupInputField() {
        // ── Text watcher for real-time validation ───────────────
        binding.etTrackingId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim() ?: ""
                val isValid = text.length >= MIN_TRACKING_ID_LENGTH

                updateButtonState(isEnabled = isValid)

                // Clear any previous error in the TextInputLayout
                if (text.isNotEmpty()) {
                    binding.tilTrackingId.error = null
                }

                // Enforce max length visually
                if (text.length > MAX_TRACKING_ID_LENGTH) {
                    binding.etTrackingId.setText(text.take(MAX_TRACKING_ID_LENGTH))
                    binding.etTrackingId.setSelection(MAX_TRACKING_ID_LENGTH)
                }
            }
        })

        // ── IME action (keyboard search key) ────────────────────
        binding.etTrackingId.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                triggerSearch()
                true
            } else {
                false
            }
        }
    }

    /**
     * Handle back press — finish the PublicTrackingActivity.
     */
    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // If we're in an error state, reset to idle
                    val state = viewModel.trackingState.value
                    if (state is PublicTrackingState.TrackingIdInvalid ||
                        state is PublicTrackingState.TrackingExpired ||
                        state is PublicTrackingState.NetworkError
                    ) {
                        viewModel.resetState()
                        clearErrorUI()
                    } else {
                        // Finish the activity
                        requireActivity().finish()
                    }
                }
            }
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  SETUP LISTENERS
    // ═══════════════════════════════════════════════════════════════

    override fun setupListeners() {
        // ── Track button ────────────────────────────────────────
        binding.btnTrack.setOnClickListener {
            triggerSearch()
        }

        // ── Retry button (in error card) ────────────────────────
        binding.btnRetry.setOnClickListener {
            clearErrorUI()
            viewModel.retry()
        }

        // ── Clear all recent searches ───────────────────────────
        binding.tvClearAll.setOnClickListener {
            viewModel.clearAllRecentSearches()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  OBSERVE DATA
    // ═══════════════════════════════════════════════════════════════

    override fun observeData() {
        // ── Tracking state ──────────────────────────────────────
        collectLatestFlow(viewModel.trackingState) { state ->
            handleTrackingState(state)
        }

        // ── Recent searches ─────────────────────────────────────
        collectLatestFlow(viewModel.recentSearches) { searches ->
            handleRecentSearches(searches)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  STATE HANDLING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle tracking state changes — drives the main UI transitions.
     *
     * States:
     *  ● Idle       → Show input card, hide error/loading
     *  ● Searching  → Show loading, hide error, disable input
     *  ● Found      → Navigate to live tracking screen
     *  ● Invalid    → Show error card with "not found" message
     *  ● Expired    → Show error card with "expired" message
     *  ● Network    → Show error card with "connection" message
     */
    private fun handleTrackingState(state: PublicTrackingState) {
        when (state) {
            is PublicTrackingState.Idle -> {
                showIdleState()
            }

            is PublicTrackingState.Searching -> {
                showSearchingState()
            }

            is PublicTrackingState.Found -> {
                showFoundState(state.tripInfo)
            }

            is PublicTrackingState.TrackingIdInvalid -> {
                showErrorState(
                    iconRes = R.drawable.ic_public_search,
                    title = getString(R.string.public_error_invalid_title),
                    message = state.message
                )
            }

            is PublicTrackingState.TrackingExpired -> {
                showErrorState(
                    iconRes = R.drawable.ic_public_clock,
                    title = getString(R.string.public_error_expired_title),
                    message = state.message
                )
            }

            is PublicTrackingState.NetworkError -> {
                showErrorState(
                    iconRes = R.drawable.ic_cloud_off,
                    title = getString(R.string.public_error_network_title),
                    message = state.message
                )
            }
        }
    }

    /**
     * Handle recent searches list updates.
     */
    private fun handleRecentSearches(searches: List<com.example.movexa.data.model.RecentSearch>) {
        if (searches.isNotEmpty()) {
            binding.layoutRecent.visibility = View.VISIBLE
            recentSearchAdapter.submitList(searches)
        } else {
            binding.layoutRecent.visibility = View.GONE
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  UI STATE TRANSITIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Show the default idle state — input card visible, no errors.
     */
    private fun showIdleState() {
        binding.cardError.visibility = View.GONE
        binding.layoutLoading.visibility = View.GONE
        binding.cardInput.alpha = 1f
        binding.btnTrack.isEnabled = true

        val text = binding.etTrackingId.text?.toString()?.trim() ?: ""
        updateButtonState(isEnabled = text.length >= MIN_TRACKING_ID_LENGTH)
    }

    /**
     * Show the searching state — loading indicator, disable input.
     */
    private fun showSearchingState() {
        hideKeyboard()
        binding.cardError.visibility = View.GONE
        binding.layoutLoading.visibility = View.VISIBLE
        binding.btnTrack.isEnabled = false

        // Animate loading appearance
        binding.layoutLoading.alpha = 0f
        binding.layoutLoading.animate()
            .alpha(1f)
            .setDuration(ANIM_FADE_DURATION)
            .start()
    }

    /**
     * Show the found state — navigate to live tracking.
     *
     * For ACTIVE trips: navigate to live tracking map
     * For COMPLETED/CANCELLED trips: navigate to delivery details
     */
    private fun showFoundState(tripInfo: PublicTripInfo) {
        binding.layoutLoading.visibility = View.GONE
        binding.cardError.visibility = View.GONE

        if (hasNavigated) return
        hasNavigated = true

        // Small delay to let the UI settle before navigation
        binding.root.postDelayed({
            try {
                if (tripInfo.isActive) {
                    // Active trip → Live tracking map
                    findNavController().navigate(
                        R.id.action_enter_to_liveTracking
                    )
                } else {
                    // Completed/Cancelled/Created → Delivery details
                    findNavController().navigate(
                        R.id.action_enter_to_deliveryDetails
                    )
                }
            } catch (e: Exception) {
                hasNavigated = false
                e.printStackTrace()
            }
        }, 200L)
    }

    /**
     * Show an error state with icon, title, and message.
     *
     * Supports three error types:
     *  ● Invalid tracking ID (search icon)
     *  ● Expired tracking (clock icon)
     *  ● Network error (cloud-off icon)
     */
    private fun showErrorState(iconRes: Int, title: String, message: String) {
        hideKeyboard()
        binding.layoutLoading.visibility = View.GONE
        binding.btnTrack.isEnabled = true
        updateButtonState(isEnabled = true)

        // Configure error card content
        binding.ivErrorIcon.setImageResource(iconRes)
        binding.ivErrorIcon.imageTintList = ContextCompat.getColorStateList(
            requireContext(), R.color.public_error_red
        )
        binding.tvErrorTitle.text = title
        binding.tvErrorMessage.text = message

        // Show with animation
        binding.cardError.visibility = View.VISIBLE
        binding.cardError.alpha = 0f
        binding.cardError.translationY = 30f
        binding.cardError.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ANIM_ERROR_DURATION)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * Clear the error card UI.
     */
    private fun clearErrorUI() {
        binding.cardError.animate()
            .alpha(0f)
            .translationY(-20f)
            .setDuration(200L)
            .withEndAction {
                binding.cardError.visibility = View.GONE
                binding.cardError.translationY = 0f
            }
            .start()
    }

    // ═══════════════════════════════════════════════════════════════
    //  SEARCH TRIGGER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Trigger a tracking ID search with debounce protection.
     *
     * Flow:
     *  1. Get text from input field
     *  2. Check debounce interval
     *  3. Validate minimum length
     *  4. Hide keyboard
     *  5. Delegate to ViewModel
     */
    private fun triggerSearch() {
        val trackingId = binding.etTrackingId.text?.toString()?.trim() ?: ""

        // ── Debounce ────────────────────────────────────────────
        val now = System.currentTimeMillis()
        if (now - lastSearchTime < SEARCH_DEBOUNCE_MS) return
        lastSearchTime = now

        // ── Validation ──────────────────────────────────────────
        if (trackingId.isBlank()) {
            binding.tilTrackingId.error = getString(R.string.public_error_empty)
            shakeView(binding.tilTrackingId)
            return
        }

        if (trackingId.length < MIN_TRACKING_ID_LENGTH) {
            binding.tilTrackingId.error = getString(R.string.public_error_too_short)
            shakeView(binding.tilTrackingId)
            return
        }

        // ── Clear previous error state ──────────────────────────
        binding.tilTrackingId.error = null
        clearErrorUI()

        // ── Hide keyboard ───────────────────────────────────────
        hideKeyboard()

        // ── Reset navigation flag ───────────────────────────────
        hasNavigated = false

        // ── Trigger search via ViewModel ────────────────────────
        viewModel.searchTrackingId(trackingId)
    }

    // ═══════════════════════════════════════════════════════════════
    //  BUTTON STATE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Update the Track button's enabled/disabled visual state.
     */
    private fun updateButtonState(isEnabled: Boolean) {
        binding.btnTrack.isEnabled = isEnabled
        binding.btnTrack.alpha = if (isEnabled) 1.0f else 0.5f
    }

    // ═══════════════════════════════════════════════════════════════
    //  ANIMATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Play staggered entrance animations for the hero section
     * and input card on first load.
     *
     * Animation sequence:
     *  1. Hero icon fades in + scales up (with overshoot)
     *  2. Title slides in from bottom
     *  3. Subtitle slides in from bottom
     *  4. Input card slides in from bottom + fades in
     *  5. Recent searches section fades in
     */
    private fun playEntranceAnimations() {
        // ── Start all views hidden ──────────────────────────────
        val heroIcon = binding.ivHeroIcon
        val title = binding.tvTitle
        val subtitle = binding.tvSubtitle
        val cardInput = binding.cardInput
        val layoutRecent = binding.layoutRecent
        val footer = binding.tvFooter

        listOf(heroIcon, title, subtitle, cardInput, layoutRecent, footer).forEach {
            it.alpha = 0f
        }

        heroIcon.scaleX = 0.3f
        heroIcon.scaleY = 0.3f
        title.translationY = 40f
        subtitle.translationY = 40f
        cardInput.translationY = 60f

        // ── Hero icon — scale + fade ────────────────────────────
        val iconAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(heroIcon, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(heroIcon, View.SCALE_X, 0.3f, 1f),
                ObjectAnimator.ofFloat(heroIcon, View.SCALE_Y, 0.3f, 1f)
            )
            duration = ANIM_HERO_DURATION
            interpolator = OvershootInterpolator(1.5f)
        }

        // ── Title — slide up + fade ─────────────────────────────
        val titleAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, 40f, 0f)
            )
            duration = ANIM_CARD_DURATION
            interpolator = DecelerateInterpolator()
            startDelay = ANIM_STAGGER_DELAY
        }

        // ── Subtitle — slide up + fade ──────────────────────────
        val subtitleAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(subtitle, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(subtitle, View.TRANSLATION_Y, 40f, 0f)
            )
            duration = ANIM_CARD_DURATION
            interpolator = DecelerateInterpolator()
            startDelay = ANIM_STAGGER_DELAY * 2
        }

        // ── Input card — slide up + fade ────────────────────────
        val cardAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(cardInput, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(cardInput, View.TRANSLATION_Y, 60f, 0f)
            )
            duration = ANIM_CARD_DURATION
            interpolator = DecelerateInterpolator()
            startDelay = ANIM_STAGGER_DELAY * 3
        }

        // ── Recent searches — fade ──────────────────────────────
        val recentAnimator = ObjectAnimator.ofFloat(layoutRecent, View.ALPHA, 0f, 1f).apply {
            duration = ANIM_FADE_DURATION
            startDelay = ANIM_STAGGER_DELAY * 4
        }

        // ── Footer — fade ───────────────────────────────────────
        val footerAnimator = ObjectAnimator.ofFloat(footer, View.ALPHA, 0f, 1f).apply {
            duration = ANIM_FADE_DURATION
            startDelay = ANIM_STAGGER_DELAY * 5
        }

        // ── Play all ────────────────────────────────────────────
        AnimatorSet().apply {
            playTogether(
                iconAnimator, titleAnimator, subtitleAnimator,
                cardAnimator, recentAnimator, footerAnimator
            )
            start()
        }
    }

    /**
     * Play a horizontal shake animation on a view (for validation errors).
     */
    private fun shakeView(view: View) {
        val shake = ObjectAnimator.ofFloat(
            view, View.TRANSLATION_X,
            0f, -12f, 12f, -8f, 8f, -4f, 4f, 0f
        ).apply {
            duration = 400L
            interpolator = AccelerateDecelerateInterpolator()
        }
        shake.start()
    }

    // ═══════════════════════════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Hide the soft keyboard.
     */
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etTrackingId.windowToken, 0)
    }
}
