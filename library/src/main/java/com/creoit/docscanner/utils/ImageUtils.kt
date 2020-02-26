package com.creoit.docscanner.utils

import android.content.Context
import android.content.res.TypedArray
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.Exception
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.pow
import android.graphics.Bitmap
import android.os.Environment.getExternalStorageDirectory
import android.util.AttributeSet
import android.util.TypedValue
import androidx.annotation.StyleableRes
import androidx.appcompat.widget.TintTypedArray
import androidx.core.net.toUri
import com.creoit.docscanner.R
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis


fun imageToMat(image: Image): Mat {
    var buffer: ByteBuffer
    var rowStride: Int
    var pixelStride: Int
    val width = image.width
    val height = image.height
    var offset = 0

    val planes = image.planes
    val data =
        ByteArray(image.width * image.height * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8)
    val rowData = ByteArray(planes[0].rowStride)

    for (i in planes.indices) {
        buffer = planes[i].buffer
        rowStride = planes[i].rowStride
        pixelStride = planes[i].pixelStride
        val w = if (i == 0) width else width / 2
        val h = if (i == 0) height else height / 2
        for (row in 0 until h) {
            val bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8
            if (pixelStride == bytesPerPixel) {
                val length = w * bytesPerPixel
                buffer.get(data, offset, length)

                if (h - row != 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
                offset += length
            } else {


                if (h - row == 1) {
                    buffer.get(rowData, 0, width - pixelStride + 1)
                } else {
                    buffer.get(rowData, 0, rowStride)
                }

                for (col in 0 until w) {
                    data[offset++] = rowData[col * pixelStride]
                }
            }
        }
    }

    val mat = Mat(height + height / 2, width, CvType.CV_8UC1)
    mat.put(0, 0, data)

    return mat
}

fun mapp(mat: MatOfPoint2f): ArrayList<Point> {
    val points = mat.toList()
    val rectPointOrdered = ArrayList<Point>()

    points.sortBy { it.x + it.y }
    rectPointOrdered.add(0, points[0])

    points.sortBy { it.x - it.y }
    rectPointOrdered.add(1, points[0])

    points.sortBy { it.x + it.y }
    rectPointOrdered.add(2, points[points.size - 1])

    points.sortBy { it.x - it.y }
    rectPointOrdered.add(3, points[points.size - 1])

    return rectPointOrdered
}

fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun ImageProxy.getBitmap(rotationDegrees: Int): Bitmap {

    var rotation = rotationDegrees % 360
    if (rotation < 0) {
        rotation += 360
    }
    val mYuvMat = image?.let { imageToMat(it) }
    val bgrMat = Mat(image?.height ?: 0, image?.width ?: 0, CvType.CV_8UC4)
    Imgproc.cvtColor(mYuvMat, bgrMat, Imgproc.COLOR_YUV2BGR_I420)
    val rgbaMatOut = Mat()
    Imgproc.cvtColor(bgrMat, rgbaMatOut, Imgproc.COLOR_BGR2RGBA, 0)
    val bitmap = Bitmap.createBitmap(bgrMat.cols(), bgrMat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(rgbaMatOut, bitmap)
    return bitmap.rotate(rotation.toFloat())
}

fun ImageProxy.decodeBitmap(): Bitmap? {
    return image?.let {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.capacity()).also { buffer.get(it) }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

/**
 * only for testing purpose
 * get bitmap after image processing
 */
fun getBitmap(bitmap: Bitmap, thr1: Double, thr2: Double): Bitmap? = runBlocking {
    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val srcMat = Mat(mutableBitmap.height, mutableBitmap.width, CvType.CV_8UC3)
    Utils.bitmapToMat(mutableBitmap, srcMat)
    val grayMat = Mat()
    val cannyEdges = Mat()
    val sobelX = Mat()
    val sobelY = Mat()
    val abs_sobelX = Mat()
    val abs_sobelY = Mat()
    val corner = Mat()

    val time = measureTimeMillis {
        //Converting the image to grayscale
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(grayMat, grayMat, Size(5.0, 5.0), 0.0, 0.0, Core.BORDER_DEFAULT)
        Imgproc.Sobel(grayMat, sobelX, CvType.CV_16S, 1, 0)
        Core.convertScaleAbs(sobelX, abs_sobelX)
        Imgproc.Sobel(grayMat, sobelY, CvType.CV_16S, 0, 1)
        Core.convertScaleAbs(sobelY, abs_sobelY)
        Core.addWeighted(abs_sobelX, 0.9, abs_sobelY, 0.9, 0.0, corner)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, 1.0))
        Imgproc.dilate(corner, corner, kernel)
        Imgproc.Canny(corner, corner, thr1, thr2)
    }
    Log.d("Time in milli", time.toString())

    val res = corner
    val bitmap = Bitmap.createBitmap(res.cols(), res.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(res, bitmap)
    bitmap

}

/**
 * Get corner coordinates
 * Reference :- https://www.youtube.com/watch?v=PV0uxIfy_-A
 */
fun getCoordinates(bitmap: Bitmap): ArrayList<Point>? {
    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val srcMat = Mat(mutableBitmap.height, mutableBitmap.width, CvType.CV_8UC3)
    Utils.bitmapToMat(mutableBitmap, srcMat)
    val grayMat = Mat()
    val cannyEdges = Mat()
    val corner = Mat()
    val sobelX = Mat()
    val sobelY = Mat()
    val abs_sobelX = Mat()
    val abs_sobelY = Mat()

    Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)
    Imgproc.GaussianBlur(grayMat, grayMat, Size(5.0, 5.0), 0.0, 0.0, Core.BORDER_DEFAULT)
    Imgproc.Sobel(grayMat, sobelX, CvType.CV_16S, 1, 0)
    Core.convertScaleAbs(sobelX, abs_sobelX)
    Imgproc.Sobel(grayMat, sobelY, CvType.CV_16S, 0, 1)
    Core.convertScaleAbs(sobelY, abs_sobelY)
    Core.addWeighted(abs_sobelX, 0.9, abs_sobelY, 0.9, 0.0, corner)
    Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(1.0, 1.0))
    Imgproc.Canny(corner, corner, 120.0, 500.0)
    Imgproc.dilate(corner, corner, Mat())


    var contours: ArrayList<MatOfPoint>? = ArrayList()
    val hierarchy = Mat()

    Imgproc.findContours(
        corner,
        contours,
        hierarchy,
        Imgproc.RETR_LIST,
        Imgproc.CHAIN_APPROX_SIMPLE
    )
    contours =
        contours?.filter { Imgproc.contourArea(it) > 25000 }?.toList() as? ArrayList<MatOfPoint>
    return contours?.let {
        contours.sortBy { Imgproc.contourArea(it) }
        contours.reverse()
        val target = contours.firstOrNull { contour ->
            val mMOP2f1 = MatOfPoint2f()
            val approx = MatOfPoint2f()
            contour.convertTo(mMOP2f1, CvType.CV_32FC2)
            val p = Imgproc.arcLength(mMOP2f1, true)
            Imgproc.approxPolyDP(mMOP2f1, approx, 0.02 * p, true)
            approx.total() == 4L
        }

        target?.let {
            Log.d("Area Of Rect", "${Imgproc.contourArea(target)}")
            val matofPoint = MatOfPoint2f()
            it.convertTo(matofPoint, CvType.CV_32FC2)
            val approx = mapp(matofPoint)
            approx
        }
    }
}

