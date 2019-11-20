package com.creoit.creodocscanner

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.creoit.docscanner.ui.DocScannerActivity.Companion.startScanner

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

         startScanner(1, "doc.pdf")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == 1) {
            if(resultCode == RESULT_OK) {
                Toast.makeText(this, "doc.pdf created", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
