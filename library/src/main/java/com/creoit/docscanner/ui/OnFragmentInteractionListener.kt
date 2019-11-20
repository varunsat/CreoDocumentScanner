package com.creoit.docscanner.ui

import android.net.Uri
import androidx.annotation.StringRes

interface OnFragmentInteractionListener {
    fun onFragmentInteraction(uri: Uri)
    fun onSavePdf()
}