package com.tatsuya.babymonitor.ui.live

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.tatsuya.babymonitor.R
import com.tatsuya.babymonitor.data.http.HttpPostMultiPart
import com.tatsuya.babymonitor.databinding.FragmentLive2Binding
import com.tatsuya.babymonitor.ui.zoom.ZoomGestureListener
import com.tatsuya.babymonitor.ui.zoom.ZoomScaleGestureListener
import java.io.File
import java.io.IOException

class Live2Fragment : Fragment(), SurfaceHolder.Callback {

    private var _binding: FragmentLive2Binding? = null
    private val viewBinding get() = _binding!!

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var surfaceHolder: SurfaceHolder

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var httpMultiPart: HttpPostMultiPart
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLive2Binding.inflate(inflater, container, false)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        viewBinding.mute.setOnClickListener {
            val isMute = sharedPreferences.getBoolean(getString(R.string.isMute_key), false)
            toggleVolume(!isMute)
        }

        httpMultiPart = HttpPostMultiPart(requireActivity().applicationContext)
        viewBinding.capture.setOnClickListener {
            capture(requireContext(), viewBinding.surfaceView)
        }

        val zoomView = viewBinding.surfaceView
        gestureDetector = GestureDetectorCompat(requireContext(), ZoomGestureListener(zoomView))
        scaleGestureDetector = ScaleGestureDetector(requireContext(), ZoomScaleGestureListener(zoomView))

        return viewBinding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        surfaceHolder = viewBinding.surfaceView.holder
        surfaceHolder.addCallback(this)

        viewBinding.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            scaleGestureDetector.onTouchEvent(event)
            true
        }
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initializePlayer() {
        // 以前のMediaPlayerインスタンスを解放
        releasePlayer()

        mediaPlayer = MediaPlayer().apply {
            // SharedPreferencesからURLを取得
            val url = sharedPreferences.getString(getString(R.string.live_url_key), "rtsp://192.168.68.19:8554/stream1")!!
            try {
                setDataSource(url)
                setDisplay(surfaceHolder)

                setOnPreparedListener {
                    it.start()
                    val isMute = sharedPreferences.getBoolean(getString(R.string.isMute_key), false)
                    toggleVolume(isMute)
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("Live2Fragment", "MediaPlayer Error: what=$what, extra=$extra")
                    Toast.makeText(context, "再生エラーが発生しました。", Toast.LENGTH_SHORT).show()
                    true
                }

                prepareAsync()
            } catch (e: IOException) {
                Log.e("Live2Fragment", "MediaPlayer setDataSource failed", e)
                Toast.makeText(context, "ストリームの準備に失敗しました。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun toggleVolume(isMute: Boolean) {
        val volume = if (isMute) 0f else 1f
        mediaPlayer?.setVolume(volume, volume)
        sharedPreferences.edit().putBoolean(getString(R.string.isMute_key), isMute).apply()
        val image = if (isMute) R.drawable.ic_volume_off else R.drawable.ic_volume_on
        viewBinding.mute.setImageResource(image)
    }

    private fun capture(context: Context, view: SurfaceView) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            view,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    val file = File(context.filesDir, "temp.png")
                    file.outputStream().use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    postData(file)
                    Log.d("Live2Fragment", "Capture success: ${file.absolutePath}")
                } else {
                    Toast.makeText(context, "キャプチャに失敗しました", Toast.LENGTH_SHORT).show()
                }
            },
            Handler(Looper.getMainLooper())
        )
    }

    private fun postData(file: File) {
        val filePart = mapOf("file1" to file)
        val stringPart = mapOf("payload_json" to """
{
    "content": "現在の${getText(R.string.baby_name)}の様子",
    "embeds": [
    {
        "title": "",
        "image": {
            "url": "attachment://${file.name}"
        }
    }]
}
""")
        val url = sharedPreferences.getString(getString(R.string.webhook_url_key), "")!!
        httpMultiPart.doUpload(url, filePart, stringPart)
    }

    // --- SurfaceHolder.Callbackの実装 ---
    override fun surfaceCreated(holder: SurfaceHolder) {
        initializePlayer()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Do nothing
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releasePlayer()
    }
}