fun getDocument(bitmap: Bitmap, points: List<Point>?, isOriginal: Boolean = true): Bitmap {
    return points?.let { list ->
        val srcMat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC3)
        val myBitmap32 = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        Utils.bitmapToMat(myBitmap32, srcMat)
        val src = MatOfPoint2f(
            list[0].let { Point(it.x, it.y) },
            list[1].let { Point(it.x, it.y) },
            list[2].let { Point(it.x, it.y) },
            list[3].let { Point(it.x, it.y) }
        )
        val height = max(distBwPoints(list[0], list[1]), distBwPoints(list[3], list[2]))
        val width = max(distBwPoints(list[0], list[3]), distBwPoints(list[1], list[2]))
        val dest = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(0.0, height),
            Point(width, height),
            Point(width, 0.0)
        )
        val resultMat = Mat()
        val opt = Imgproc.getPerspectiveTransform(src, dest)
        Imgproc.warpPerspective(
            srcMat,
            dest,
            opt,
            Size(width, height),
            Imgproc.INTER_LINEAR
        )
        if (isOriginal)
            Imgproc.cvtColor(dest, dest, Imgproc.COLOR_BGR2GRAY)
        Imgproc.adaptiveThreshold(
            dest,
            resultMat,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            115,
            10.0
        )

        val resultBitmap =
            Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(resultMat, resultBitmap)
        resultBitmap
    } ?: bitmap
}

