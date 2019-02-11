package com.priyankvasa.android.cameraviewex_sample.camera

import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SpinnerAdapter
import com.priyankvasa.android.cameraviewex.AspectRatio
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

    val configListener: ConfigListener by lazy { targetFragment as ConfigListener }

    override fun show(manager: FragmentManager, tag: String?) {
        super.show(manager, tag)
        manager.executePendingTransactions()
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Dialog_Settings)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
            window?.decorView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_dialog_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupView()
    }

    private fun setupView() {
        spinnerAspectRatio.adapter = spinnerAdapter
        spinnerAspectRatio.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val (x, y) = (spinnerAdapter.getItem(position) as? String)?.split(':') ?: return
                configListener.onNewAspectRatio(AspectRatio.of(x.toInt(), y.toInt()))
            }
        }
        buttonCancel.setOnClickListener { dialog.cancel() }
    }

    interface ConfigListener {

        fun onNewAspectRatio(aspectRatio: AspectRatio)
    }

    companion object {

        val TAG: String = SettingsDialogFragment::class.java.canonicalName
            ?: SettingsDialogFragment::class.java.name

        fun newInstance() = SettingsDialogFragment()
    }
}