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

    // ============================================================
    // ⚠️ CẤU HÌNH - ĐỔI URL NÀY THÀNH URL NGROK CỦA BẠN
    // ============================================================
    private val serverUrl = "https://amplifier-rake-overjoyed.ngrok-free.dev"

    // ============================================================
    // BIẾN TOÀN CỤC
    // ============================================================
    private var socket: Socket? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private lateinit var tvStatus: TextView
    private lateinit var tvDevice: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnTest: Button
    private lateinit var btnStop: Button

    private val handler = Handler(Looper.getMainLooper())

    // ============================================================
    // LIFECYCLE
    // ============================================================
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

    // ============================================================
    // XÂY DỰNG GIAO DIỆN (bằng code, không cần XML)
    // ============================================================
    private fun buildUI() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 80, 50, 80)
        }

        // Tiêu đề
        val title = TextView(this).apply {
            text = "🔊 Remote Sound"
            textSize = 26f
            setPadding(0, 0, 0, 30)
        }

        // Thông tin thiết bị
        tvDevice = TextView(this).apply {
            text = "📱 Thiết bị: ${Build.MODEL}\n" +
                   "🤖 Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            textSize = 14f
            setPadding(0, 0, 0, 30)
        }

        // Trạng thái
        tvStatus = TextView(this).apply {
            text = "⚪ Chưa kết nối"
            textSize = 16f
            setPadding(0, 30, 0, 30)
        }

        // Nút Connect
        btnConnect = Button(this).apply {
            text = "🔗 Kết nối Server"
            textSize = 16f
            setOnClickListener { connect() }
        }

        // Nút Disconnect
        btnDisconnect = Button(this).apply {
            text = "🔌 Ngắt kết nối"
            textSize = 16f
            setOnClickListener { disconnect() }
        }

        // Nút Test offline
        btnTest = Button(this).apply {
            text = "🔔 Test âm thanh (offline)"
            textSize = 16f
            setOnClickListener {
                playSound("notification", 1.0f, 3)
                tvStatus.text = "🔔 Đang test âm thanh..."
            }
        }

        // Nút Stop
        btnStop = Button(this).apply {
            text = "⏹️ Dừng âm thanh"
            textSize = 16f
            setOnClickListener {
                stopSound()
                tvStatus.text = "⏹️ Đã dừng"
            }
        }

        // Hướng dẫn
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

        // Thêm vào layout
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

    // ============================================================
    // KẾT NỐI SOCKET.IO (có bypass Ngrok)
    // ============================================================
    private fun connect() {
        if (socket?.connected() == true) {
            tvStatus.text = "ℹ️ Đã kết nối rồi"
            return
        }

        try {
            tvStatus.text = "🔄 Đang kết nối..."

            // ⭐ CẤU HÌNH SOCKET.IO + BYPASS NGROK
            val opts = IO.Options().apply {
                reconnection = true
                reconnectionDelay = 3000
                reconnectionAttempts = 10
                timeout = 15000
                
                // ⭐ Bypass trang cảnh báo Ngrok
                extraHeaders = mapOf(
                    "ngrok-skip-browser-warning" to "true",
                    "User-Agent" to "RemoteSoundApp/1.0"
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

    // ============================================================
    // LẮNG NGHE SỰ KIỆN SOCKET
    // ============================================================
    private fun setupSocketListeners() {
        socket?.on(Socket.EVENT_CONNECT) {
            Log.d("Socket", "✅ Đã kết nối server")
            runOnUiThread {
                tvStatus.text = "✅ Đã kết nối"
            }

            // Đăng ký tên thiết bị với server
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

        socket?.on(Socket.EVENT_RECONNECT) {
            Log.d("Socket", "🔄 Đã kết nối lại")
            runOnUiThread {
                tvStatus.text = "✅ Đã kết nối lại"
            }
        }

        // ⭐ Nhận lệnh từ server
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
                            tvStatus.text = "🎵 Đang phát: $sound (vol=${(volume * 100).toInt()}%)"
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
                        // (Tùy chọn) Thêm TextToSpeech ở đây
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

    // ============================================================
    // NGẮT KẾT NỐI
    // ============================================================
    private fun disconnect() {
        try {
            socket?.disconnect()
            socket?.off()
            socket = null
            Log.d("Socket", "🔌 Đã ngắt kết nối")
        } catch (e: Exception) {
            Log.e("Socket", "Lỗi disconnect: ${e.message}")
        }
        stopSound()
        runOnUiThread {
            tvStatus.text = "⚪ Đã ngắt kết nối"
        }
    }

    // ============================================================
    // PHÁT ÂM THANH
    // ============================================================
    private fun playSound(soundType: String, volume: Float, durationSeconds: Int) {
        // Dừng cái cũ trước
        stopSound()

        try {
            // === Set âm lượng ===
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val newVolume = (maxVolume * volume).toInt().coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
            Log.d("Sound", "🔊 Set volume: $newVolume / $maxVolume")

            // === Chọn loại âm thanh ===
            val uri = when (soundType) {
                "alarm" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                "notification" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                "siren", "music" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                "beep" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            // === Khởi tạo MediaPlayer ===
            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setDataSource(this@MainActivity, uri)
                isLooping = durationSeconds > 0
                prepare()
                start()
            }

            // === Rung kèm ===
            vibrate(500)

            // === Tự dừng sau N giây ===
            if (durationSeconds > 0) {
                handler.postDelayed({
                    stopSound()
                    runOnUiThread {
                        if (socket?.connected() == true) {
                            tvStatus.text = "✅ Đã kết nối"
                        }
                    }
                }, durationSeconds * 1000L)
            }

            Log.d("Sound", "▶️ Đang phát: $soundType, ${durationSeconds}s")

        } catch (e: Exception) {
            Log.e("Sound", "❌ Lỗi phát: ${e.message}")
            e.printStackTrace()
        }
    }

    // ============================================================
    // DỪNG ÂM THANH
    // ============================================================
    private fun stopSound() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            Log.d("Sound", "⏹️ Đã dừng")
        } catch (e: Exception) {
            Log.e("Sound", "Lỗi stop: ${e.message}")
        }
    }

    // ============================================================
    // RUNG
    // ============================================================
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
                    VibrationEffect.createOneShot(
                        milliseconds,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(milliseconds)
            }
            Log.d("Vibrate", "📳 Rung ${milliseconds}ms")
        } catch (e: Exception) {
            Log.e("Vibrate", "Lỗi: ${e.message}")
        }
    }
}
