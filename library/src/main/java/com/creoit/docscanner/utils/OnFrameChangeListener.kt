package com.creoit.docscanner.utils

import android.graphics.Bitmap
import android.graphics.Point

interface OnFrameChangeListener {
    fun onChange(bitmap: Bitmap, points: ArrayList<Point>)
}