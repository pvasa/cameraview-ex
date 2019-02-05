package com.priyankvasa.android.cameraviewex_sample.camera

import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SpinnerAdapter
import com.priyankvasa.android.cameraviewex_sample.R
import kotlinx.android.synthetic.main.fragment_dialog_settings.*

class SettingsDialogFragment : DialogFragment() {

    private val spinnerAdapter: SpinnerAdapter by lazy {
        ArrayAdapter.createFromResource(
            context ?: throw NullPointerException("Null context!"),
            R.array.aspect_ratio_array,
            android.R.layout.simple_spinner_item
        ).apply {
            // Specify the layout to use when the list of choices appears
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Dialog_Settings)
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_dialog_settings, container, true)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupView()
    }

    private fun setupView() {
        spinnerAspectRatio.adapter = spinnerAdapter
        buttonCancel.setOnClickListener { dialog.cancel() }
    }

    companion object {

        val TAG: String = SettingsDialogFragment::class.java.canonicalName
            ?: SettingsDialogFragment::class.java.name

        fun newInstance() = SettingsDialogFragment()
    }
}