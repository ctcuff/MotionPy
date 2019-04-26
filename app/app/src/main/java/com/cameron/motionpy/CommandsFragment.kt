package com.cameron.motionpy

import android.os.Bundle
import android.support.design.widget.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.bottom_sheet_view.view.*

class CommandsFragment : BottomSheetDialogFragment() {

    private lateinit var clickListener: (String) -> Unit

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_view, container)

        with(view) {
            tv_start.setOnClickListener { clickListener(Commands.START) }
            tv_pause.setOnClickListener { clickListener(Commands.PAUSE) }
            tv_stop.setOnClickListener { clickListener(Commands.STOP) }
            tv_power_off.setOnClickListener { clickListener(Commands.POWER_OFF) }
        }

        return view
    }

    fun setClickListener(clickListener: (String) -> Unit) {
        this.clickListener = clickListener
    }
}