fun getDocument(
    bitmap: Bitmap,
    rectangle: Rectangle?,
    isOriginal: Boolean = false,
    contrast: Double = 1.0,
    brightness: Double = 0.0
): Bitmap {
    return rectangle?.let {
        val srcMat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC3)
        val myBitmap32 = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        Utils.bitmapToMat(myBitmap32, srcMat)
        srcMat.convertTo(srcMat, -1, contrast, brightness)
        val src = MatOfPoint2f(
            it.topLeft.let { Point(it.x.toDouble(), it.y.toDouble()) },
            it.bottomLeft.let { Point(it.x.toDouble(), it.y.toDouble()) },
            it.bottomRight.let { Point(it.x.toDouble(), it.y.toDouble()) },
            it.topRight.let { Point(it.x.toDouble(), it.y.toDouble()) }
        )
        val height =
            max(distBwPoints(it.topLeft, it.bottomLeft), distBwPoints(it.bottomRight, it.topRight))
        val width =
            max(distBwPoints(it.topLeft, it.topRight), distBwPoints(it.bottomLeft, it.bottomRight))
        val dest = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(0.0, height),
            Point(width, height),
            Point(width, 0.0)
        )
        val resultMat = Mat()
        val opt = Imgproc.getPerspectiveTransform(src, dest)
        Imgproc.warpPerspective(
            srcMat,
            dest,
            opt,
            Size(width, height),
            Imgproc.INTER_LINEAR
        )
        val res = if (!isOriginal) {
            Imgproc.cvtColor(dest, dest, Imgproc.COLOR_BGR2GRAY)
            Imgproc.adaptiveThreshold(
                dest,
                resultMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                115,
                10.0
            )
            resultMat
        } else dest


        val resultBitmap =
            Bitmap.createBitmap(res.cols(), res.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(res, resultBitmap)
        resultBitmap
    } ?: bitmap
}

fun distBwPoints(p1: Point, p2: Point): Double {
    val x = p2.x - p1.x
    val y = p2.y - p1.y
    return sqrt(
        x.pow(2) + y.pow(2)
    )
}

fun distBwPoints(p1: android.graphics.Point, p2: android.graphics.Point): Double {
    val x = p2.x - p1.x
    val y = p2.y - p1.y
    return Math.sqrt(
        x.toDouble().pow(2) + y.toDouble().pow(2)
    )
}

fun createImageFromBitmap(context: Context, bitmap: Bitmap, count: Int = 0): String? {
    var fileName: String? = "temp$count"
    try {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val fo = context.openFileOutput(fileName, AppCompatActivity.MODE_PRIVATE)
        fo.write(bytes.toByteArray())
        fo.close()
    } catch (e: Exception) {
        e.printStackTrace()
        fileName = null
    }
    return fileName
}

fun getUri(bitmap: Bitmap, count: Int): Uri {
    val path = getExternalStorageDirectory().toString()
    val fOut: OutputStream
    val file = File(
        path,
        "Creo$count.jpg"
    )
    fOut = FileOutputStream(file)
    bitmap.compress(
        Bitmap.CompressFormat.JPEG,
        100,
        fOut
    )
    fOut.flush()
    fOut.close()
    return Uri.fromFile(file)
}

fun getOutputDirectory(context: Context): File {
    val appContext = context.applicationContext
    val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
        File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() }
    }
    return if (mediaDir != null && mediaDir.exists())
        mediaDir else appContext.filesDir
}

fun Bitmap.getDefaultRect(points: ArrayList<Point>?) {
    points?.let {
        val minX = width.toDouble() * 15 / 100
        val minY = height.toDouble() * 15 / 100
        points.add(Point(minX, minY))
        points.add(Point(minX, height.toDouble() - minY))
        points.add(Point(width.toDouble() - minX, height.toDouble() - minY))
        points.add(Point(width.toDouble() - minX, minY))
    }
}

fun Bitmap.getDefaultGraphicRect(points: ArrayList<android.graphics.Point>?) {
    points?.let {
        val minX = width * 15 / 100
        val minY = height * 15 / 100
        points.add(android.graphics.Point(minX, minY))
        points.add(android.graphics.Point(minX, height - minY))
        points.add(android.graphics.Point(width - minX, height - minY))
        points.add(android.graphics.Point(width - minX, minY))
    }
}

fun Context.withTypedArray(
    attrs: AttributeSet, @StyleableRes styleableResArray: IntArray,
    block: TypedArray.() -> Unit
) {
    val typedArray = obtainStyledAttributes(attrs, styleableResArray)
    try {
        typedArray.block()
    } finally {
        typedArray.recycle()
    }
}