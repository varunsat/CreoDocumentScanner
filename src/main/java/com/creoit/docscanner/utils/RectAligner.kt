package com.creoit.docscanner.utils

import android.graphics.Point
import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2

class RectAligner(val rectangle: Rectangle) {

    fun align(width: Float, height: Float) {
        val top = ArrayList<Point>()

        val points = rectangle.toArrayList()

        val lines = getLines(points)

        val result = lines.sort(width, height)
        if(lines.getTotalArea(width, height) > result.first) {
            lines.clear()
            lines.addAll(result.second)
        }

        rectangle.topLeft = lines[0].start
        rectangle.bottomLeft = lines[1].start
        rectangle.bottomRight = lines[2].start
        rectangle.topRight = lines[3].start

        /*if (!isConcave(points)) {
            val p1 = Point(0, 0)
            val p2 = Point(width, 0)
            val anchorLine = Line(p1, p2)
            val topLine = getLine(lines, anchorLine)

            anchorLine.start = Point(0, 0)
            anchorLine.end = Point(0, height)
            val leftLine = getLine(lines, anchorLine)

            anchorLine.start = Point(0, height)
            anchorLine.end = Point(width, height)
            val bottomLine = getLine(lines, anchorLine)

            anchorLine.start = Point(width, height)
            anchorLine.end = Point(width, 0)
            val rightLine = getLine(lines, anchorLine)

            if(topLine != null && leftLine!= null && bottomLine != null && rightLine != null) {
                rectangle.topLeft = topLine.toArrayList().intersect(leftLine.toArrayList()).toList()[0]
                rectangle.bottomLeft = leftLine.toArrayList().intersect(bottomLine.toArrayList()).toList()[0]
                rectangle.bottomRight = bottomLine.toArrayList().intersect(rightLine.toArrayList()).toList()[0]
                rectangle.topRight = rightLine.toArrayList().intersect(topLine.toArrayList()).toList()[0]
            }
        }*/

    }

    private fun getLine(
        lines: ArrayList<Line>,
        anchorLine: Line
    ): Line? {
        val p1 = anchorLine.start
        val p2 = anchorLine.end
        val points = rectangle.toArrayList()
        var topLine: Line? = null
        for (i in lines.indices) {
            val rect =
                if (doIntersect(p1, p2, lines[i].start, lines[i].end))
                    Rectangle(
                        p1, lines[i].start, p2, lines[i].end
                    )
                else
                    Rectangle(
                        p1, p2, lines[i].start, lines[i].end
                    )

            if (
                points.filter { it != lines[i].start && it != lines[i].end }.none {
                    it.isInside(rect)
                }
            ) {
                topLine = lines[i]
                break
            }
        }
        return topLine
    }

    private fun Point.isInside(rectangle: Rectangle): Boolean {
        val points = rectangle.toArrayList()
        val extreme = Point(Int.MAX_VALUE, y)

        var count = 0
        for (i in 0 until points.size - 1) {
            if (doIntersect(this, points[i], extreme, points[i + 1])) {
                count++
            }
        }

        return count % 2 == 1
    }

    private fun ArrayList<Line>.sort(
        width: Float,
        height: Float,
        count: Int = 1
    ): Pair<Float, List<Line>> {

        /*if (abs(getTotalArea(width, height) - (height * width)) < 5f) {
            Log.d("Area Lines", this.toString())
            Log.d("Area", "${getTotalArea(width, height)} ||| ${(height * width)}")
            return
        } else {*/
        Log.d("Area Lines", this.toString())
        Log.d("Area", "${getTotalArea(width, height)} ||| ${(height * width)}")
        val line = get(0)
        removeAt(0)
        add(line)
        if (count < 4) {
            val result = this.map { Line(Point(it.start.x, it.start.y), Point(it.end.x, it.end.y)) }.toArrayList().sort(width, height, count + 1)
            if(getTotalArea(width, height) > result.first) {
                return result
            }
        }
        return Pair(
            getTotalArea(width, height),
            this.map { Line(Point(it.start.x, it.start.y), Point(it.end.x, it.end.y)) }.toList()
        )
        //}
    }

    private fun <T> List<T>.toArrayList() = ArrayList<T>(this)

