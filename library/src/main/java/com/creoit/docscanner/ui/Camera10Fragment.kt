package com.creoit.docscanner.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.hardware.Camera
import android.hardware.display.DisplayManager
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageButton
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.creoit.docscanner.R
import com.creoit.docscanner.custom.BaseFragment
import com.creoit.docscanner.utils.*
import kotlinx.android.synthetic.main.fragment_camera.*
import kotlinx.android.synthetic.main.rv_item_docs.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.opencv.android.OpenCVLoader
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class Camera10Fragment : BaseFragment(), OnFrameChangeListener {
    override fun getLayoutResId() = R.layout.fragment_camera

    private lateinit var container: ConstraintLayout
    private lateinit var outputDirectory: File
    private lateinit var displayManager: DisplayManager
    private lateinit var mainExecutor: Executor
    private lateinit var analysisExecutor: Executor
    private var bitmap: Bitmap? = null
    private var points = ArrayList<Point>()

    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: androidx.camera.core.Camera? = null

    private lateinit var rvAdapter: SimpleAdapter<Document, DocVH>

    private var isPaused = false

    private val IMMERSIVE_FLAG_TIMEOUT = 500L
    private val FLAGS_FULLSCREEN =
        View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

    private val documentVM by sharedViewModel<DocumentVM>()

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@Camera10Fragment.displayId) {
                Log.d(TAG, "Rotation changed: ${view.display.rotation}")
                imageCapture?.setTargetRotation(view.display.rotation)
                imageAnalyzer?.setTargetRotation(view.display.rotation)
            }
        } ?: Unit
    }

    init {
        if (!OpenCVLoader.initDebug())
            Log.d("ERROR", "Unable to load OpenCV")
        else
            Log.d("SUCCESS", "OpenCV loaded")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainExecutor = ContextCompat.getMainExecutor(requireContext())
        analysisExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        if (::rvAdapter.isInitialized) {
            rvAdapter.notifyDataSetChanged()
            with(viewFinder) {
                postDelayed({
                    systemUiVisibility = FLAGS_FULLSCREEN
                }, IMMERSIVE_FLAG_TIMEOUT)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isPaused = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        displayManager.unregisterDisplayListener(displayListener)
    }

    /** Define callback that will be triggered after a photo has been taken and saved to disk */
    private val imageSavedListener = object : ImageCapture.OnImageSavedCallback {
        override fun onError(imageCaptureError: Int, message: String, cause: Throwable?) {
            Log.e(TAG, "Photo capture failed: $message", cause)
        }

        override fun onImageSaved(photoFile: File) {
            Log.d(TAG, "Photo capture succeeded: ${photoFile.absolutePath}")
            lifecycleScope.launch {

                val previewBitmap = BitmapFactory.decodeFile(photoFile.absolutePath).rotate(90f)
                //image?.decodeBitmap()?.rotate(rotation.toFloat())
                previewBitmap.let {
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
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launchWhenResumed { initViews() }
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
        container = view as ConstraintLayout

        // Every time the orientation of device changes, recompute layout
        displayManager = viewFinder.context
            .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        // Determine the output directory
        outputDirectory = getOutputDirectory(requireContext())

        // Wait for the views to be properly laid out
        viewFinder.post {

            // Keep track of the display in which this view is attached
            displayId = viewFinder.display.displayId

            // Build UI controls
            updateCameraUi()

            // Bind use cases
            bindCameraUseCases()
        }
    }

    override fun onChange(bitmap: Bitmap, points: ArrayList<Point>) {
        lifecycleScope.launchWhenResumed {
            viewFinder.post {
                this@Camera10Fragment.bitmap = bitmap
                //val size = autoFitPreviewBuilder?.viewFinderDimens ?: screenSize
                val layoutParams = viewFinder.layoutParams as ConstraintLayout.LayoutParams
                val ratio = "${bitmap.width}:${bitmap.height}"
                if (layoutParams.dimensionRatio != ratio) {

                    Log.d(
                        "Sizes Of Image Ratio",
                        "\n$ratio\n\n"
                    )
                    /*val set = ConstraintSet()
                    set.clone(clCameraFragment)
                    set.setDimensionRatio(R.id.viewFinder, ratio)
                    set.applyTo(clCameraFragment)*/

                    /*val imageCaptureConfig = ImageCapture.Builder()
                        .setTargetAspectRatio(aspectRatio(bitmap.width, bitmap.height))
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)


                    activity?.windowManager?.defaultDisplay?.rotation?.let {
                        imageCaptureConfig.setTargetRotation(
                            it
                        )
                    }*/

                }

                val previewBitmap =
                    Bitmap.createBitmap(
                        viewFinder.width,
                        viewFinder.height,
                        Bitmap.Config.ARGB_8888
                    )
                val widthVal = previewBitmap.width.toDouble() / bitmap.width.toDouble()
                val heightVal = previewBitmap.height.toDouble() / bitmap.height.toDouble()
                this@Camera10Fragment.points.clear()
                this@Camera10Fragment.points.addAll(points)
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
                    "\n${viewFinder.width}, ${viewFinder.height}\n\n${bitmap.width}, ${bitmap.height}\n${previewBitmap.width}, ${previewBitmap.height}" +
                            "\n $points \n $previewPoints \n\n"
                )
                val canvas = Canvas(previewBitmap)
                if (previewPoints.size >= 4) {
                    canvas.drawLineWithPoint(previewPoints[0], previewPoints[1])
                    canvas.drawLineWithPoint(previewPoints[1], previewPoints[2])
                    canvas.drawLineWithPoint(previewPoints[2], previewPoints[3])
                    canvas.drawLineWithPoint(previewPoints[3], previewPoints[0])
                }
                ivDrawable.setImageBitmap(previewBitmap)
                //ivOpenCV.setImageBitmap(bitmap)
            }
        }
    }

    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateCameraUi()
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = viewFinder.display.rotation

        // Bind the CameraProvider to the LifeCycleOwner
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(Runnable {

            // CameraProvider
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            preview = Preview.Builder()
                // We request aspect ratio but no resolution
                .setTargetAspectRatio(aspectRatio(viewFinder.width, viewFinder.height))
                //.setTargetResolution(Size(viewFinder.width, viewFinder.height))
                // Set initial target rotation
                .setTargetRotation(rotation)
                .build()

            // Default PreviewSurfaceProvider
            preview?.previewSurfaceProvider = viewFinder.previewSurfaceProvider

            // ImageCapture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                // We request aspect ratio but no resolution to match preview config, but letting
                // CameraX optimize for whatever specific resolution best fits requested capture mode
                .setTargetAspectRatio(aspectRatio(viewFinder.width, viewFinder.height))
                //.setTargetResolution(Size(viewFinder.width, viewFinder.height))
                // Set initial target rotation, we will have to call this again if rotation changes
                // during the lifecycle of this use case
                .setTargetRotation(rotation)
                .build()

            // ImageAnalysis
            imageAnalyzer = ImageAnalysis.Builder()

                // We request aspect ratio but no resolution
                //.setTargetAspectRatio(aspectRatio(viewFinder.width, viewFinder.height))
                .setTargetResolution(Size(viewFinder.width, viewFinder.height))
                // Set initial target rotation, we will have to call this again if rotation changes
                // during the lifecycle of this use case
                .setTargetRotation(rotation)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(analysisExecutor, ImageAnalyzer(this))
                }

            // Must unbind the use-cases before rebinding them.
            cameraProvider.unbindAll()

            try {
                // A variable number of use-cases can be passed here -
                // camera provides access to CameraControl & CameraInfo
                Log.d(TAG, "Use case binding...")
                camera = cameraProvider.bindToLifecycle(
                    this as LifecycleOwner, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, mainExecutor)
    }

    /**
     *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    private fun updateCameraUi() {
        ivCapture.setOnClickListener {
            /*lifecycleScope.launch {
                val mainExecutor = ContextCompat.getMainExecutor(requireContext())

                val photoFile =
                    createFile(
                        getOutputDirectory(requireContext()),
                        FILENAME,
                        PHOTO_EXTENSION
                    )

                // Setup image capture metadata
                val metadata = ImageCapture.Metadata()
                imageCapture?.takePicture(
                    photoFile,
                    metadata,
                    mainExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(file: File) {

                            lifecycleScope.launch {


                                *//*var rotation = rotationDegrees % 360
                                                                if (rotation < 0) {
                                                                    rotation += 360
                                                                }*//*

                                val previewBitmap = BitmapFactory.decodeFile(file.absolutePath)
                                //image?.decodeBitmap()?.rotate(rotation.toFloat())
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

                        override fun onError(
                            imageCaptureError: Int,
                            message: String,
                            cause: Throwable?
                        ) {
                            Log.d("Capture Error", message)
                        }
                    })
                // We can only change the foreground Drawable using API level 23+ API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    // Display flash animation to indicate that photo was captured
                    container.postDelayed({
                        container.foreground = ColorDrawable(Color.WHITE)
                        container.postDelayed(
                            { container.foreground = null }, 50L)
                    }, 100L)
                }
            }*/
            // Get a stable reference of the modifiable image capture use case
            imageCapture?.let { imageCapture ->

                // Create output file to hold the image
                val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)

                // Setup image capture metadata
                val metadata = ImageCapture.Metadata().apply {

                    // Mirror image when using the front camera
                    isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
                }

                // Setup image capture listener which is triggered after photo has been taken
                imageCapture.takePicture(photoFile, metadata, mainExecutor, imageSavedListener)

                // We can only change the foreground Drawable using API level 23+ API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    // Display flash animation to indicate that photo was captured
                    container.postDelayed({
                        container.foreground = ColorDrawable(Color.WHITE)
                        container.postDelayed(
                            { container.foreground = null }, 50L
                        )
                    }, 100L)
                }
            }
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
                            putInt(ImageViewerFragment.ARG_DOC_POSITION, adapterPosition)
                        })
                }
            }
        }
    }

    companion object {

        private const val TAG = "CameraXBasic"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(
                baseFolder, SimpleDateFormat(format, Locale.US)
                    .format(System.currentTimeMillis()) + extension
            )
    }
}
