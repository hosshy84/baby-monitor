package com.tatsuya.babymonitor.ui.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableRow
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.tatsuya.babymonitor.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var _context: Context

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val profileViewModel =
            ViewModelProvider(this).get(ProfileViewModel::class.java)

        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        _context = requireContext()
        initTable()
//        val root: View = binding.root
//        val textView: TextView = binding.textProfile
//        profileViewModel.text.observe(viewLifecycleOwner) {
//            textView.text = it
//        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initTable() {
        val tableLayout = binding.anniversaryTable
        val row = TableRow(_context)
        val paddingInPixels = (5 * resources.displayMetrics.density).toInt()
        row.setPadding(paddingInPixels, paddingInPixels, paddingInPixels, paddingInPixels)

        val textView1 = TextView(_context)
        textView1.text = "test"
        val textViewParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT)
        textView1.layoutParams = textViewParams
//        textView1.gravity = Gravity.LEFT
        row.addView(textView1)

        val textView2 = TextView(_context)
        textView2.text = "test"
        textView2.layoutParams = textViewParams
//        textView2.gravity = Gravity.LEFT
        row.addView(textView2)

        val textView3 = TextView(_context)
        textView3.text = "test"
        textView3.layoutParams = textViewParams
//        textView3.gravity = Gravity.LEFT
        row.addView(textView3)

        // 表に追加
        tableLayout.addView(row)
    }
}