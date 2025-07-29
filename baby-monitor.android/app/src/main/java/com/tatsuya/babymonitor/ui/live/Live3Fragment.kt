package com.tatsuya.babymonitor.ui.live

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.tatsuya.babymonitor.databinding.FragmentLive3Binding

@OptIn(UnstableApi::class)
class Live3Fragment : Fragment() {

    private var _binding: FragmentLive3Binding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private val rtspUrl = "rtsp://192.168.68.19:8554/stream1"

    private var playWhenReady = true
    private var mediaItemIndex = 0
    private var playbackPosition = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLive3Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        initializePlayer()
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
        player = ExoPlayer.Builder(requireContext())
            .build()
            .also { exoPlayer ->
                binding.playerView.player = exoPlayer
exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ALL
                val mediaItem = MediaItem.Builder()
                    .setUri(rtspUrl)
//                    .setMimeType(MimeTypes.APPLICATION_RTSP)
//                    .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
                    .build()
                val mediaSource = RtspMediaSource.Factory()
                    .setDebugLoggingEnabled(true)
                    .setTimeoutMs(30000)
//                    .createMediaSource(MediaItem.fromUri(rtspUrl))
                    .createMediaSource(mediaItem)

                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.seekTo(mediaItemIndex, playbackPosition)
                exoPlayer.prepare()
            }
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            playbackPosition = exoPlayer.currentPosition
            mediaItemIndex = exoPlayer.currentMediaItemIndex
            playWhenReady = exoPlayer.playWhenReady
            exoPlayer.release()
        }
        player = null
    }
}