    private fun ArrayList<Line>.getTotalArea(width: Float, height: Float): Float {
        var area = 0f
        val rectangle = Rectangle(
            Point(0, 0),
            Point(0, height.toInt()),
            Point(width.toInt(), height.toInt()),
            Point(width.toInt(), 0)
        )
        area += areaOfQuadrilateral(
            arrayListOf(
                get(0).start, get(0).end, get(1).end, get(2).end
            )
        )



        area += if (doIntersect(get(0).start, rectangle.bottomLeft, get(0).end, rectangle.topLeft))
            areaOfQuadrilateral(
                arrayListOf(
                    get(0).start, get(0).end, rectangle.bottomLeft, rectangle.topLeft
                )
            ) else
            areaOfQuadrilateral(
                arrayListOf(
                    get(0).start, rectangle.bottomLeft, rectangle.topLeft, get(0).end
                )
            )



        area += if (doIntersect(
                get(1).start,
                rectangle.bottomRight,
                get(1).end,
                rectangle.bottomLeft
            )
        )
            areaOfQuadrilateral(
                arrayListOf(
                    get(1).start, get(1).end, rectangle.bottomRight, rectangle.bottomLeft
                )
            ) else
            areaOfQuadrilateral(
                arrayListOf(
                    get(1).start, rectangle.bottomRight, rectangle.bottomLeft, get(1).end
                )
            )



        area += if (doIntersect(
                get(2).start,
                rectangle.topRight,
                get(2).end,
                rectangle.bottomRight
            )
        )
            areaOfQuadrilateral(
                arrayListOf(
                    get(2).start, get(2).end, rectangle.topRight, rectangle.bottomRight
                )
            ) else
            areaOfQuadrilateral(
                arrayListOf(
                    get(2).start, rectangle.topRight, rectangle.bottomRight, get(2).end
                )
            )


        area += if (doIntersect(get(3).start, rectangle.topRight, get(3).end, rectangle.topLeft))
            areaOfQuadrilateral(
                arrayListOf(
                    get(3).start, get(3).end, rectangle.topRight, rectangle.topLeft
                )
            ) else
            areaOfQuadrilateral(
                arrayListOf(
                    get(3).start, rectangle.topRight, rectangle.topLeft, get(3).end
                )
            )
        /*area += areaOfQuadrilateral(
            arrayListOf(
                get(1).start, get(1).end, rectangle.bottomLeft, rectangle.topLeft
            )
        )
        area += areaOfQuadrilateral(
            arrayListOf(
                get(2).start, get(2).end, rectangle.bottomRight, rectangle.bottomLeft
            )
        )
        area += areaOfQuadrilateral(
            arrayListOf(
                get(3).start, get(3).end, rectangle.topRight, rectangle.bottomRight
            )
        )*/

        return area
    }

    private fun getLines(points: java.util.ArrayList<Point>): ArrayList<Line> {
        return if (doIntersect(points[0], points[2], points[1], points[3])) {
            arrayListOf(
                Line(points[0], points[1]),
                Line(points[1], points[2]),
                Line(points[2], points[3]),
                Line(points[3], points[0])
            )
        } else if (doIntersect(points[0], points[3], points[1], points[2])) {
            arrayListOf(
                Line(points[0], points[1]),
                Line(points[1], points[3]),
                Line(points[3], points[2]),
                Line(points[2], points[0])
            )
        } else {
            arrayListOf(
                Line(points[0], points[2]),
                Line(points[2], points[1]),
                Line(points[1], points[3]),
                Line(points[3], points[0])
            )
        }
    }

    //concave convex
    private fun isConcave(points: java.util.ArrayList<Point>) =
        (!doIntersect(points[0], points[1], points[2], points[3]) &&
                !doIntersect(points[0], points[2], points[3], points[2]) &&
                !doIntersect(points[0], points[3], points[1], points[2])).let {
            Log.d("Concave 1", doIntersect(points[0], points[1], points[2], points[3]).toString())
            Log.d("Concave 2", doIntersect(points[0], points[2], points[3], points[2]).toString())
            Log.d("Concave 3", doIntersect(points[0], points[3], points[1], points[2]).toString())
            it
        }

    private fun areaOfQuadrilateral(points: ArrayList<Point>) =
        (areaOfTriangle(points[0], points[1], points[2]) +
                areaOfTriangle(points[0], points[2], points[3])).toInt()

    fun areaOfTriangle(A: Point, B: Point, C: Point): Float {
        val area = (A.x * (B.y - C.y) + B.x * (C.y - A.y) + C.x * (A.y - B.y)) / 2.0f
        return Math.abs(area)
    }

    /*fun align() {

        val alignedRect = Rectangle(
            NullPoint(0, 0),
            NullPoint(0, 0),
            NullPoint(0, 0),
            NullPoint(0, 0)
        )
        val points = rectangle.toArrayList()

        val xSum = points.map { it.x }.sum()
        val ySum = points.map { it.y }.sum()
        val center = Point(xSum / 4, ySum / 4)

        val diag1 = ArrayList<Point>()
        val diag2 = ArrayList<Point>()

        points[0].findOppositeSide(center, points.filter { it != points[0] }).let {
            diag1.add(points[0])
            diag1.add(it)

            diag2.addAll(points.filterIndexed { index, point -> index != 0 && point != it })
        }

        Log.d("RectAligner diag", "$diag1 | $diag2")
        val top = arrayListOf(diag1.minBy { it.y }!!, diag2.minBy { it.y }!!)
        val bottom = arrayListOf(diag1.maxBy { it.y }!!, diag2.maxBy { it.y }!!)
        Log.d("RectAligner direc", "$top | $bottom")

        rectangle.topLeft = top.minBy { it.x }.let {
            top.remove(it)
            it!!
        }
        rectangle.bottomLeft = bottom.minBy { it.x }.let {
            bottom.remove(it)
            it!!
        }
        rectangle.bottomRight = bottom[0]
        rectangle.topRight = top[0]
    }*/

