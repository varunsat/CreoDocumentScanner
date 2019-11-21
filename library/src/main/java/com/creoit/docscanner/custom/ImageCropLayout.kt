package com.creoit.docscanner.custom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.creoit.docscanner.R
import com.creoit.docscanner.utils.Rectangle
import com.creoit.docscanner.utils.distBwPoints
import com.creoit.docscanner.utils.withTypedArray
import kotlin.math.atan2


class ImageCropLayout(context: Context, attributeSet: AttributeSet) :
    FrameLayout(context, attributeSet) {

    private val imageView by lazy {
        ImageView(context).apply {
            elevation = -1f
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
    }
    private var movingCorner: Point? = null
    private lateinit var rectangle: Rectangle
    private var circleRadius = 30f
    private var isMoving = false

    private val paint = Paint()
    private val transparentPaint = Paint()
    private var onPointChangeListener: OnPointChangeListener? = null

    init {
        initViews(attributeSet)
    }

    @SuppressLint("ResourceType")
    private fun initViews(attrs: AttributeSet) {

        transparentPaint.style = Paint.Style.FILL
        transparentPaint.color = ContextCompat.getColor(context, R.color.transparentGray)
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 9f

        attrs.let {
            context.withTypedArray(it, R.styleable.ImageCropLayout) {
                paint.color = getColor(
                    R.styleable.ImageCropLayout_strokeColor,
                    Color.WHITE
                )
            }
        }
        addView(imageView)

        setWillNotDraw(false)

        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    (getCorner(event.x.toInt(), event.y.toInt()))?.also {
                        movingCorner = it
                        if (onPointChangeListener?.onMove(
                                event.rawX.toInt(),
                                event.rawY.toInt()
                            ) == true
                        ) {
                            it.x = event.x.toInt()
                            it.y = event.y.toInt()
                            with(rectangle)
                            {
                                when (it) {
                                    topLeft -> {
                                        topLeft.validatePointPos(topRight, bottomLeft, bottomRight)
                                    }
                                    bottomLeft -> {
                                        bottomLeft.validatePointPos(topLeft, bottomRight, topRight)
                                    }
                                    bottomRight -> {
                                        bottomRight.validatePointPos(bottomLeft, topRight, topLeft)
                                    }
                                    topRight -> {
                                        topRight.validatePointPos(topLeft, bottomRight, bottomLeft)
                                    }
                                }
                            }
                            isMoving = true
                            invalidate()
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    Log.d(
                        "Rectangalore",
                        rectangle.toArrayList().toString()
                    )
                    onPointChangeListener?.onStop()
                    movingCorner = null
                    isMoving = false
                    validateRect()
                    //RectAligner(rectangle).align(width.toFloat(), height.toFloat())
                    invalidate()
                }
            }
            true
        }
    }

    fun setOnPointChangeListener(onPointChangeListener: OnPointChangeListener) {
        this.onPointChangeListener = onPointChangeListener
    }

    override fun onDraw(canvas: Canvas?) {
        if (!::rectangle.isInitialized) {
            val horizontalDiff = width * 30 / 100
            val verticalDiff = height * 30 / 100
            rectangle = Rectangle(
                topLeft = Point(horizontalDiff, verticalDiff),
                bottomLeft = Point(horizontalDiff, height - verticalDiff),
                bottomRight = Point(width - horizontalDiff, height - verticalDiff),
                topRight = Point(width - horizontalDiff, verticalDiff)
            )
        }
        if (!isMoving) {
            drawBackground(canvas)
        }
        drawPoints(canvas, radius = if (isMoving) 10f else circleRadius)
        drawLines(canvas)
        super.onDraw(canvas)
    }

    /***
     * Draw transparent black paint to outside rectangle
     */
    private fun drawBackground(canvas: Canvas?) {
        val path = Path()
        path.reset()
        with(rectangle) {
            //TopRight to TopLeft to BottomLeft
            path.moveTo(topLeft.getX(), topLeft.getY())
            path.lineTo(topRight.getX(), topRight.getY())
            path.lineTo(width.toFloat(), 0f)
            path.lineTo(0f, 0f)
            path.lineTo(topLeft.getX(), topLeft.getY())
            path.lineTo(0f, 0f)
            path.lineTo(0f, height.toFloat())
            path.lineTo(bottomLeft.getX(), bottomLeft.getY())
            path.lineTo(topLeft.getX(), topLeft.getY())

            //BottomLeft to BottomRight to TopRight
            path.moveTo(width.toFloat(), height.toFloat())
            path.lineTo(bottomRight.getX(), bottomRight.getY())
            path.lineTo(bottomLeft.getX(), bottomLeft.getY())
            path.lineTo(0f, height.toFloat())
            path.lineTo(width.toFloat(), height.toFloat())
            path.lineTo(width.toFloat(), 0f)
            path.lineTo(topRight.getX(), topRight.getY())
            path.lineTo(bottomRight.getX(), bottomRight.getY())
            path.lineTo(width.toFloat(), height.toFloat())
        }
        canvas?.drawPath(path, transparentPaint)
    }

    /***
     * Draw crop layout boarder
     * 5f difference will make corner overlap
     */
    private fun drawLines(canvas: Canvas?) {
        canvas?.drawLines(
            floatArrayOf(
                rectangle.topLeft.getX(), rectangle.topLeft.getY() - 5f,
                rectangle.bottomLeft.getX(), rectangle.bottomLeft.getY() + 5f,
                rectangle.bottomLeft.getX() - 5f, rectangle.bottomLeft.getY(),
                rectangle.bottomRight.getX() + 5f, rectangle.bottomRight.getY(),
                rectangle.bottomRight.getX(), rectangle.bottomRight.getY() + 5f,
                rectangle.topRight.getX(), rectangle.topRight.getY() - 5f,
                rectangle.topRight.getX() + 5f, rectangle.topRight.getY(),
                rectangle.topLeft.getX() - 5f, rectangle.topLeft.getY()
            ), paint
        )
        /*canvas?.drawLines(
            floatArrayOf(
                0f,0f,
                0f, height.toFloat(),
                0f, height.toFloat(),
                width.toFloat(), height.toFloat(),
                width.toFloat(), height.toFloat(),
                width.toFloat(), 0f,
                width.toFloat(), 0f,
                0f, 0f
            ), paint
        )*/
    }

    private fun Point.getX() = x.toFloat()
    private fun Point.getY() = y.toFloat()

    /***
     * Draw circles at corner of crop layout
     */
    private fun drawPoints(canvas: Canvas?, radius: Float = circleRadius) {
        val xMax = rectangle.toArrayList().map { it.x }.max() ?: 0
        val yMax = rectangle.toArrayList().map { it.y }.max() ?: 0
        val xMin = rectangle.toArrayList().map { it.x }.min() ?: 0
        val yMin = rectangle.toArrayList().map { it.y }.min() ?: 0
        val xSum = rectangle.toArrayList().map { it.x }.sum()
        val ySum = rectangle.toArrayList().map { it.y }.sum()
        canvas?.drawCircle(
            rectangle.topLeft.getX(),
            rectangle.topLeft.getY(),
            radius,
            paint
        )
        canvas?.drawCircle(
            rectangle.bottomLeft.getX(),
            rectangle.bottomLeft.getY(),
            radius,
            paint
        )
        canvas?.drawCircle(
            rectangle.bottomRight.getX(),
            rectangle.bottomRight.getY(),
            radius,
            paint
        )
        canvas?.drawCircle(
            rectangle.topRight.getX(),
            rectangle.topRight.getY(),
            radius,
            paint
        )
        /*canvas?.drawCircle(
            ((xMin + xMax) / 2).toFloat(),
            ((yMin + yMax) / 2).toFloat(),
            radius,
            paint
        )*/
    }

    /***
     * Get nearest corner to Point(x,y)
     */
    private fun getCorner(x: Int, y: Int): Point? {
        return when {
            rectangle.topLeft.isInside(x, y) -> rectangle.topLeft
            rectangle.bottomLeft.isInside(x, y) -> rectangle.bottomLeft
            rectangle.bottomRight.isInside(x, y) -> rectangle.bottomRight
            rectangle.topRight.isInside(x, y) -> rectangle.topRight
            else -> null
        }
    }

    /***
     * Check if Point is inside the circle with center as Point(circle_x, circley) and rad as radius
     */
    private fun Point.isInside(
        circle_x: Int, circle_y: Int,
        rad: Int = circleRadius.toInt() + 30
    ) = (x - circle_x) * (x - circle_x) + (y - circle_y) * (y - circle_y) <= rad * rad

    /***
     * Sort rectangle points
     */
    private fun validateRect() {
        var rectArray = rectangle.toArrayList()

        /*rectArray.forEachIndexed { index, point ->
            for(i in index until rectArray.size) {
                if(point.x == rectArray[i].x) {
                    if(point.y >= rectArray[i].y)
                }
            }
        }*/

        val xSum = rectArray.map { it.x }.sum()
        val ySum = rectArray.map { it.y }.sum()
        val center = Point(xSum, ySum)

        rectArray.sortBy { it.y }
        //TopLeft and BottomLeft point will have x min (i.e. 0th or 1st element)
        //TopLeft will have y min BottomLeft will have y max
        var topLeft = rectArray.filterIndexed { index, _ -> index in 0..1 }.minBy { it.x }?.also {
            rectArray.remove(it)
        }
        var topRight = rectArray.filterIndexed { index, _ -> index in 0 until 1 }.maxBy { it.x }?.also {
            rectArray.remove(it)
        }
        rectArray.reverse()
        //TopRight and BottomRight point will have x max (i.e. 0th or 1st element after reverse)
        //TopRight will have y min BottomRight will have y max
        var bottomLeft = rectArray.filterIndexed { index, _ -> index in 0..1 }.minBy { it.x }?.also {
            rectArray.remove(it)
        }
        var bottomRight = rectArray.filterIndexed { index, _ -> index in 0..1 }.maxBy { it.x }?.also {
            rectArray.remove(it)
        }
        Log.d(
            "Rectangalore Sort",
            rectArray.toString()
        )

        if (
            doIntersect(topLeft!!, bottomRight!!, bottomLeft!!, topRight!!) ||
            doIntersect(topLeft, bottomRight, bottomLeft, topRight)
        ) {
            rectArray = rectangle.toArrayList()
            rectArray.sortBy { it.x }
            //TopLeft and BottomLeft point will have x min (i.e. 0th or 1st element)
            //TopLeft will have y min BottomLeft will have y max
            topLeft = rectArray.filterIndexed { index, _ -> index in 0..1 }.minBy { it.y }?.also {
                rectArray.remove(it)
            }
            bottomLeft = rectArray.filterIndexed { index, _ -> index in 0 until 1 }.maxBy { it.y }?.also {
                rectArray.remove(it)
            }
            rectArray.reverse()
            //TopRight and BottomRight point will have x max (i.e. 0th or 1st element after reverse)
            //TopRight will have y min BottomRight will have y max
            topRight = rectArray.filterIndexed { index, _ -> index in 0..1 }.minBy { it.y }?.also {
                rectArray.remove(it)
            }
            bottomRight = rectArray.filterIndexed { index, _ -> index in 0..1 }.maxBy { it.y }?.also {
                rectArray.remove(it)
            }
        }
        with(rectangle) {
            this.topLeft = topLeft!!
            this.bottomLeft = bottomLeft!!
            this.topRight = topRight!!
            this.bottomRight = bottomRight!!
        }
    }

    // Given three colinear points p, q, r, the function checks if
    // point q lies on line segment 'pr'
    private fun onSegment(p: Point, q: Point, r: Point): Boolean {
        return q.x <= Math.max(p.x, r.x) && q.x >= Math.min(p.x, r.x) &&
            q.y <= Math.max(p.y, r.y) && q.y >= Math.min(p.y, r.y)

    }

    // To find orientation of ordered triplet (p, q, r).
    // The function returns following values
    // 0 --> p, q and r are colinear
    // 1 --> Clockwise
    // 2 --> Counterclockwise
    private fun orientation(p: Point, q: Point, r: Point): Int {
        // See https://www.geeksforgeeks.org/orientation-3-ordered-points/
        // for details of below formula.
        val `val` = (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)

        if (`val` == 0) return 0 // colinear

        return if (`val` > 0) 1 else 2 // clock or counterclock wise
    }

    fun doIntersect(p1: Point, q1: Point, p2: Point, q2: Point): Boolean {
        // Find the four orientations needed for general and
        // special cases
        val o1 = orientation(p1, q1, p2)
        val o2 = orientation(p1, q1, q2)
        val o3 = orientation(p2, q2, p1)
        val o4 = orientation(p2, q2, q1)

        // General case
        if (o1 != o2 && o3 != o4)
            return true

        // Special Cases
        // p1, q1 and p2 are colinear and p2 lies on segment p1q1
        if (o1 == 0 && onSegment(p1, p2, q1)) return true

        // p1, q1 and q2 are colinear and q2 lies on segment p1q1
        if (o2 == 0 && onSegment(p1, q2, q1)) return true

        // p2, q2 and p1 are colinear and p1 lies on segment p2q2
        if (o3 == 0 && onSegment(p2, p1, q2)) return true

        // p2, q2 and q1 are colinear and q1 lies on segment p2q2
        return o4 == 0 && onSegment(p2, q1, q2)

// Doesn't fall in any of the above cases
    }

    fun calculateInterceptionPoint(s1: Point, d1: Point, s2: Point, d2: Point): Point? {

        val sNumerator = (s1.y * d1.x + s2.x * d1.y - s1.x * d1.y - s2.y * d1.x).toDouble()
        val sDenominator = (d2.y * d1.x - d2.x * d1.y).toDouble()

        // parallel ... 0 or infinite points, or one of the vectors is 0|0
        if (sDenominator == 0.0) {
            return null
        }

        val s = sNumerator / sDenominator

        val t: Double
        if (d1.x != 0) {
            t = (s2.x + s * d2.x - s1.x) / d1.x
        } else {
            t = (s2.y + s * d2.y - s1.y) / d1.y
        }

        return Point((s1.x + t * d1.x).toInt(), (s1.y + t * d1.y).toInt())

    }

    /**
     * This method will swap the Point with nearest point(p1 or p2)
     * if Point crosses the opposite side line
     */
    private fun Point.validatePointPos(p1: Point, p2: Point, oppPoint: Point) {
        val a1 = getAngle(this, p1, oppPoint)
        val a2 = getAngle(this, p2, oppPoint)
        //if angle if below 10 or above 350 swap
        if (a1 !in 10f..350f) {
            swapPoint(this, p1)
        } else if (a2 !in 10f..350f) {
            swapPoint(this, p2)
        } else if (getAngle(p1, this, p2) < 10) {
            if (distBwPoints(this, p1) > distBwPoints(this, p2)) {
                swapPoint(this, p2)
            } else {
                swapPoint(this, p1)
            }
        }
    }

    /**
     * Get angle between startPoint, anglePoint and anglePoint, endPoint
     */
    private fun getAngle(startPoint: Point, anglePoint: Point, endPoint: Point): Double {
        val degree = Math.toDegrees(
            atan2(startPoint.x - anglePoint.x, startPoint.y - anglePoint.y) -
                    atan2(endPoint.x - anglePoint.x, endPoint.y - anglePoint.y)
        )
        return if (degree < 0) 360 + degree else if (degree > 360) degree % 360 else degree
    }

    fun setRectangle(rectangle: Rectangle) {
        this.rectangle = rectangle
        invalidate()
    }

    fun getRectangle() = rectangle

    private fun swapPoint(p1: Point, p2: Point) {
        p1.x = p1.x + p2.x
        p2.x = p1.x - p2.x
        p1.x = p1.x - p2.x

        p1.y = p1.y + p2.y
        p2.y = p1.y - p2.y
        p1.y = p1.y - p2.y
    }

    private fun atan2(x: Int, y: Int) = atan2(y.toDouble(), x.toDouble())

    companion object {
        interface OnPointChangeListener {
            fun onMove(x: Int, y: Int): Boolean
            fun onStop()
        }
    }

}