package com.creoit.docscanner

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import com.creoit.docscanner.custom.BaseActivity
import com.creoit.docscanner.ui.DocScannerActivity
import com.creoit.docscanner.utils.PdfFile
import org.jetbrains.anko.doAsyncResult
import org.jetbrains.anko.startActivityForResult

class DocScanner(
    private val activity: AppCompatActivity,
    private val isMultiple: Boolean,
    private val mediaKey: String,
    private val uris: List<Uri>
) {

    private constructor(builder: Builder) : this(
        builder.activity,
        builder.isMultiple,
        builder.mediaKey,
        builder.uris
    )

    class Builder(val activity: AppCompatActivity) {
        var isMultiple: Boolean = true
            private set

        var mediaKey: String = "doc.pdf"
            private set

        var uris = ArrayList<Uri>()
            private set

        fun setMultiple(isMultiple: Boolean): Builder {
            this.isMultiple = isMultiple
            return this
        }

        fun setMediaKey(mediaKey: String): Builder {
            this.mediaKey = mediaKey
            return this
        }

        fun setUris(array: ArrayList<Uri>): Builder {
            this.uris = array
            return this
        }

        fun build() = DocScanner(this)
    }

    fun getClass(): Activity = DocScannerActivity()

    fun getParams() = arrayOf(
        DocScannerActivity.ARG_FILE_NAME to mediaKey,
        DocScannerActivity.ARG_IMAGE_URI_LIST to if (uris.isEmpty()) null else uris,
        DocScannerActivity.ARG_IS_SINGLE to !isMultiple
    )

    fun getDocument() {
        val result = activity.startActivityForResult<DocScannerActivity>(
            0,
            *getParams()
        )
    }
}