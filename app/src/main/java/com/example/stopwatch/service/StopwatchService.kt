package com.example.stopwatch.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import androidx.core.app.NotificationCompat
import com.example.stopwatch.util.Constants.ACTION_SERVICE_CANCEL
import com.example.stopwatch.util.Constants.ACTION_SERVICE_START
import com.example.stopwatch.util.Constants.ACTION_SERVICE_STOP
import com.example.stopwatch.util.Constants.NOTIFICATION_CHANNEL_ID
import com.example.stopwatch.util.Constants.NOTIFICATION_CHANNEL_NAME
import com.example.stopwatch.util.Constants.NOTIFICATION_ID
import com.example.stopwatch.util.Constants.STOPWATCH_STATE
import com.example.stopwatch.util.formatTime
import com.example.stopwatch.util.milliPad
import com.example.stopwatch.util.pad
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class StopwatchService : Service() {
    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var notificationBuilder: NotificationCompat.Builder

    private val binder = StopwatchBinder()

    private var duration: Duration = Duration.ZERO
    private var milliDuration: Duration = Duration.ZERO
    private lateinit var timer: Timer
    private lateinit var milliTimer: Timer

    private val _millisSeconds = MutableStateFlow("000")
    val millisSeconds: StateFlow<String> get() = _millisSeconds

    private val _seconds = MutableStateFlow("00")
    val seconds: StateFlow<String> get() = _seconds

    private val _minutes = MutableStateFlow("00")
    val minutes: StateFlow<String> get() = _minutes

    private val _hours = MutableStateFlow("00")
    val hours: StateFlow<String> get() = _hours

    private val _currentState = MutableStateFlow(StopwatchState.Idle)
    val currentState: StateFlow<StopwatchState> get() = _currentState

    override fun onBind(p0: Intent?) = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra(STOPWATCH_STATE)) {
            StopwatchState.Started.name -> {
                setStopButton()
                startForegroundService()
                startStopwatch { hours, minutes, seconds ->
                    updateNotification(hours = hours,
                        minutes = minutes,
                        seconds = seconds)
                }
            }
            StopwatchState.Stopped.name -> {
                stopStopwatch()
                setResumeButton()
            }
            StopwatchState.Canceled.name -> {
                stopStopwatch()
                cancelStopwatch()
                stopForegroundService()
            }
        }
        intent?.action.let {
            when (it) {
                ACTION_SERVICE_START -> {
                    setStopButton()
                    startForegroundService()
                    startStopwatch { hours, minutes, seconds ->
                        updateNotification(hours = hours,
                            minutes = minutes,
                            seconds = seconds)
                    }
                }
                ACTION_SERVICE_STOP -> {
                    stopStopwatch()
                    setResumeButton()
                }
                ACTION_SERVICE_CANCEL -> {
                    stopStopwatch()
                    cancelStopwatch()
                    stopForegroundService()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startStopwatch(onTick: (h: String, m: String, s: String) -> Unit) {
        _currentState.value = StopwatchState.Started

        milliTimer = fixedRateTimer(initialDelay = 0L, period = 1L) {
            milliDuration = milliDuration.plus(1.milliseconds)
            updateTimeUnitsMilli()
        }

        timer = fixedRateTimer(initialDelay = 1000L, period = 1000L) {
            duration = duration.plus(1.seconds)
            updateTimeUnits()
            onTick(hours.value, minutes.value, seconds.value)
        }
    }

    private fun stopStopwatch() {
        if (this::timer.isInitialized) {
            timer.cancel()
        }
        if (this::milliTimer.isInitialized) {
            milliTimer.cancel()
        }
        _currentState.value = StopwatchState.Stopped
    }

    private fun cancelStopwatch() {
        duration = Duration.ZERO
        milliDuration = Duration.ZERO
        _currentState.value = StopwatchState.Idle
        updateTimeUnits()
        updateTimeUnitsMilli()
    }

    private fun updateTimeUnits() {
        duration.toComponents { hours, minutes, seconds, _ ->
            this@StopwatchService._hours.value = hours.toInt().pad()
            this@StopwatchService._minutes.value = minutes.pad()
            this@StopwatchService._seconds.value = seconds.pad()
        }
    }

    private fun updateTimeUnitsMilli() {
        milliDuration.toComponents { _, _, _, nanoseconds ->
            this@StopwatchService._millisSeconds.value =
                (TimeUnit.NANOSECONDS.toMillis(nanoseconds.toLong())).toInt().milliPad()

        }
    }


    private fun startForegroundService() {
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
        createNotificationChannel()
    }

    private fun stopForegroundService() {
        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification(
        hours: String,
        minutes: String,
        seconds: String,
    ) {
        notificationManager.notify(
            NOTIFICATION_ID,
            notificationBuilder.setContentText(
                formatTime(
                    hours = hours,
                    minutes = minutes,
                    seconds = seconds
                )
            ).build()
        )
    }

    @SuppressLint("RestrictedApi")
    private fun setStopButton() {
        notificationBuilder.mActions.removeAt(0)
        notificationBuilder.mActions.add(
            0,
            NotificationCompat.Action(
                0,
                "Stop",
                ServiceHelper.stopPendingIntent(this)
            )
        )
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    @SuppressLint("RestrictedApi")
    private fun setResumeButton() {
        notificationBuilder.mActions.removeAt(0)
        notificationBuilder.mActions.add(
            0,
            NotificationCompat.Action(
                0,
                "Resume",
                ServiceHelper.resumePendingIntent(this)
            )
        )
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    inner class StopwatchBinder : Binder() {
        fun getService(): StopwatchService = this@StopwatchService
    }
}

enum class StopwatchState {
    Idle,
    Started,
    Stopped,
    Canceled
}