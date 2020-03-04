package com.creoit.docscanner.utils

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.Point


class ImageAnalyzer(val onFrameChangeListener: OnFrameChangeListener) :
    ImageAnalysis.Analyzer {
    private var points = ArrayList<Point>()

    var thr1 = 400.0
    var thr2 = 500.0

    override fun analyze(image: ImageProxy) {

        val bmp = image.getBitmap(image.imageInfo.rotationDegrees)
        bmp.let { bitmap ->
            points.clear()
            getCoordinates(bitmap)?.let {
                points.addAll(it.toList())
            }
            if(points.size == 0) {
               /* points.add(Point(0.0,0.0))
                points.add(Point(0.0,bitmap.height.toDouble()))
                points.add(Point(bitmap.width.toDouble(),bitmap.height.toDouble()))
                points.add(Point(bitmap.width.toDouble(),0.0))*/
                bitmap.getDefaultRect(points)
            }
            getBitmap(bitmap, thr1, thr2)?.let {
                onFrameChangeListener.onChange(
                    it,
                    points.map {
                        android.graphics.Point(
                            it.x.toInt(),
                            it.y.toInt()
                        )
                    } as ArrayList<android.graphics.Point>)
            }

        }
        image.close()
    }
}
