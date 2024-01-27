package com.example.stopwatch

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.example.stopwatch.databinding.ActivityMainBinding
import com.example.stopwatch.service.ServiceHelper
import com.example.stopwatch.service.StopwatchService
import com.example.stopwatch.service.StopwatchState
import com.example.stopwatch.util.Constants
import com.example.stopwatch.util.Constants.ACTION_SERVICE_CANCEL
import com.example.stopwatch.util.Constants.ACTION_SERVICE_START
import com.example.stopwatch.util.Constants.ACTION_SERVICE_STOP
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    private lateinit var stopwatchService: StopwatchService

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as StopwatchService.StopwatchBinder
            stopwatchService = binder.getService()
            observeLiveData()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            stopwatchService = null!!
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        this.window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        binding.autoSelectType.setDropDownBackgroundDrawable(ResourcesCompat.getDrawable(resources,
            R.color.white,
            null))

        val intent = Intent(this, StopwatchService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        val selectionList = resources.getStringArray(R.array.time_suggestions)
        val selectionAdapter =
            ArrayAdapter(this, R.layout.dropdown_item, selectionList)
        binding.autoSelectType.setAdapter(selectionAdapter)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.autoSelectType.setOnItemClickListener { _, _, _, _ ->
            when (binding.selectType.editText?.text.toString().trim()) {
                Constants.SECONDS -> binding.milliSecondTv.visibility = View.GONE
                Constants.MILLISECONDS -> binding.milliSecondTv.visibility = View.VISIBLE

            }
        }

        binding.startBtn.setOnClickListener {
            ServiceHelper.triggerForegroundService(
                context = this,
                action = if (stopwatchService.currentState.value == StopwatchState.Started) ACTION_SERVICE_STOP
                else ACTION_SERVICE_START
            )
        }
        binding.cancelBtn.setOnClickListener {
            ServiceHelper.triggerForegroundService(
                context = this, action = ACTION_SERVICE_CANCEL
            )
            binding.selectType.visibility = View.VISIBLE
        }
    }

    @SuppressLint("ResourceAsColor", "SetTextI18n")
    private fun observeLiveData() {

        lifecycleScope.launch {
            stopwatchService.millisSeconds.collect { milliSeconds ->
                binding.milliSecondTv.text = ":$milliSeconds"
            }
        }

        lifecycleScope.launch {
            stopwatchService.seconds.collect { seconds ->
                binding.secondTv.text = seconds
                addAnimation(view = binding.secondTv)
            }
        }

        lifecycleScope.launch {
            stopwatchService.minutes.collect { minutes ->
                binding.minutesTv.text = "$minutes:"
                addAnimation(view = binding.minutesTv)
            }
        }

        lifecycleScope.launch {
            stopwatchService.hours.collect { hours ->
                binding.minutesTv.text = "$hours:"
                addAnimation(view = binding.hoursTv)
            }
        }

        lifecycleScope.launch {
            stopwatchService.currentState.collect { state ->
                if (state == StopwatchState.Started) {
                    binding.selectType.visibility = View.INVISIBLE
                    binding.startBtn.text = "Stop"
                    binding.startBtn.setBackgroundResource(R.drawable.button_background_stop)
                    binding.cancelBtn.setBackgroundResource(R.drawable.button_background)
                } else {
                    binding.startBtn.text = "Start"
                    binding.startBtn.setBackgroundResource(R.drawable.button_background)
                    binding.cancelBtn.setBackgroundResource(R.drawable.button_background_disabled)
                }
            }
        }

    }


    private fun requestPermissions(vararg permissions: String) {
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            result.entries.forEach {
                Log.d("MainActivity", "${it.key} = ${it.value}")
            }
        }
        requestPermissionLauncher.launch(permissions.asList().toTypedArray())
    }

    private fun addAnimation(view: TextView, duration: Long = 1000) {
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        val fadeOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out)
        val slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        val slideOut = AnimationUtils.loadAnimation(this, R.anim.slide_down)

        fadeIn.duration = duration
        fadeOut.duration = duration
        slideIn.duration = duration
        slideOut.duration = duration

        // Fade In Animation
        view.startAnimation(fadeIn)
        view.startAnimation(fadeOut)
        view.startAnimation(slideIn)
        view.startAnimation(slideOut)

    }
}