package com.creoit.docscanner.ui

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.*
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.creoit.docscanner.R
import com.creoit.docscanner.custom.BaseFragment
import com.creoit.docscanner.custom.ImageCropLayout
import com.creoit.docscanner.utils.Document
import com.creoit.docscanner.utils.Rectangle
import com.creoit.docscanner.utils.getDocument
import kotlinx.android.synthetic.main.fragment_crop_image.*
import org.jetbrains.anko.dip
import org.koin.androidx.viewmodel.ext.android.sharedViewModel

class CropImageFragment : BaseFragment() {
    private var listener: OnFragmentInteractionListener? = null

    private val position by lazy {
        arguments?.getInt(ARG_DOC_POS)
    }
    private val documentVM by sharedViewModel<DocumentVM>()

    private var document: Document? = null

    override fun getLayoutResId() = R.layout.fragment_crop_image

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

        document?.let { document1 ->
            val bitmap = BitmapFactory.decodeStream(context?.openFileInput(document1.image))
            val set = ConstraintSet()
            set.clone(clParent)
            set.setDimensionRatio(R.id.ivPreview, "${bitmap.width}:${bitmap.height}")
            set.applyTo(clParent)
            ivPreview.post {
                ivPreview.setImageBitmap(bitmap)
                val widthVal = ivPreview.width.toDouble() / bitmap.width.toDouble()
                val heightVal = ivPreview.height.toDouble() / bitmap.height.toDouble()
                document1.rectangle.let {
                    clPreview.setRectangle(
                        Rectangle(
                            topLeft = Point((it.topLeft.x.toDouble() * widthVal).toInt(), (it.topLeft.y.toDouble() * heightVal).toInt()),
                            bottomLeft = Point((it.bottomLeft.x.toDouble() * widthVal).toInt(), (it.bottomLeft.y.toDouble() * heightVal).toInt()),
                            bottomRight = Point((it.bottomRight.x.toDouble() * widthVal).toInt(), (it.bottomRight.y.toDouble() * heightVal).toInt()),
                            topRight = Point((it.topRight.x.toDouble() * widthVal).toInt(), (it.topRight.y.toDouble() * heightVal).toInt())
                        )
                    )
                }
                clPreview.setOnPointChangeListener(object :
                    ImageCropLayout.Companion.OnPointChangeListener {
                    override fun onMove(x: Int, y: Int): Boolean {

                        val loc: IntArray = intArrayOf(0,0)
                        ivPreview.getLocationOnScreen(loc)
                        val viewX = loc[0]
                        val viewY=  loc[1]
                        return if(x in viewX .. viewX + ivPreview.width && y in viewY .. viewY + ivPreview.height) {
                            set.clone(clParent)
                            if (x > ivPreview.width / 2) {
                                set.clear(R.id.clMagnifierLayout, ConstraintSet.END)
                                set.connect(
                                    R.id.clMagnifierLayout,
                                    ConstraintSet.START,
                                    R.id.clParent,
                                    ConstraintSet.START,
                                    0
                                )
                            } else {
                                set.clear(R.id.clMagnifierLayout, ConstraintSet.START)
                                set.connect(
                                    R.id.clMagnifierLayout,
                                    ConstraintSet.END,
                                    R.id.clParent,
                                    ConstraintSet.END,
                                    0
                                )
                            }
                            set.applyTo(clParent)
                            showMagnifier(x, y)
                            true
                        } else {
                            false
                        }
                    }

                    override fun onStop() {
                        ivMagnifier.visibility = View.GONE
                    }

                })

                clPreview.invalidate()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == R.id.menu_save) {
            document?.let { document ->

                document.rectangle = clPreview.getRectangle()
                val bitmap = BitmapFactory.decodeStream(context?.openFileInput(document.image))
                val widthVal = bitmap.width.toDouble() / ivPreview.width.toDouble()
                val heightVal = bitmap.height.toDouble() / ivPreview.height.toDouble()
                document.rectangle.toArrayList().forEach {
                    it.x = (it.x.toDouble() * widthVal).toInt()
                    it.y = (it.y.toDouble() * heightVal).toInt()
                }
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

    private fun showMagnifier(x: Int, y: Int) {
        ivMagnifier.visibility = View.VISIBLE
        val loc: IntArray = intArrayOf(0,0)
        ivPreview.getLocationOnScreen(loc)
        val width = ivPreview.width + loc[0]
        val height = ivPreview.height + loc[1]
        val rect = Rect(
            if(x < 100) 0 else if(x > width - 100) width - 200 else x - 100,
            if(y < 100) 0 else if(y > height - 100) height - 200 else y - 100,
            if(x > width - 100) width else if(x < 100) 200 else x + 100,
            if(y > height - 100) height else if(y < 100) 200 else y + 100
        )
        val bitmap = Bitmap.createBitmap(context!!.dip(200), context!!.dip(200), Bitmap.Config.ARGB_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.window?.let {
                PixelCopy.request(it, rect, bitmap, {
                    ivMagnifier.setImageBitmap(getCircledBitmap(bitmap))
                }, Handler())
            }
        }
    }

    private fun getCircledBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle((bitmap.width / 2).toFloat(), (bitmap.height / 2).toFloat(), (bitmap.width / 2).toFloat(), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        val boarderPaint = Paint()
        boarderPaint.color = ContextCompat.getColor(context!!, R.color.white)
        boarderPaint.strokeWidth = 2f
        boarderPaint.style = Paint.Style.STROKE
        canvas.drawCircle((bitmap.width / 2).toFloat(), (bitmap.height / 2).toFloat(), (bitmap.width / 2) - 2.toFloat(), boarderPaint)

        return output
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
