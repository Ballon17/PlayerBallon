package com.ballon.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.*
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.ballon.player.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@UnstableApi
class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var player: ExoPlayer? = null
    private var isLandscape = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }

        // روابط التواصل الاجتماعي الخاصة بك
        binding.navView.setNavigationItemSelectedListener { item ->
            val url = when (item.itemId) {
                R.id.nav_telegram -> "https://t.me/ballontvlive"
                R.id.nav_facebook -> "https://www.facebook.com/share/1GBNvctQod/"
                R.id.nav_whatsapp -> "https://whatsapp.com/channel/0029VbChv7kGpLHS0Rxl9k2T"
                R.id.nav_youtube -> "https://youtube.com/@fhmhsn?si=7LL7TQcP5DS9ep9z"
                R.id.nav_blog -> "https://easy-money-tips-app-reviews2026.blogspot.com/?m=1"
                else -> ""
            }
            if (url.isNotEmpty()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            binding.drawerLayout.closeDrawers()
            true
        }

        binding.btnPlay.setOnClickListener { startPlay() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.playerContainer.visibility == View.VISIBLE) stopAndBack()
                else finish()
            }
        })
    }

    private fun startPlay() {
        val url = binding.etUrl.text.toString().trim()
        val drmKey = binding.etDrmKey.text.toString().trim()
        val userAgent = if (binding.etUserAgent.text.isEmpty()) "PlayerBallon/1.0" else binding.etUserAgent.text.toString()

        if (url.isEmpty()) return

        // الدخول في وضع ملء الشاشة
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.toolbar.visibility = View.GONE
        hideSystemUI()

        val headers = mutableMapOf<String, String>()
        headers["Referer"] = binding.etReferrer.text.toString()
        headers["Cookie"] = binding.etCookie.text.toString()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(headers.filterValues { it.isNotEmpty() })

        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)

        // حل Clearkey المتقدم
        if (drmKey.contains(":")) {
            val parts = drmKey.split(":")
            val jwk = JSONObject().apply {
                put("keys", JSONArray().put(JSONObject().apply {
                    put("kty", "oct")
                    put("kid", base64UrlEncode(parts[0]))
                    put("k", base64UrlEncode(parts[1]))
                }))
                put("type", "temporary")
            }.toString().toByteArray()

            val drmCallback = object : MediaDrmCallback {
                override fun executeProvisionRequest(u: UUID, r: ExoMediaDrm.ProvisionRequest) = ByteArray(0)
                override fun executeKeyRequest(u: UUID, r: ExoMediaDrm.KeyRequest) = jwk
            }
            mediaSourceFactory.setDrmSessionManagerProvider { 
                DefaultDrmSessionManager.Builder().setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER).build(drmCallback)
            }
        }

        player = ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build()
        binding.playerView.player = player
        
        // إعداد أزرار المشغل المخصصة
        setupPlayerButtons()

        binding.inputScrollView.visibility = View.GONE
        binding.playerContainer.visibility = View.VISIBLE

        player?.setMediaItem(MediaItem.fromUri(url))
        player?.prepare()
        player?.play()
    }

    private fun setupPlayerButtons() {
        val btnAspect = binding.playerView.findViewById<ImageButton>(R.id.btnAspectRatio)
        val btnRotate = binding.playerView.findViewById<ImageButton>(R.id.btnRotate)
        val btnAudio = binding.playerView.findViewById<ImageButton>(R.id.btnAudio)

        btnAspect?.setOnClickListener {
            binding.playerView.resizeMode = if (binding.playerView.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) 
                AspectRatioFrameLayout.RESIZE_MODE_FILL else AspectRatioFrameLayout.RESIZE_MODE_FIT
            Toast.makeText(this, "Aspect Ratio Toggled", Toast.LENGTH_SHORT).show()
        }

        btnRotate?.setOnClickListener {
            isLandscape = !isLandscape
            requestedOrientation = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }

        btnAudio?.setOnClickListener {
            // فتح قائمة اختيار الصوت (تتطلب كود إضافي للمسارات، لكن الزر جاهز)
            Toast.makeText(this, "Audio Track Selection", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAndBack() {
        player?.release()
        player = null
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding.toolbar.visibility = View.VISIBLE
        showSystemUI()
        binding.playerContainer.visibility = View.GONE
        binding.inputScrollView.visibility = View.VISIBLE
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun showSystemUI() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun base64UrlEncode(hex: String): String {
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}