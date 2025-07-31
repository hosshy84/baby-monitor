package com.tatsuya.babymonitor.ui.live

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import com.tatsuya.babymonitor.R
import com.tatsuya.babymonitor.data.http.HttpPostMultiPart
import com.tatsuya.babymonitor.databinding.FragmentLiveBinding
import com.tatsuya.babymonitor.ui.zoom.ZoomGestureListener
import com.tatsuya.babymonitor.ui.zoom.ZoomScaleGestureListener
import java.io.File


class LiveFragment : Fragment() {

    companion object {
        fun newInstance() = LiveFragment()
    }

    private lateinit var viewModel: LiveViewModel

    private var _binding: FragmentLiveBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val viewBinding get() = _binding!!

    private var player: Player? = null

    private var playWhenReady = true
    private var mediaItemIndex = 0
    private var playbackPosition = 0L

    private lateinit var sharedPreferences: SharedPreferences
    private var currentLocation = "" // SharedPreferences key for current location
    private lateinit var httpMultiPart: HttpPostMultiPart
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    @OptIn(UnstableApi::class)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this)[LiveViewModel::class.java]
        _binding = FragmentLiveBinding.inflate(inflater, container, false)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        viewBinding.mute.setOnClickListener { _ ->
            val isMute = sharedPreferences.getBoolean(getString(R.string.isMute_key), false)
            toggleVolume(!isMute)
        }
        httpMultiPart = HttpPostMultiPart(requireActivity().applicationContext)
        viewBinding.capture.setOnClickListener { _ -> capture(requireContext(), viewBinding.videoView.videoSurfaceView as SurfaceView) }
        viewBinding.locationSwitch.setOnClickListener { _ -> switchLocation() }
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
        initializePlayer()
    }

    public override fun onResume() {
        super.onResume()
        if (player == null) {
            initializePlayer()
        }
    }

    public override fun onPause() {
        super.onPause()
    }

    public override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        // Load current location from SharedPreferences
        currentLocation = sharedPreferences.getString(getString(R.string.current_live_location_key), getString(R.string.live_url_living_key))!!
        val url = sharedPreferences.getString(currentLocation, "")!!
        updateLocationIcon()
        val mediaItem = MediaItem.fromUri(url)
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

                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()
            }

        val isMute = sharedPreferences.getBoolean(getString(R.string.isMute_key), false)
        toggleVolume(isMute)
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

    @OptIn(UnstableApi::class) private fun toggleVolume(isMute: Boolean) {
        player.also { exoPlayer ->
            exoPlayer?.volume = if (isMute) 0f else 1f
            sharedPreferences
                .edit()
                .putBoolean(getString(R.string.isMute_key), isMute)
                .apply()
            val image = if (isMute) R.drawable.ic_volume_off else R.drawable.ic_volume_on
            viewBinding.mute.setImageResource(image)
        }
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
                    Log.d("test", file.absolutePath)
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
        val url = sharedPreferences.getString(getString(R.string.webhook_url_key), "")!!
        httpMultiPart.doUpload(url, filePart, stringPart)
    }

    private fun switchLocation() {
        // Switch between living and bedroom
        currentLocation = if (currentLocation == getString(R.string.live_url_living_key)) {
            getString(R.string.live_url_bedroom_key)
        } else {
            getString(R.string.live_url_living_key)
        }
        
        // Save current location to SharedPreferences
        sharedPreferences
            .edit()
            .putString(getString(R.string.current_live_location_key), currentLocation)
            .apply()
        
        // Update icon
        updateLocationIcon()
        
        // Restart player with new URL
        releasePlayer()
        initializePlayer()
    }
    
    private fun updateLocationIcon() {
        val iconResource = if (currentLocation == getString(R.string.live_url_living_key)) {
            R.drawable.ic_living
        } else {
            R.drawable.ic_bedroom
        }
        viewBinding.locationSwitch.setImageResource(iconResource)
    }

}