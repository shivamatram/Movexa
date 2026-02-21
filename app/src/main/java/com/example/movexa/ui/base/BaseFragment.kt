package com.example.movexa.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.example.movexa.MainActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Base fragment with safe ViewBinding lifecycle management.
 * All fragments in Movexa must extend this class.
 *
 * @param VB The ViewBinding type for this fragment.
 * @param inflate The inflate function reference for the ViewBinding.
 */
abstract class BaseFragment<VB : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> VB
) : Fragment() {

    // ─── ViewBinding ────────────────────────────────────────────
    private var _binding: VB? = null

    /**
     * Access the ViewBinding instance. Only valid between onCreateView and onDestroyView.
     */
    protected val binding: VB
        get() = _binding ?: throw IllegalStateException(
            "ViewBinding is only valid between onCreateView and onDestroyView. " +
            "Fragment: ${this::class.simpleName}"
        )

    /**
     * Safe access to binding that returns null if view is destroyed.
     */
    protected val bindingOrNull: VB?
        get() = _binding

    // ─── Lifecycle ──────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        observeData()
        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─── Abstract / Open Methods ────────────────────────────────

    /**
     * Initialize views and configure UI state.
     * Called in onViewCreated after binding is available.
     */
    protected open fun initViews() {}

    /**
     * Set up data observers (LiveData, StateFlow, etc).
     * Called in onViewCreated.
     */
    protected open fun observeData() {}

    /**
     * Set up click listeners and other UI interactions.
     * Called in onViewCreated.
     */
    protected open fun setupListeners() {}

    // ─── Loading ────────────────────────────────────────────────

    /**
     * Show the global loading indicator from activity.
     */
    protected fun showLoading() {
        (activity as? MainActivity)?.showLoading()
    }

    /**
     * Hide the global loading indicator from activity.
     */
    protected fun hideLoading() {
        (activity as? MainActivity)?.hideLoading()
    }

    // ─── Error / Messages ───────────────────────────────────────

    /**
     * Show an error message via Snackbar.
     */
    protected fun showError(message: String) {
        view?.let { rootView ->
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(
                    com.example.movexa.theme.AppColors.ERROR
                )
                .setTextColor(
                    com.example.movexa.theme.AppColors.ON_ERROR
                )
                .show()
        }
    }

    /**
     * Show a success message via Snackbar.
     */
    protected fun showSuccess(message: String) {
        view?.let { rootView ->
            Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(
                    com.example.movexa.theme.AppColors.SUCCESS
                )
                .setTextColor(
                    com.example.movexa.theme.AppColors.ON_ERROR
                )
                .show()
        }
    }

    /**
     * Show an info message via Snackbar.
     */
    protected fun showInfo(message: String) {
        view?.let { rootView ->
            Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(
                    com.example.movexa.theme.AppColors.INFO
                )
                .setTextColor(
                    com.example.movexa.theme.AppColors.ON_ERROR
                )
                .show()
        }
    }

    // ─── Flow Observers ─────────────────────────────────────────

    /**
     * Collect a Flow lifecycle-aware in STARTED state.
     */
    protected fun <T> collectFlow(flow: Flow<T>, action: suspend (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect { action(it) }
            }
        }
    }

    /**
     * Collect latest value from a Flow lifecycle-aware in STARTED state.
     */
    protected fun <T> collectLatestFlow(flow: Flow<T>, action: suspend (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collectLatest { action(it) }
            }
        }
    }

    // ─── Navigation ─────────────────────────────────────────────

    /**
     * Navigate safely using action ID.
     */
    protected fun navigateTo(actionId: Int, args: Bundle? = null) {
        try {
            findNavController().navigate(actionId, args)
        } catch (e: Exception) {
            // Prevents crashes from rapid double-taps or invalid destinations
            e.printStackTrace()
        }
    }

    /**
     * Navigate safely using NavDirections.
     */
    protected fun navigateTo(directions: NavDirections) {
        try {
            findNavController().navigate(directions)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Navigate back in the navigation stack.
     */
    protected fun navigateBack() {
        try {
            findNavController().popBackStack()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ─── Toolbar Control ────────────────────────────────────────

    /**
     * Show the activity toolbar with optional title.
     */
    protected fun showToolbar(title: String? = null) {
        (activity as? MainActivity)?.showToolbar(title)
    }

    /**
     * Hide the activity toolbar.
     */
    protected fun hideToolbar() {
        (activity as? MainActivity)?.hideToolbar()
    }

    /**
     * Show the bottom navigation bar.
     */
    protected fun showBottomNav() {
        (activity as? MainActivity)?.showBottomNav()
    }

    /**
     * Hide the bottom navigation bar.
     */
    protected fun hideBottomNav() {
        (activity as? MainActivity)?.hideBottomNav()
    }
}
