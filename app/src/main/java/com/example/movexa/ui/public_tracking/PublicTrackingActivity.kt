package com.example.movexa.ui.public_tracking

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.movexa.databinding.ActivityPublicTrackingBinding

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  PUBLIC TRACKING ACTIVITY
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * Standalone Activity for the Public Customer Tracking System.
 *
 * This Activity is completely isolated from the authenticated MainActivity / nav_graph
 * to ensure that unauthenticated customers cannot access any internal fleet data.
 *
 * ─── Key Design Decisions ─────────────────────────────────────────
 *
 *  ● NO toolbar — each fragment manages its own top bar
 *  ● NO bottom navigation — single-task flow
 *  ● NO auth checks — public access only
 *  ● SharedPreferences for recent searches (no DataStore, no Room)
 *  ● SharedViewModel (PublicTrackingViewModel) initialised here
 *    and shared across all 3 child fragments via activityViewModels()
 *
 * ─── Navigation Flow ─────────────────────────────────────────────
 *
 *  EnterTrackingFragment ──→ PublicLiveTrackingFragment ──→ DeliveryDetailsFragment
 *                          └─→ DeliveryDetailsFragment ──→ PublicLiveTrackingFragment
 *
 * @since 2026-02-22
 */
class PublicTrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPublicTrackingBinding

    // ─── Lifecycle ──────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPublicTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()
        setupWindowInsets()
        initViewModel()
    }

    // ─── Setup ──────────────────────────────────────────────────

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    /**
     * Initialise the shared ViewModel so fragments can retrieve it
     * via `activityViewModels()`.
     *
     * `initialize(context)` loads recent searches from SharedPreferences.
     */
    private fun initViewModel() {
        val viewModel = ViewModelProvider(this)[PublicTrackingViewModel::class.java]
        viewModel.initialize(this)
    }
}
