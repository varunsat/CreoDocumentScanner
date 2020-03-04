package com.creoit.docscanner.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore.setRequireOriginal
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.annotation.StringRes
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraX
import androidx.camera.core.CameraXConfig
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.creoit.docscanner.R
import com.creoit.docscanner.custom.BaseActivity
import com.creoit.docscanner.utils.*
import kotlinx.android.synthetic.main.activity_doc_scanner.*
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.alert
import androidx.navigation.NavOptions
import id.zelory.compressor.Compressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.startActivityForResult
import java.io.File


class DocScannerActivity : BaseActivity(), OnFragmentInteractionListener{

    private val imageList by lazy {
        intent?.getParcelableArrayListExtra<Uri>(ARG_IMAGE_URI_LIST)
    }

    private val fileName by lazy {
        intent?.getStringExtra(ARG_FILE_NAME)
    }

    private val documentVM by viewModel<DocumentVM>()
    var dialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doc_scanner)
        //CameraX.initialize(this, Camera2Config.defaultConfig())
        initKoin(this@DocScannerActivity)
        setupActionBar(toolbar)

        documentVM.documentList.clear()
        documentVM.isSinglePage = intent?.getBooleanExtra(ARG_IS_SINGLE, false) ?: false

        lifecycleScope.launchWhenResumed {

            navFragment.findNavController().apply {
                addOnDestinationChangedListener { _, destination, _ ->
                    toolbar.visibility =
                        if (destination.id == R.id.cameraFragment) GONE else VISIBLE
                }

                var appBarConfiguration = AppBarConfiguration.Builder()

                var dialog: Dialog? = null
                lifecycleScope.launch {
                    onUI {
                        dialog = showProgressDialog(R.string.loading_from_gallery)
                    }
                    if (imageList != null && imageList?.size != 0) {
                        val docList = imageList?.mapIndexedNotNull { index, uri ->


                            //val bitmap = uri.getRotationFixedImage()
                            //val bitmap = getGlideBitmap(uri)

                            val bitmap = Compressor(this@DocScannerActivity).compressToBitmap(
                                File(getLocalUri(uri).getPath(this@DocScannerActivity))
                            )

                            bitmap?.let {
                                val points = ArrayList<Point>()
                                var docImage: Bitmap? = null
                                getCoordinates(bitmap)?.let { list ->
                                    docImage = getDocument(bitmap, list)
                                    points.addAll(list.map {
                                        Point(
                                            it.x.toInt(),
                                            it.y.toInt()
                                        )
                                    }.toList())
                                }
                                if (points.size == 0) {
                                    bitmap.getDefaultGraphicRect(points)
                                }
                                val rect = Rectangle(
                                    topLeft = points[0],
                                    bottomLeft = points[1],
                                    bottomRight = points[2],
                                    topRight = points[3]
                                )
                                createImageFromBitmap(this@DocScannerActivity, bitmap, index)?.let {
                                    Document(
                                        image = it,
                                        rectangle = rect,
                                        croppedImage = docImage ?: bitmap
                                    )
                                }

                            }
                        }
                        docList?.let {
                            documentVM.documentList.addAll(it)
                            if (documentVM.documentList.size > 0) {
                                appBarConfiguration =
                                    AppBarConfiguration.Builder(R.id.imageViewerFragment)
                                val navOptions = NavOptions.Builder()
                                    .setPopUpTo(R.id.cameraFragment, true)
                                    .build()
                                navigate(
                                    R.id.action_cameraFragment_to_imageViewerFragment,
                                    bundleOf(),
                                    navOptions
                                )
                            }
                        }
                    }
                    onUI {
                        dialog?.dismiss()
                    }
                }

                NavigationUI.setupWithNavController(toolbar, this, appBarConfiguration.build())
            }
        }
    }

    override fun onSavePdf() {
        lifecycleScope.launch(Dispatchers.Default) {
            onUI {
                dialog = showProgressDialog(R.string.converting_to_pdf)
            }
            fileName?.let {
                val files = documentVM.documentList.mapIndexed { index, document ->
                    MediaFile(
                        getUri(
                            document.croppedImage,
                            index
                        ),
                        document.croppedImage.height,
                        document.croppedImage.width
                    )
                }
                convertToPdf(this@DocScannerActivity, files, fileName)

                if (files.isEmpty()) {
                    finish()
                }

                Intent().apply {
                    putExtra(PdfFile.KEY, fileName)
                    setResult(Activity.RESULT_OK, this)
                    finish()
                }
            } ?: finish()
            onUI {
                dialog?.dismiss()
            }
        }
    }

    override fun onFragmentInteraction(uri: Uri) {

    }

    private fun initKoin(context: Context) {

        val docModule = module {
            single { DocumentVM() }
        }
        GlobalContext.getOrNull()?.unloadModules(docModule)
        if (GlobalContext.getOrNull()?.modules(docModule) == null) {

            startKoin {
                androidContext(context)
                modules(docModule)
                androidLogger()
            }
        }
    }

    companion object {
        const val ARG_IMAGE_URI_LIST = "image_uri_list"
        const val ARG_FILE_NAME = "file_name"
        const val ARG_IS_SINGLE = "is_single"

        open fun Activity.startScanner(
            requestCode: Int,
            fileName: String,
            isSingleDoc: Boolean = false,
            uris: List<Uri>? = null
        ) {
            startActivityForResult(
                intentFor<DocScannerActivity>(
                    ARG_FILE_NAME to fileName,
                    ARG_IS_SINGLE to isSingleDoc,
                    ARG_IMAGE_URI_LIST to uris
                ),
                requestCode
            )
        }
    }
}
