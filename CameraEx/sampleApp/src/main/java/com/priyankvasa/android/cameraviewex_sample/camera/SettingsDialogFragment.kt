package com.priyankvasa.android.cameraviewex_sample.camera

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.priyankvasa.android.cameraviewex.AspectRatio
import com.priyankvasa.android.cameraviewex_sample.R
import kotlinx.android.synthetic.main.fragment_dialog_settings.*

class SettingsDialogFragment : DialogFragment() {

    val configListener: ConfigListener by lazy { targetFragment as ConfigListener }

    private val aspectRatiosRepo: AspectRatiosRepository
        by lazy { targetFragment as AspectRatiosRepository }

    private val spinnerAdapter: SpinnerAdapter<AspectRatio> by lazy {

        SpinnerAdapter(
            context ?: throw NullPointerException("Null context!"),
            android.R.layout.simple_spinner_item,
            aspectRatiosRepo.supportedAspectRatios.toTypedArray()
        ).apply {
            // Specify the layout to use when the list of choices appears
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

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
        setupAspectRatioSpinner()
        buttonCancel.setOnClickListener { dialog.cancel() }
    }

    private fun setupAspectRatioSpinner(): Unit = with(spinnerAspectRatio) {

        adapter = spinnerAdapter

        setSelection(spinnerAdapter.getItemPosition(aspectRatiosRepo.currentAspectRatio))

        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                configListener.onNewAspectRatio(spinnerAdapter[position] ?: return)
            }
        }
    }

    private inner class SpinnerAdapter<T>(
        context: Context,
        @LayoutRes itemViewResource: Int,
        private val items: Array<out T>
    ) : ArrayAdapter<T>(context, itemViewResource, items) {

        operator fun get(position: Int): T? = getItem(position)

        fun getItemPosition(item: T): Int = items.indexOf(item)
    }

    interface ConfigListener {

        fun onNewAspectRatio(aspectRatio: AspectRatio)
    }

    companion object {

        val TAG: String =
            SettingsDialogFragment::class.java.run { canonicalName ?: name ?: simpleName }

        fun newInstance(): SettingsDialogFragment = SettingsDialogFragment()
    }
}