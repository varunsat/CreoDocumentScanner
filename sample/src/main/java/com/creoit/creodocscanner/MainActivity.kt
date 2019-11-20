package com.creoit.creodocscanner

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.creoit.docscanner.ui.DocScannerActivity.Companion.startScanner
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val FROM_GALLERY = 2
    private val FROM_DOC_SCANNER = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        btnPickFromCamera.setOnClickListener {
            startScanner(FROM_DOC_SCANNER, "doc.pdf")
        }
        btnPickFromGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(intent, FROM_GALLERY)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (requestCode == FROM_DOC_SCANNER) {
                Toast.makeText(this, "doc.pdf created", Toast.LENGTH_SHORT).show()
            }
            else if(requestCode == FROM_GALLERY) {
                val uris = arrayListOf<Uri>()
                when {
                    data?.data != null -> {
                        val uri = data.data

                        uri?.let {
                            if (it.path != null)
                                uris.add(it)
                        }
                    }
                    data?.clipData != null -> {
                        repeat(data.clipData!!.itemCount) {
                            uris.add(data.clipData!!.getItemAt(it).uri)
                        }
                    }
                }
                startScanner(FROM_DOC_SCANNER, "doc.pdf", uris = uris)
            }
        }
    }
}
