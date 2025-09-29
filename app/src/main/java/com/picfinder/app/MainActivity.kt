package com.picfinder.app

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.picfinder.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupNavigation()
        setupBackButtonBehavior()
    }
    
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)
        
        // Update navigation mapping
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_search -> {
                    navController.navigate(R.id.searchFragment)
                    true
                }
                R.id.navigation_folders -> {
                    navController.navigate(R.id.foldersFragment)
                    true
                }
                R.id.navigation_settings -> {
                    navController.navigate(R.id.settingsFragment)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupBackButtonBehavior() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentDestination = navController.currentDestination?.id
                
                when (currentDestination) {
                    R.id.searchFragment -> {
                        // Already on search fragment, exit the app
                        finish()
                    }
                    else -> {
                        // Navigate to search fragment first
                        navController.navigate(R.id.searchFragment)
                        // Update bottom navigation selection
                        findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.navigation_search
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }
}