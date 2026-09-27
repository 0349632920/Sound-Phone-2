package com.example.sound

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

class MainActivity : AppCompatActivity() {

    // ⚠️ URL NGROK CỦA BẠN
    private val serverUrl = "https://amplifier-rake-overjoyed.ngrok-free.dev"

    private var socket: Socket? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private lateinit var tvStatus: TextView
    private lateinit var tvDevice: TextView

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        buildUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        stopSound()
    }

    private fun buildUI() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 80, 50, 80)
        }

        val title = TextView(this).apply {
            text = "🔊 Remote Sound"
            textSize = 26f
            setPadding(0, 0, 0, 30)
        }

        tvDevice = TextView(this).apply {
            text = "📱 Thiết bị: ${Build.MODEL}\n" +
                   "🤖 Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            textSize = 14f
            setPadding(0, 0, 0, 30)
        }

        tvStatus = TextView(this).apply {
            text = "⚪ Chưa kết nối"
            textSize = 16f
            setPadding(0, 30, 0, 30)
        }

        val btnConnect = Button(this).apply {
            text = "🔗 Kết nối Server"
            textSize = 16f
            setOnClickListener { connect() }
        }

        val btnDisconnect = Button(this).apply {
            text = "🔌 Ngắt kết nối"
            textSize = 16f
            setOnClickListener { disconnect() }
        }

        val btnTest = Button(this).apply {
            text = "🔔 Test âm thanh (offline)"
            textSize = 16f
            setOnClickListener {
                playSound("notification", 1.0f, 3)
                tvStatus.text = "🔔 Đang test âm thanh..."
            }
        }

        val btnStop = Button(this).apply {
            text = "⏹️ Dừng âm thanh"
            textSize = 16f
            setOnClickListener {
                stopSound()
                tvStatus.text = "⏹️ Đã dừng"
            }
        }

        val tvHelp = TextView(this).apply {
            text = "\n📖 Hướng dẫn:\n" +
                   "1. Đảm bảo server Python + Ngrok đang chạy\n" +
                   "2. Bấm 'Kết nối Server'\n" +
                   "3. Đợi trạng thái chuyển thành '✅ Đã kết nối'\n" +
                   "4. Từ PC chạy pc_gui.py để điều khiển\n" +
                   "\n💡 URL Server:\n$serverUrl"
            textSize = 12f
            setPadding(0, 40, 0, 0)
        }

        layout.addView(title)
        layout.addView(tvDevice)
        layout.addView(tvStatus)
        layout.addView(btnConnect)
        layout.addView(btnDisconnect)
        layout.addView(btnTest)
        layout.addView(btnStop)
        layout.addView(tvHelp)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun connect() {
        if (socket?.connected() == true) {
            tvStatus.text = "ℹ️ Đã kết nối rồi"
            return
        }

        try {
            tvStatus.text = "🔄 Đang kết nối..."

            // ⭐ FIX: value phải là List<String>
            val opts = IO.Options().apply {
                reconnection = true
                reconnectionDelay = 3000
                reconnectionAttempts = 10
                timeout = 15000
                extraHeaders = mapOf(
                    "ngrok-skip-browser-warning" to listOf("true"),
                    "User-Agent" to listOf("RemoteSoundApp/1.0")
                )
            }

            socket = IO.socket(serverUrl, opts)
            setupSocketListeners()
            socket?.connect()

        } catch (e: URISyntaxException) {
            tvStatus.text = "❌ URL sai: ${e.message}"
            Log.e("Socket", "URL error: ${e.message}")
        } catch (e: Exception) {
            tvStatus.text = "❌ Lỗi: ${e.message}"
            Log.e("Socket", "Error: ${e.message}")
        }
    }

    private fun setupSocketListeners() {
        socket?.on(Socket.EVENT_CONNECT) {
            Log.d("Socket", "✅ Đã kết nối server")
            runOnUiThread {
                tvStatus.text = "✅ Đã kết nối"
            }

            try {
                val reg = JSONObject().apply {
                    put("name", Build.MODEL)
                    put("manufacturer", Build.MANUFACTURER)
                    put("android", Build.VERSION.RELEASE)
                }
                socket?.emit("register", reg)
                Log.d("Socket", "📱 Đã đăng ký: ${Build.MODEL}")
            } catch (e: Exception) {
                Log.e("Socket", "Lỗi register: ${e.message}")
            }
        }

        socket?.on(Socket.EVENT_DISCONNECT) { args ->
            Log.d("Socket", "❌ Mất kết nối: ${args.joinToString()}")
            runOnUiThread {
                tvStatus.text = "❌ Mất kết nối"
            }
        }

        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e("Socket", "⚠️ Lỗi kết nối: ${args.joinToString()}")
            runOnUiThread {
                tvStatus.text = "⚠️ Lỗi kết nối: ${args.firstOrNull()}"
            }
        }

        // ⭐ FIX: dùng chuỗi "reconnect" thay vì Socket.EVENT_RECONNECT
        socket?.on("reconnect") { args ->
            Log.d("Socket", "🔄 Đã kết nối lại: ${args.joinToString()}")
            runOnUiThread {
                tvStatus.text = "✅ Đã kết nối lại"
            }
        }

        socket?.on("command") { args ->
            try {
                val data = args[0] as JSONObject
                val action = data.optString("action", "")
                Log.d("Socket", "📩 Nhận lệnh: $action")

                when (action) {
                    "play" -> {
                        val sound = data.optString("sound", "notification")
                        val volume = data.optDouble("volume", 1.0).toFloat()
                        val duration = data.optInt("duration", 5)

                        runOnUiThread {
                            tvStatus.text = "🎵 Đang phát: $sound"
                        }
                        playSound(sound, volume, duration)
                    }

                    "stop" -> {
                        stopSound()
                        runOnUiThread {
                            tvStatus.text = "⏹️ Đã dừng theo lệnh"
                        }
                    }

                    "speak" -> {
                        val text = data.optString("text", "")
                        Log.d("Socket", "🗣️ TTS: $text")
                        runOnUiThread {
                            tvStatus.text = "🗣️ Nhận TTS: $text"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Socket", "Lỗi xử lý lệnh: ${e.message}")
            }
        }
    }

    private fun disconnect() {
        try {
            socket?.disconnect()
            socket?.off()
            socket = null
        } catch (e: Exception) {
            Log.e("Socket", "Lỗi disconnect: ${e.message}")
        }
        stopSound()
        tvStatus.text = "⚪ Đã ngắt kết nối"
    }

    private fun playSound(soundType: String, volume: Float, durationSeconds: Int) {
        stopSound()

        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val newVolume = (maxVolume * volume).toInt().coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)

            val uri = when (soundType) {
                "alarm" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                "notification" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                "siren", "music" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setDataSource(this@MainActivity, uri)
                isLooping = durationSeconds > 0
                prepare()
                start()
            }

            vibrate(500)

            if (durationSeconds > 0) {
                handler.postDelayed({
                    stopSound()
                }, durationSeconds * 1000L)
            }

            Log.d("Sound", "▶️ Đang phát: $soundType, ${durationSeconds}s")

        } catch (e: Exception) {
            Log.e("Sound", "❌ Lỗi phát: ${e.message}")
        }
    }

    private fun stopSound() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("Sound", "Lỗi stop: ${e.message}")
        }
    }

    private fun vibrate(milliseconds: Long) {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(milliseconds)
            }
        } catch (e: Exception) {
            Log.e("Vibrate", "Lỗi: ${e.message}")
        }
    }
}
