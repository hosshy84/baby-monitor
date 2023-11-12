package com.example.babymonitor.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.babymonitor.databinding.FragmentHomeBinding
import com.github.niqdev.mjpeg.DisplayMode
import com.github.niqdev.mjpeg.Mjpeg
import com.github.niqdev.mjpeg.MjpegView

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

//        val textView: TextView = binding.textHome
//        homeViewModel.text.observe(viewLifecycleOwner) {
//            textView.text = it
//        }
        return root
    }

    override fun onResume() {
        super.onResume()
        loadIpCam()
    }

    override fun onPause() {
        super.onPause()
        binding.mjpegView.stopPlayback()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadIpCam() {
        val streamURL = "http://192.168.68.18:8080/?action=stream"
        val timeout = 100
        Mjpeg.newInstance()
            .open(streamURL, timeout)
            .subscribe({
                binding.mjpegView.setSource(it)
                binding.mjpegView.setDisplayMode(DisplayMode.BEST_FIT)
                binding.mjpegView.showFps(true)
            },
                {
                    Log.e("loadIpCam", it.toString())
//                    Toast.makeText(this, "Error: $it", Toast.LENGTH_LONG).show()
                })
    }
}