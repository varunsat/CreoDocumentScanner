package com.creoit.docscanner.utils

import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Rectangle(
    var topLeft: Point,
    var bottomLeft: Point,
    var bottomRight: Point,
    var topRight: Point
) : Parcelable {
    fun toArrayList() = arrayListOf(topLeft, bottomLeft, bottomRight, topRight)
}

@Parcelize
data class Line(
    var start: Point,
    var end: Point
): Parcelable {
    fun toArrayList() = arrayListOf(start, end)
}

class NullPoint(x: Int, y: Int) : Point(x,y)

@Parcelize
data class Document(
    val image: String,
    var rectangle: Rectangle,
    var croppedImage: Bitmap,
    var rotation: Float = 0f,
    var isOriginal: Boolean = false,
    var brightness: Double = 0.0,
    var contrast: Double = 1.0
) : Parcelable

@Parcelize
data class MediaFile(
    val uri: Uri,
    val height: Int,
    val width: Int
): Parcelable {
}

@Parcelize
data class PdfFile(
    val uri: Uri
): Parcelable {
    companion object {
        const val KEY = "pdfFile"
    }
}