    /*fun align() {

        val alignedRect = Rectangle(
            NullPoint(0, 0),
            NullPoint(0, 0),
            NullPoint(0, 0),
            NullPoint(0, 0)
        )
        val points = rectangle.toArrayList()

        val xSum = points.map { it.x }.sum()
        val ySum = points.map { it.y }.sum()
        val center = Point(xSum / 4, ySum / 4)

        val top = points.top(center)
        val left = points.left(center)
        val bottom = points.bottom(center)
        val right = points.right(center)

        val topLeftList = top.intersect(left).toList()
        val bottomLeftList = bottom.intersect(left).toList()
        val bottomRightList = bottom.intersect(right).toList()
        val topRightList = top.intersect(right).toList()

        if (topLeftList.size == 1) {
            alignedRect.topLeft = topLeftList[0]
        }

        if (bottomLeftList.size == 1) {
            alignedRect.bottomLeft = bottomLeftList[0]
        }

        if (bottomRightList.size == 1) {
            alignedRect.bottomRight = bottomRightList[0]
        }

        if (topRightList.size == 1) {
            alignedRect.topRight = topRightList[0]
        }
        Log.d("RectAligner alignedRect", "$alignedRect")

        with(alignedRect) {
            if (topLeft !is NullPoint) {
                Log.d("RectAligner", "topLeft !is NullPoint")
                if (bottomRight is NullPoint) {
                    Log.d("RectAligner", "bottomRight is NullPoint")
                    bottomRight = topLeft.findOppositeSide(
                        center,
                        points.filter { !toArrayList().contains(it) })
                }
            }
            if (bottomLeft !is NullPoint) {
                Log.d("RectAligner", "bottomLeft !is NullPoint")
                if (topRight is NullPoint) {
                    Log.d("RectAligner", "topRight is NullPoint")
                    topRight =
                        bottomLeft.findOppositeSide(
                            center,
                            points.filter { !toArrayList().contains(it) },
                            true
                        )
                }
            }
            if (bottomRight !is NullPoint) {
                Log.d("RectAligner", "bottomRight !is NullPoint")
                if (topLeft is NullPoint) {
                    Log.d("RectAligner", "topLeft is NullPoint")
                    topLeft =
                        bottomRight.findOppositeSide(
                            center,
                            points.filter { !toArrayList().contains(it) })
                }
            }
            if (topRight !is NullPoint) {
                Log.d("RectAligner", "topRight !is NullPoint")
                if (bottomLeft is NullPoint) {
                    Log.d("RectAligner", "bottomLeft is NullPoint")
                    bottomLeft = topRight.findOppositeSide(
                        center,
                        points.filter { !toArrayList().contains(it) })
                }
            }
        }

        Log.d("RectAligner", "$rectangle  |  $alignedRect")
        rectangle.topLeft = alignedRect.topLeft
        rectangle.bottomLeft = alignedRect.bottomLeft
        rectangle.bottomRight = alignedRect.bottomRight
        rectangle.topRight = alignedRect.topRight

    }

    private fun List<Point>.top(center: Point) = filter { it.y < center.y }
    private fun List<Point>.left(center: Point) = filter { it.x < center.x }
    private fun List<Point>.bottom(center: Point) = filter { it.y > center.y }
    private fun List<Point>.right(center: Point) = filter { it.x > center.x }*/


    private fun Point.findOppositeSide(
        center: Point,
        points: List<Point>,
        reversed: Boolean = false
    ) =
        (if (reversed) points.sortedByDescending { getAngle(center, this, it) }
        else points.sortedBy { getAngle(center, this, it) })
            .let {
                Log.d("RectAligner $this", it.toString())
                Point(it[0].x, it[0].y)
            }

    /**
     * Get angle between startPoint, anglePoint and anglePoint, endPoint
     */
    private fun getAngle(startPoint: Point, anglePoint: Point, endPoint: Point): Double {
        val degree = Math.toDegrees(
            atan2(
                (startPoint.x - anglePoint.x).toDouble(),
                (startPoint.y - anglePoint.y).toDouble()
            ) -
                    atan2(
                        (endPoint.x - anglePoint.x).toDouble(),
                        (endPoint.y - anglePoint.y).toDouble()
                    )
        )
        return if (degree < 0) 360 + degree else if (degree > 360) degree % 360 else degree
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
}

