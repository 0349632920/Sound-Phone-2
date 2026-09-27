package com.example.sound

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

class MainActivity : AppCompatActivity() {

    // ⚠️ ĐỔI THÀNH IP SERVER CỦA BẠN
    private val serverUrl = "http://192.168.1.100:5000"

    private var socket: Socket? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Tạo giao diện bằng code (không cần file XML layout)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 100, 50, 50)
        }

        val title = TextView(this).apply {
            text = "🔊 Remote Sound"
            textSize = 24f
        }

        tvStatus = TextView(this).apply {
            text = "Chưa kết nối"
            textSize = 16f
            setPadding(0, 40, 0, 40)
        }

        val tvDevice = TextView(this).apply {
            text = "Thiết bị: ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}"
            textSize = 14f
            setPadding(0, 0, 0, 40)
        }

        val btnConnect = Button(this).apply {
            text = "🔗 Kết nối Server"
            setOnClickListener { connect() }
        }

        val btnDisconnect = Button(this).apply {
            text = "🔌 Ngắt kết nối"
            setOnClickListener { disconnect() }
        }

        val btnTest = Button(this).apply {
            text = "🔔 Test âm thanh"
            setOnClickListener { playSound("notification", 1.0f, 3) }
        }

        layout.addView(title)
        layout.addView(tvDevice)
        layout.addView(tvStatus)
        layout.addView(btnConnect)
        layout.addView(btnDisconnect)
        layout.addView(btnTest)

        setContentView(layout)
    }

    private fun connect() {
        try {
            socket = IO.socket(serverUrl)
            
            socket?.on(Socket.EVENT_CONNECT) {
                runOnUiThread { tvStatus.text = "✅ Đã kết nối" }
                Log.d("Socket", "Đã kết nối")
                
                // Đăng ký tên thiết bị
                val reg = JSONObject().apply {
                    put("name", Build.MODEL)
                }
                socket?.emit("register", reg)
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                runOnUiThread { tvStatus.text = "❌ Mất kết nối" }
            }

            socket?.on("command") { args ->
                try {
                    val data = args[0] as JSONObject
                    val action = data.optString("action", "")
                    
                    when (action) {
                        "play" -> {
                            val sound = data.optString("sound", "beep")
                            val volume = data.optDouble("volume", 1.0).toFloat()
                            val duration = data.optInt("duration", 5)
                            
                            runOnUiThread { 
                                tvStatus.text = "🎵 Đang phát: $sound" 
                            }
                            playSound(sound, volume, duration)
                        }
                        "stop" -> {
                            stopSound()
                            runOnUiThread { tvStatus.text = "⏹️ Đã dừng" }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Socket", "Lỗi: ${e.message}")
                }
            }

            socket?.connect()
            tvStatus.text = "Đang kết nối..."
        } catch (e: URISyntaxException) {
            tvStatus.text = "❌ URL sai: ${e.message}"
        }
    }

    private fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        stopSound()
        tvStatus.text = "Đã ngắt kết nối"
    }

    private fun playSound(soundType: String, volume: Float, durationSeconds: Int) {
        stopSound()
        try {
            // Set âm lượng
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val newVolume = (maxVolume * volume).toInt().coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)

            // Chọn âm thanh
            val uri = when (soundType) {
                "alarm" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                "notification" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                "siren", "music" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setDataSource(this@MainActivity, uri)
                isLooping = durationSeconds > 0
                prepare()
                start()
            }

            vibrate(1000)

            // Tự dừng sau duration
            if (durationSeconds > 0) {
                android.os.Handler(mainLooper).postDelayed({
                    stopSound()
                }, durationSeconds * 1000L)
            }
        } catch (e: Exception) {
            Log.e("Sound", "Lỗi phát: ${e.message}")
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

    private fun vibrate(ms: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as android.os.VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        } catch (e: Exception) {
            Log.e("Vibrate", "Lỗi: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}
