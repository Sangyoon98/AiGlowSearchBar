package com.sangyoon.aiglow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

private const val ApproximationErrorPx = 0.5f
private const val SamplesPerLoop = 192
private const val PointEpsilon = 1e-4f

/**
 * Converts the legacy degree-based animation value into one wrapped perimeter turn.
 *
 * Why keep degrees internally: the public configuration still calls the duration a
 * rotation, while normalized distance lets all perimeter renderers share one phase
 * without changing API or timing semantics.
 *
 * (한국어) 기존 degree 애니메이션 값을 둘레 한 바퀴의 정규화 거리로 바꿉니다. 공개 설정과
 * 타이밍 호환성을 유지하면서 모든 둘레 렌더러가 같은 phase를 공유하기 위한 변환입니다.
 */
internal fun perimeterPhaseFraction(angleDegrees: Float): Float = positiveModulo(angleDegrees / 360f)

/**
 * Resolves the palette coordinate at an absolute distance along an outline.
 *
 * Why distance rather than polar angle: the same phase advances the same number of
 * pixels on a short side, a long side, and a rounded corner.
 *
 * (한국어) 외곽선의 절대 거리에서 팔레트 좌표를 계산합니다. 극각 대신 둘레 거리를 쓰므로
 * 짧은 변·긴 변·둥근 모서리 어디서나 같은 phase가 같은 픽셀 거리만큼 이동합니다.
 */
internal fun perimeterGradientFraction(
    distancePx: Float,
    perimeterPx: Float,
    angleDegrees: Float,
): Float {
    if (perimeterPx <= 0f || !perimeterPx.isFinite()) return 0f
    return positiveModulo(distancePx / perimeterPx - perimeterPhaseFraction(angleDegrees))
}

/**
 * Closes a cyclic palette so interpolated mesh colors meet without a wrap seam.
 * (한국어) mesh 색이 순환 경계에서 이음새 없이 만나도록 팔레트를 닫습니다.
 */
internal fun closeGradientLoop(colors: List<Color>): List<Color> = when {
    colors.size == 1 -> colors + colors
    colors.first() != colors.last() -> colors + colors.first()
    else -> colors
}

/**
 * Samples the same equally-spaced cyclic palette used by the perimeter meshes.
 *
 * Why expose this internally: JVM tests must use exactly the same interpolation and
 * wrap behavior as the cached edge texture.
 *
 * (한국어) 둘레 mesh와 같은 등간격 순환 팔레트를 샘플합니다. JVM 테스트가 실제 테두리와
 * 동일한 보간/순환 규칙을 공유하도록 internal로 둡니다.
 */
internal fun cyclicColorAt(colors: List<Color>, fraction: Float): Int {
    if (colors.size == 1) return colors.first().toArgb()
    val scaled = positiveModulo(fraction) * colors.lastIndex
    val startIndex = floor(scaled).toInt().coerceIn(0, colors.lastIndex - 1)
    return lerp(
        colors[startIndex],
        colors[startIndex + 1],
        scaled - startIndex,
    ).toArgb()
}

private fun positiveModulo(value: Float): Float {
    val remainder = value % 1f
    return if (remainder < 0f) remainder + 1f else remainder
}

private data class PerimeterPoint(
    val fraction: Float,
    val x: Float,
    val y: Float,
)

private data class PerimeterMesh(
    val meshWidth: Int,
    val vertices: FloatArray,
    val fractions: FloatArray,
    val colors: IntArray,
)

/**
 * Cached color carrier that maps a tiny white bitmap around every path contour.
 *
 * Why `drawBitmapMesh`: unlike `drawVertices`, its hardware path is available on API
 * 26–28. The mesh supplies only the color field; a separate continuous outline mask
 * restores exact anti-aliased joins and applies blur once per whole contour.
 *
 * (한국어) 작은 흰 bitmap을 각 path contour 둘레로 매핑하는 캐시된 색상 carrier입니다.
 * drawVertices와 달리 API 26~28 하드웨어에서도 동작하고, 실제 AA/join/blur는 별도의
 * 연속 외곽선 mask가 담당하므로 contour 전체에 블러가 한 번만 적용됩니다.
 */
