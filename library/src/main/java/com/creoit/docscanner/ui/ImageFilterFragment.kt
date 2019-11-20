package com.creoit.docscanner.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.SeekBar
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.creoit.docscanner.R
import com.creoit.docscanner.custom.BaseFragment
import com.creoit.docscanner.utils.Document
import com.creoit.docscanner.utils.getDocument
import kotlinx.android.synthetic.main.fragment_image_filter.*
import org.koin.androidx.viewmodel.ext.android.sharedViewModel

class ImageFilterFragment : BaseFragment() {

    private var listener: OnFragmentInteractionListener? = null

    private val position by lazy {
        arguments?.getInt(ARG_DOC_POS)
    }
    private val documentVM by sharedViewModel<DocumentVM>()

    private var document: Document? = null

    override fun getLayoutResId() = R.layout.fragment_image_filter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launchWhenResumed {
            initViews()
        }
    }

    private fun initViews() {
        document = documentVM.documentList[position ?: 0]
        document?.let { doc ->
            ivPreview.setImageBitmap(doc.croppedImage)
            sbContrast.progress = doc.contrast.toInt()
            sbBrightness.progress = doc.brightness.toInt()
        }

        bottomAppBar.setOnNavigationItemSelectedListener {
            document?.isOriginal = (it.itemId == R.id.menu_original)
            document?.brightness = 0.0
            document?.contrast = 1.0
            sbContrast.progress = 1
            sbBrightness.progress = 0
            refreshImage()
            true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sbContrast.min = 1
        }
        sbContrast.max = 10
        sbContrast.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val contrast = (seekBar?.progress ?: 0).toDouble()
                    document?.contrast = if(contrast == 0.0) 1.0 else contrast
                    refreshImage()
                }

            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sbBrightness.min = -127
        }
        sbBrightness.max = 127
        sbBrightness.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    document?.brightness = (seekBar?.progress ?: 0).toDouble()
//                    document?.brightness = if(brightness == 0.0) 1.0 else brightness
//                    document?.brightness = (((seekBar?.progress ?: 0) - 50) * 254 / 100).toDouble()
                    refreshImage()
                }

            }
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == R.id.menu_save) {
            document?.let { document ->
                val bitmap = BitmapFactory.decodeStream(context?.openFileInput(document.image))
                document.croppedImage = getDocument(bitmap, document.rectangle, document.isOriginal, document.contrast, document.brightness)
                documentVM.documentList[position ?: 0] = document
                findNavController().popBackStack()
            }
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.save_menu, menu)
        menu.findItem(R.id.menu_save)?.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun refreshImage() {
        document?.let {
            val bitmap = BitmapFactory.decodeStream(context?.openFileInput(it.image))
            val docImage = getDocument(bitmap, it.rectangle, it.isOriginal, it.contrast, it.brightness )
            ivPreview.setImageBitmap(docImage)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object{
        const val ARG_DOC_POS = "doc_position"
    }
}
