package com.seki.multilangduo.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.seki.multilangduo.*
import com.seki.multilangduo.model.TaskPhase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller by lazy { (application as MultilangDuoApplication).playback }
    private var armed = false
    private var lastStartId = 0
    private var wakeJob: Job? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private data class Status(val busy: Boolean, val paused: Boolean, val canPause: Boolean, val phase: TaskPhase, val line: Int)

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "朗读与音频任务", NotificationManager.IMPORTANCE_LOW).apply { description = "后台朗读、跟读识别和音频生成的任务控制" }
        )
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MultilangDuo:Playback")
        scope.launch {
            controller.state.map { Status(it.busy, it.paused, it.canPause, it.phase, it.currentLineIndex) }.distinctUntilChanged().collect { state ->
                if (!armed) return@collect
                if (!state.busy) {
                    wakeJob?.cancel(); wakeJob = null
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelfResult(lastStartId)
                } else {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
                    updateWakeLock(!state.paused)
                }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            TOGGLE -> controller.togglePause()
            CANCEL -> controller.cancel()
            CONTINUE -> controller.continueRecognition()
            else -> {
                try {
                    val types = if (Build.VERSION.SDK_INT >= 29) {
                        var value = if (intent?.getBooleanExtra(EXPORT, false) == true) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        if (Build.VERSION.SDK_INT >= 30 && intent?.getBooleanExtra(MICROPHONE, false) == true) value = value or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        value
                    } else 0
                    ServiceCompat.startForeground(this, NOTIFICATION, notification(), types)
                    armed = true
                    controller.executePending()
                } catch (e: Exception) {
                    controller.cancel(); controller.message("后台服务启动失败：${e.message}")
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelfResult(startId)
                }
            }
        }
        if (controller.state.value.busy) updateWakeLock(!controller.state.value.paused)
        else { stopForeground(STOP_FOREGROUND_REMOVE); stopSelfResult(startId) }
        // Never restart a killed task from the beginning or reopen the microphone without a user action.
        return START_NOT_STICKY
    }
    private fun updateWakeLock(active: Boolean) {
        if (!active) { wakeJob?.cancel(); wakeJob = null; return }
        if (wakeJob?.isActive == true) return
        wakeJob = scope.launch {
            try {
                while (isActive) {
                    wakeLock.acquire(10 * 60 * 1000L)
                    delay(5 * 60 * 1000L)
                    if (wakeLock.isHeld) wakeLock.release()
                }
            } finally { if (wakeLock.isHeld) wakeLock.release() }
        }
    }
    private fun command(action: String): PendingIntent = PendingIntent.getService(this, action.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notification(): Notification {
        val state = controller.state.value
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val status = when {
            state.paused -> "已暂停，点击继续播放"
            state.phase == TaskPhase.Listening -> "正在跟读识别"
            state.phase == TaskPhase.WaitingForConfirmation -> "等待确认或语音输入"
            state.phase == TaskPhase.Generating -> "正在生成 WAV"
            state.phase == TaskPhase.Saving -> "正在保存 WAV"
            state.phase == TaskPhase.Starting -> "正在启动任务"
            else -> "正在朗读第 ${state.currentLineIndex + 1} 行"
        }
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.icon_monochrome)
            .setContentTitle("文本转语音应用").setContentText(status).setContentIntent(open)
            .setOnlyAlertOnce(true).setSilent(true).setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (state.canPause) addAction(0, if (state.paused) "继续播放" else "暂停", command(TOGGLE))
                if (state.phase == TaskPhase.WaitingForConfirmation) addAction(0, "完成并继续", command(CONTINUE))
                addAction(0, "取消朗读", command(CANCEL))
            }.build()
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        controller.cancel(); controller.message("后台音频处理达到系统时间限制，任务已停止。")
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() {
        // Unexpected service destruction must release the player and microphone as well.
        if (controller.state.value.busy) controller.cancel()
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        private const val CHANNEL = "playback"
        private const val NOTIFICATION = 1
        private const val EXPORT = "export"
        private const val MICROPHONE = "microphone"
        private const val TOGGLE = "com.seki.multilangduo.TOGGLE_PAUSE"
        private const val CANCEL = "com.seki.multilangduo.CANCEL"
        private const val CONTINUE = "com.seki.multilangduo.CONTINUE"
        fun start(context: Context, export: Boolean, microphone: Boolean) {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java)
                .putExtra(EXPORT, export).putExtra(MICROPHONE, microphone))
        }
    }
}