internal class PerimeterGradient(
    path: Path,
    colors: List<Color>,
    carrierWidthPx: Float,
) {
    private val palette = closeGradientLoop(colors)
    private val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.WHITE)
    }
    private val meshes = approximateContours(path).mapNotNull { points ->
        createRibbonMesh(points, carrierWidthPx)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
    }

    /**
     * Updates cached vertex colors without rebuilding geometry or triggering layout.
     * (한국어) geometry/layout은 건드리지 않고 캐시된 꼭짓점 색상만 갱신합니다.
     */
    fun applyPhase(angleDegrees: Float) {
        meshes.forEach { mesh ->
            val rowSize = mesh.meshWidth + 1
            repeat(rowSize) { index ->
                val color = cyclicColorAt(
                    palette,
                    perimeterGradientFraction(mesh.fractions[index], 1f, angleDegrees),
                )
                mesh.colors[index] = color
                mesh.colors[rowSize + index] = color
            }
        }
    }

    /**
     * Draws only the broad color carrier; [PerimeterMaskLayer] restores exact edges.
     * (한국어) 넓은 색상 carrier만 그리며 정확한 외곽선은 [PerimeterMaskLayer]가 복원합니다.
     */
    fun draw(canvas: Canvas) {
        meshes.forEach { mesh ->
            canvas.drawBitmapMesh(
                bitmap,
                mesh.meshWidth,
                1,
                mesh.vertices,
                0,
                mesh.colors,
                0,
                paint,
            )
        }
    }
}

/**
 * Composites a perimeter color carrier through one continuous outline mask.
 *
 * Why two nested layers: mask alpha (including BlurMaskFilter or API 26 fallback
 * strokes) is built once, while the mesh is combined as a single SRC_IN source. This
 * prevents per-segment blur bulbs and prevents translucent segment overlaps from
 * becoming brighter at corners.
 *
 * (한국어) 둘레 색상 carrier를 하나의 연속 외곽선 mask로 합성합니다. mask alpha를 먼저
 * 만든 뒤 mesh 전체를 한 번의 SRC_IN source로 교차시켜 선분별 blur 밝은 점과 반투명
 * 겹침을 막습니다.
 */
internal class PerimeterMaskLayer(
    private val gradient: PerimeterGradient,
    private val path: Path,
    private val maskPaints: List<Paint>,
    width: Float,
    height: Float,
    outsetPx: Float,
) {
    private val bounds = RectF(-outsetPx, -outsetPx, width + outsetPx, height + outsetPx)
    private val layerPaint = Paint()
    private val sourceInPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    /**
     * Applies one global alpha after intersecting the color field with the full mask.
     * (한국어) 전체 mask와 색상장을 교차한 뒤 global alpha를 한 번만 적용합니다.
     */
    fun draw(canvas: Canvas, alpha: Float) {
        layerPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        val maskLayer = canvas.saveLayer(bounds, layerPaint)
        maskPaints.forEach { paint -> canvas.drawPath(path, paint) }

        val colorLayer = canvas.saveLayer(bounds, sourceInPaint)
        gradient.draw(canvas)
        canvas.restoreToCount(colorLayer)
        canvas.restoreToCount(maskLayer)
    }
}

private fun approximateContours(path: Path): List<List<PerimeterPoint>> {
    val triples = path.approximate(ApproximationErrorPx)
    if (triples.size < 6) return emptyList()

    val contours = mutableListOf<MutableList<PerimeterPoint>>()
    var current = mutableListOf(
        PerimeterPoint(triples[0], triples[1], triples[2]),
    )
    for (index in 3 until triples.size step 3) {
        val next = PerimeterPoint(triples[index], triples[index + 1], triples[index + 2])
        val previous = current.last()
        val isContourMove = abs(next.fraction - previous.fraction) <= PointEpsilon &&
            squaredDistance(next, previous) > PointEpsilon
        if (isContourMove) {
            if (current.size >= 2) contours += current
            current = mutableListOf(next)
        } else {
            current += next
        }
    }
    if (current.size >= 2) contours += current

    return contours.mapNotNull(::normalizeAndSubdivideContour)
}

