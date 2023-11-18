package com.example.babymonitor.ui.live

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.babymonitor.R

class LiveFragment : Fragment() {

    companion object {
        fun newInstance() = LiveFragment()
    }

    private lateinit var viewModel: LiveViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(this)[LiveViewModel::class.java]
        return inflater.inflate(R.layout.fragment_live, container, false)
    }

}