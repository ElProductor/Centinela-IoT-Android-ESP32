package com.inacap.centinelaiot.ui.main

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.inacap.centinelaiot.CentinelaApp
import com.inacap.centinelaiot.R
import com.inacap.centinelaiot.databinding.ActivityMainBinding
import com.inacap.centinelaiot.ui.audit.AuditLogFragment
import com.inacap.centinelaiot.ui.centinela.CentinelaFragment
import com.inacap.centinelaiot.ui.settings.SettingsFragment
import com.inacap.centinelaiot.ui.universal.UniversalHubFragment
import com.inacap.centinelaiot.ui.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val sessionManager by lazy { CentinelaApp.instance.sessionManager }

    private val centinelaFragment by lazy { CentinelaFragment() }
    private val universalHubFragment by lazy { UniversalHubFragment() }
    private val auditLogFragment by lazy { AuditLogFragment() }
    private val settingsFragment by lazy { SettingsFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupBottomNavigation()
        setupObservers()

        // Cargar por defecto el panel Centinela
        if (savedInstanceState == null) {
            loadFragment(centinelaFragment)
        }
    }

    private fun setupUI() {
        binding.tvRoleBadge.text = sessionManager.role.uppercase()
        val roleColor = when (sessionManager.role.uppercase()) {
            "ADMIN" -> ContextCompat.getColor(this, R.color.primary)
            "OPERATOR" -> ContextCompat.getColor(this, R.color.secondary)
            else -> ContextCompat.getColor(this, R.color.text_secondary)
        }
        binding.tvRoleBadge.setTextColor(roleColor)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_centinela -> {
                    loadFragment(centinelaFragment)
                    true
                }
                R.id.navigation_universal -> {
                    loadFragment(universalHubFragment)
                    true
                }
                R.id.navigation_audit -> {
                    loadFragment(auditLogFragment)
                    true
                }
                R.id.navigation_settings -> {
                    loadFragment(settingsFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun setupObservers() {
        viewModel.connectionStatus.observe(this) { status ->
            binding.tvConnectionStatus.text = status
            val isOnline = status.contains("ONLINE", true) || status.contains("SIMULADOR", true)
            val color = if (isOnline) {
                ContextCompat.getColor(this, R.color.status_safe)
            } else {
                ContextCompat.getColor(this, R.color.status_warning)
            }
            binding.tvConnectionStatus.setTextColor(color)
        }
    }
}