private fun normalizeAndSubdivideContour(points: List<PerimeterPoint>): List<PerimeterPoint>? {
    val startFraction = points.first().fraction
    val span = points.last().fraction - startFraction
    if (span <= PointEpsilon) return null

    val normalized = points.map { point ->
        point.copy(fraction = ((point.fraction - startFraction) / span).coerceIn(0f, 1f))
    }.toMutableList()
    if (squaredDistance(normalized.first(), normalized.last()) > PointEpsilon) {
        normalized += normalized.first().copy(fraction = 1f)
    }

    val result = ArrayList<PerimeterPoint>()
    normalized.zipWithNext().forEachIndexed { index, (start, end) ->
        if (index == 0) result += start
        val fractionSpan = end.fraction - start.fraction
        val steps = ceil(fractionSpan * SamplesPerLoop).toInt().coerceAtLeast(1)
        repeat(steps) { step ->
            val amount = (step + 1) / steps.toFloat()
            result += PerimeterPoint(
                fraction = start.fraction + fractionSpan * amount,
                x = start.x + (end.x - start.x) * amount,
                y = start.y + (end.y - start.y) * amount,
            )
        }
    }
    return result
}

private fun createRibbonMesh(
    points: List<PerimeterPoint>,
    carrierWidthPx: Float,
): PerimeterMesh? {
    if (points.size < 3) return null
    val meshWidth = points.lastIndex
    val rowSize = meshWidth + 1
    val vertices = FloatArray(rowSize * 4)
    val fractions = FloatArray(rowSize)
    val colors = IntArray(rowSize * 2)
    val halfWidth = carrierWidthPx.coerceAtLeast(1f) / 2f

    repeat(rowSize) { index ->
        val pointIndex = if (index == meshWidth) 0 else index
        val previousIndex = if (pointIndex == 0) meshWidth - 1 else pointIndex - 1
        val nextIndex = if (pointIndex == meshWidth - 1) 0 else pointIndex + 1
        val point = points[pointIndex]
        val previous = points[previousIndex]
        val next = points[nextIndex]
        val previousDirection = normalized(point.x - previous.x, point.y - previous.y)
        val nextDirection = normalized(next.x - point.x, next.y - point.y)
        val previousNormalX = -previousDirection.second
        val previousNormalY = previousDirection.first
        val nextNormalX = -nextDirection.second
        val nextNormalY = nextDirection.first
        val miter = normalized(previousNormalX + nextNormalX, previousNormalY + nextNormalY)
        val denominator = abs(miter.first * nextNormalX + miter.second * nextNormalY)
        val miterLength = min(halfWidth / denominator.coerceAtLeast(0.25f), halfWidth * 4f)
        val offsetX = miter.first * miterLength
        val offsetY = miter.second * miterLength

        vertices[index * 2] = point.x - offsetX
        vertices[index * 2 + 1] = point.y - offsetY
        val secondRowOffset = rowSize * 2 + index * 2
        vertices[secondRowOffset] = point.x + offsetX
        vertices[secondRowOffset + 1] = point.y + offsetY
        fractions[index] = if (index == meshWidth) 1f else point.fraction
    }
    return PerimeterMesh(meshWidth, vertices, fractions, colors)
}

private fun normalized(x: Float, y: Float): Pair<Float, Float> {
    val length = sqrt(x * x + y * y)
    return if (length <= PointEpsilon) 1f to 0f else x / length to y / length
}

private fun squaredDistance(first: PerimeterPoint, second: PerimeterPoint): Float {
    val dx = first.x - second.x
    val dy = first.y - second.y
    return dx * dx + dy * dy
}
