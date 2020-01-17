package com.creoit.docscanner.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.*
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.TintTypedArray
import androidx.camera.core.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.creoit.docscanner.R
import com.creoit.docscanner.custom.BaseFragment
import com.creoit.docscanner.utils.*
import com.creoit.docscanner.ui.ImageViewerFragment.Companion.ARG_DOC_POSITION
import id.zelory.compressor.Compressor
import kotlinx.android.synthetic.main.fragment_camera.*
import kotlinx.android.synthetic.main.fragment_camera.view.*
import kotlinx.android.synthetic.main.rv_item_docs.view.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.opencv.android.OpenCVLoader
import splitties.init.appCtx
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class CameraFragment : BaseFragment(), OnFrameChangeListener {
    private var listener: OnFragmentInteractionListener? = null

    private var lensFacing = CameraX.LensFacing.BACK
    private var points = ArrayList<Point>()
    private var bitmap: Bitmap? = null
    private var outputDirectory: File? = null

    private var autoFitPreviewBuilder: AutoFitPreviewBuilder? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private lateinit var metrics: DisplayMetrics
    private lateinit var screenSize: Size
    private lateinit var screenAspectRatio: Rational

    private lateinit var displayManager: DisplayManager

    private var isPaused = false

    private val IMMERSIVE_FLAG_TIMEOUT = 500L
    private val FLAGS_FULLSCREEN =
        View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = textureView?.let { view ->
            preview?.setTargetRotation(view.display.rotation)
            imageCapture?.setTargetRotation(view.display.rotation)
            imageAnalyzer?.setTargetRotation(view.display.rotation)

        } ?: Unit
    }

    private lateinit var rvAdapter: SimpleAdapter<Document, DocVH>
    private val documentVM by sharedViewModel<DocumentVM>()

    init {
        if (!OpenCVLoader.initDebug())
            Log.d("ERROR", "Unable to load OpenCV")
        else
            Log.d("SUCCESS", "OpenCV loaded")
    }

    override fun getLayoutResId() = R.layout.fragment_camera

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launchWhenStarted {
            initViews()
        }
    }

    private fun initViews() {

        rvAdapter = SimpleAdapter(
            items = documentVM.documentList,
            getLayoutId = { R.layout.rv_item_docs },
            vhBinder = { view, _ -> DocVH(view) },
            binder = { vh, doc -> vh.bind(doc) }
        )

        with(rvDocs) {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = rvAdapter
        }

        context?.let {
            outputDirectory = getOutputDirectory(it)
        }
        textureView?.post {
            //if (preview == null)
            if (!isPaused) {
                startCamera()
            }
        }

        btnSave.setOnClickListener {
            lifecycleScope.async {
                listener?.onSavePdf()
            }
        }

        displayManager = context?.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        if (::rvAdapter.isInitialized) {
            rvAdapter.notifyDataSetChanged()
            with(textureView) {
                postDelayed({
                    systemUiVisibility = FLAGS_FULLSCREEN
                }, IMMERSIVE_FLAG_TIMEOUT)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        CameraX.unbindAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onPause() {
        super.onPause()
        isPaused = true
    }

    private fun startCamera() {
        metrics = DisplayMetrics().also { textureView?.display?.getRealMetrics(it) }
        screenSize = Size(metrics.widthPixels, metrics.heightPixels)
        screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)

        val previewConfig = PreviewConfig.Builder().apply {
            setLensFacing(lensFacing)
            setTargetAspectRatio(screenAspectRatio)
            setTargetResolution(screenSize)
            activity?.windowManager?.defaultDisplay?.rotation?.let { setTargetRotation(it) }
        }.build()

        autoFitPreviewBuilder = AutoFitPreviewBuilder.build(previewConfig, textureView)
        preview = autoFitPreviewBuilder?.useCase

        val imageCaptureConfig = ImageCaptureConfig.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetResolution(screenSize)
            .setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)

        activity?.windowManager?.defaultDisplay?.rotation?.let {
            imageCaptureConfig.setTargetRotation(
                it
            )
        }

        //imageCapture = ImageCapture(imageCaptureConfig.build())

        ivCapture.setOnClickListener {
            lifecycleScope.launch {
                imageCapture?.takePicture(object : ImageCapture.OnImageCapturedListener() {
                    override fun onCaptureSuccess(image: ImageProxy?, rotationDegrees: Int) {

                        lifecycleScope.launch {
                            var rotation = rotationDegrees % 360
                            if (rotation < 0) {
                                rotation += 360
                            }
                            val previewBitmap = image?.decodeBitmap()?.rotate(rotation.toFloat())
                            super.onCaptureSuccess(image, rotationDegrees)
                            previewBitmap?.let {
                                val widthVal = it.width.toDouble() / bitmap!!.width.toDouble()
                                val heightVal =
                                    it.height.toDouble() / bitmap!!.height.toDouble()
                                points.forEach {
                                    it.x = (it.x.toDouble() * widthVal).toInt()
                                    it.y = (it.y.toDouble() * heightVal).toInt()
                                }
                                createImageFromBitmap(
                                    context!!,
                                    previewBitmap,
                                    rvAdapter.itemCount
                                )?.let {
                                    if (points.size == 4) {
                                        val rectangle = Rectangle(
                                            points[0], points[1], points[2], points[3]
                                        )
                                        val doc = getDocument(previewBitmap, rectangle)
                                        val document = Document(it, rectangle, doc)
                                        rvAdapter.addItem(document, notify = true)
                                        if (documentVM.isSinglePage) {

                                            val navOptions = NavOptions.Builder()
                                                .setPopUpTo(R.id.cameraFragment, true)
                                                .build()
                                            findNavController().navigate(
                                                R.id.action_cameraFragment_to_imageViewerFragment,
                                                bundleOf(),
                                                navOptions
                                            )
                                        }
                                    }

                                }
                            }
                        }
                    }
                })
            }
        }

        CameraX.bindToLifecycle(this, getAnalyzer(), preview)


    }

    private fun getAnalyzer(): ImageAnalysis {
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            val analyzerThread = HandlerThread("LuminosityAnalysis").apply { start() }
            setCallbackHandler(Handler(analyzerThread.looper))
            setImageReaderMode(
                ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE
            )
            setTargetAspectRatio(screenAspectRatio)
            setTargetResolution(screenSize)
            setTargetRotation(textureView.display.rotation)

            activity?.windowManager?.defaultDisplay?.rotation?.let { setTargetRotation(it) }
        }.build()

        return ImageAnalysis(analyzerConfig).apply {
            analyzer = ImageAnalyzer(this@CameraFragment)
            imageAnalyzer = this
        }
    }

    override fun onChange(bitmap: Bitmap, points: ArrayList<Point>) {
        this.bitmap = bitmap
        val size = autoFitPreviewBuilder?.viewFinderDimens ?: screenSize
        lifecycleScope.launchWhenResumed {
            val layoutParams = textureView.layoutParams as ConstraintLayout.LayoutParams
            val ratio = "${bitmap.width}:${bitmap.height}"
            if (layoutParams.dimensionRatio != ratio) {
                val set = ConstraintSet()
                set.clone(clCameraFragment)
                set.setDimensionRatio(R.id.textureView, ratio)
                set.applyTo(clCameraFragment)

                val imageCaptureConfig = ImageCaptureConfig.Builder()
                    .setTargetAspectRatio(Rational(bitmap.width, bitmap.height))
                    .setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)

                activity?.windowManager?.defaultDisplay?.rotation?.let {
                    imageCaptureConfig.setTargetRotation(
                        it
                    )
                }

                imageCapture = ImageCapture(imageCaptureConfig.build())
                CameraX.bindToLifecycle(this@CameraFragment, imageCapture)

            }

            val previewBitmap =
                Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            val widthVal = previewBitmap.width.toDouble() / bitmap.width.toDouble()
            val heightVal = previewBitmap.height.toDouble() / bitmap.height.toDouble()
            this@CameraFragment.points.clear()
            this@CameraFragment.points.addAll(points)
            val previewPoints = points.map {
                Point(
                    (previewBitmap.width.toDouble() / (bitmap.width.toDouble() / it.x.toDouble())).toInt(),
                    (previewBitmap.height.toDouble() / (bitmap.height.toDouble() / it.y.toDouble())).toInt()
                )
            }
            /*val previewPoints = points.map{
                Point( (it.x.toDouble() * widthVal).toInt(),
                    (it.y.toDouble() * heightVal).toInt())
            }*/

            Log.d(
                "Sizes Of Image",
                "\n${bitmap.width}, ${bitmap.height}\n${previewBitmap.width}, ${previewBitmap.height}"
            )
            val canvas = Canvas(previewBitmap)
            if (previewPoints.size >= 4) {
                canvas.drawLineWithPoint(previewPoints[0], previewPoints[1])
                canvas.drawLineWithPoint(previewPoints[1], previewPoints[2])
                canvas.drawLineWithPoint(previewPoints[2], previewPoints[3])
                canvas.drawLineWithPoint(previewPoints[3], previewPoints[0])
            }
            ivDrawable.setImageBitmap(previewBitmap)
//            ivDrawable.setImageBitmap(bitmap)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                if (textureView != null) {
                    textureView.post {

                        if (!isPaused) {
                            startCamera()
                        }
                    }
                }
            } else {
                Toast.makeText(
                    context,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                activity?.finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        context?.let { contx ->
            ContextCompat.checkSelfPermission(
                contx, it
            )
        } == PackageManager.PERMISSION_GRANTED
    }

    inner class DocVH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(document: Document) {
            with(itemView) {
                val doc = document.croppedImage
                ivPreview.setImageBitmap(doc)
                val set = ConstraintSet()
                set.clone(clParent)
                set.setDimensionRatio(R.id.ivPreview, "${doc.width}:${doc.height}")
                set.applyTo(clParent)

                setOnClickListener {
                    findNavController().navigate(R.id.action_cameraFragment_to_imageViewerFragment,
                        Bundle().apply {
                            putInt(ARG_DOC_POSITION, adapterPosition)
                        })
                }
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    private fun Canvas.drawLineWithPoint(p1: org.opencv.core.Point, p2: org.opencv.core.Point) {
        val paint = Paint()
        paint.color = fetchPrimaryColor()
        paint.strokeWidth = 5f
        drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), paint)
    }

    private fun fetchPrimaryColor(): Int {
        /*val typedValue = TypedValue()
        val a = TintTypedArray.obtainStyledAttributes(
            context,
            typedValue.data,
            intArrayOf(R.attr.colorPrimary)
        )
        val color = a.getColor(0, 0)
        a.recycle()*/
        return (viewColor.background as? ColorDrawable)?.color ?: Color.WHITE
    }


    private fun Canvas.drawLineWithPoint(p1: Point, p2: Point) {
        drawLineWithPoint(
            org.opencv.core.Point(p1.x.toDouble(), p1.y.toDouble()),
            org.opencv.core.Point(p2.x.toDouble(), p2.y.toDouble())
        )
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}
