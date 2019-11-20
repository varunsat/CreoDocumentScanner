package com.creoit.docscanner.ui

import androidx.lifecycle.ViewModel
import com.creoit.docscanner.utils.Document

class DocumentVM : ViewModel() {

    val documentList: ArrayList<Document> = ArrayList()
    var isSinglePage: Boolean = false
}