package com.example.babymonitor.ui.monitor

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.ScaleGestureDetector
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import com.example.babymonitor.R
import com.example.babymonitor.data.http.HttpPostMultiPart
import com.example.babymonitor.databinding.FragmentMonitorBinding
import com.example.babymonitor.ui.zoom.ZoomGestureListener
import com.example.babymonitor.ui.zoom.ZoomScaleGestureListener
import java.io.File


class MonitorFragment : Fragment() {

    companion object {
        fun newInstance() = MonitorFragment()
    }

    private lateinit var viewModel: MonitorViewModel

    private var _binding: FragmentMonitorBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val viewBinding get() = _binding!!

    private var player: Player? = null

    private var playWhenReady = true
    private var mediaItemIndex = 0
    private var playbackPosition = 0L
    private var currentVolume: Float? = null

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    @OptIn(UnstableApi::class)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(this)[MonitorViewModel::class.java]
        _binding = FragmentMonitorBinding.inflate(inflater, container, false)
        viewBinding.mute.setOnClickListener { _ -> toggleVolume() }
        viewBinding.capture.setOnClickListener { _ -> capture(requireContext(), viewBinding.videoView.videoSurfaceView as SurfaceView) }
        val zoomView = viewBinding.videoView
        gestureDetector = GestureDetectorCompat(requireContext(), ZoomGestureListener(zoomView))
        scaleGestureDetector = ScaleGestureDetector(requireContext(), ZoomScaleGestureListener(zoomView))
        return viewBinding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewBinding.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            scaleGestureDetector.onTouchEvent(event)
            true
        }
    }

    public override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT > 23) {
            initializePlayer()
        }
    }

    public override fun onResume() {
        super.onResume()
        loadSettings()
//        hideSystemUi()
        if (Build.VERSION.SDK_INT <= 23 || player == null) {
            initializePlayer()
        }
    }

    public override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT <= 23) {
            releasePlayer()
        }
    }

    public override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT > 23) {
            releasePlayer()
        }
    }

    private fun initializePlayer() {
        // ExoPlayer implements the Player interface
        player = ExoPlayer.Builder(requireContext())
            .build()
            .also { exoPlayer ->
                viewBinding.videoView.player = exoPlayer
                // Update the track selection parameters to only pick standard definition tracks
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSizeSd()
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(getString(R.string.media_url_dash))
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
                exoPlayer.setMediaItems(listOf(mediaItem), mediaItemIndex, playbackPosition)
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.prepare()
            }

        if (currentVolume == null) {
            setVolumeImage(true)
        } else {
            player.also { exoPlayer -> exoPlayer?.volume = 0f }
            setVolumeImage(false)
        }
    }

    private fun releasePlayer() {
        player?.let { player ->
            playbackPosition = player.currentPosition
            mediaItemIndex = player.currentMediaItemIndex
            playWhenReady = player.playWhenReady
            player.release()
        }
        player = null
    }

    private fun loadSettings() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    @SuppressLint("InlinedApi")
    private fun hideSystemUi() {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, viewBinding.videoView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @OptIn(UnstableApi::class) private fun toggleVolume() {
        player.also { exoPlayer ->
            if (currentVolume == null) {
                currentVolume = exoPlayer?.volume
                exoPlayer?.volume = 0f
                setVolumeImage(false)
            } else {
                exoPlayer?.volume = currentVolume!!
                currentVolume = null
                setVolumeImage(true)
            }
        }
    }

    private fun setVolumeImage(isOn: Boolean) {
        val image = if (isOn) R.drawable.ic_volume_on else R.drawable.ic_volume_off
        viewBinding.mute.setImageResource(image)
    }

    private fun capture(context: Context, view: SurfaceView) {
        val bitmap = Bitmap.createBitmap(
            view.width,
            view.height,
            Bitmap.Config.ARGB_8888
        )
        PixelCopy.request(
            view,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    val file = File(context.filesDir, "temp.png")
                    val outputStream = file.outputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.close()
                    postDate(file)
                    Log.d("test", "${file.absolutePath}")
                } else {
                    Toast.makeText(context, "失敗しました", Toast.LENGTH_SHORT).show()
                }
            },
            Handler(Looper.myLooper()!!)
        )
    }

    private fun postDate(file: File) {
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
        val httpMultiPart = HttpPostMultiPart(requireActivity().applicationContext)
        val url = sharedPreferences.getString("webhook_url", "")!!
        httpMultiPart.doUpload(url, filePart, stringPart)
    }

}