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
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val ApproximationErrorPx = 0.5f
private const val SamplesPerLoop = 192
private const val PointEpsilon = 1e-4f
private const val BlurRadiusToSigma = 0.57735f
private const val FillCarrierOutsetPx = 2f

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
 * Returns the actual one-sided support of Android's native blur mask.
 *
 * Why derive it instead of multiplying the requested radius by an arbitrary safety
 * factor: `BlurMaskFilter` converts radius to Skia sigma and allocates a 6-sigma
 * kernel. Matching that 3-sigma margin keeps the color ribbon large enough for every
 * non-zero mask pixel without the previous four-radius overreach.
 *
 * (한국어) Android 네이티브 블러 mask가 한쪽으로 차지하는 실제 범위를 계산합니다.
 * 임의의 배수를 더하지 않고 Skia의 radius→sigma 변환과 6-sigma 커널에 맞춰 3-sigma
 * 여백만 잡아 mask 픽셀을 빠뜨리지 않으면서 기존의 과도한 4배 반경 여백을 없앱니다.
 */
internal fun nativeBlurMaskOutsetPx(blurRadiusPx: Float): Float {
    if (blurRadiusPx <= 0f || !blurRadiusPx.isFinite()) return 0f
    return ceil(3f * (BlurRadiusToSigma * blurRadiusPx + 0.5f))
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
 * Mixes one cyclic palette into the phase-invariant color used at a surface center.
 *
 * Why the duplicated closing stop is excluded: it occupies the same 0/1 position as
 * the first stop and must not receive double weight. RGB channels are averaged after
 * premultiplication so translucent palettes do not leak color from transparent stops.
 *
 * (한국어) 순환 팔레트를 background 중심에 쓸 하나의 phase 불변 색으로 섞습니다.
 * 순환을 닫으려고 복제한 마지막 색은 첫 색과 같은 0/1 위치이므로 중복 가중하지 않고,
 * 반투명 색의 RGB가 새지 않도록 premultiplied 상태에서 평균을 냅니다.
 */
internal fun mixedGradientColorArgb(colors: List<Color>): Int {
    val stops = if (colors.size > 1 && colors.first() == colors.last()) colors.dropLast(1) else colors
    var alphaSum = 0L
    var premultipliedRedSum = 0L
    var premultipliedGreenSum = 0L
    var premultipliedBlueSum = 0L
    stops.forEach { color ->
        val argb = color.toArgb()
        val alpha = argb ushr 24
        alphaSum += alpha
        premultipliedRedSum += ((argb ushr 16) and 0xFF).toLong() * alpha
        premultipliedGreenSum += ((argb ushr 8) and 0xFF).toLong() * alpha
        premultipliedBlueSum += (argb and 0xFF).toLong() * alpha
    }
    val alpha = (alphaSum.toFloat() / stops.size).roundToInt()
    if (alphaSum == 0L) return 0
    val red = (premultipliedRedSum.toFloat() / alphaSum).roundToInt()
    val green = (premultipliedGreenSum.toFloat() / alphaSum).roundToInt()
    val blue = (premultipliedBlueSum.toFloat() / alphaSum).roundToInt()
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
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

/**
 * Updates only the animated perimeter rows of one cached mesh color buffer.
 *
 * Why this stays pure and array-based: the draw loop mutates its existing cache with
 * no allocation, while JVM tests can lock down the rule that a surface's trailing
 * center row never changes with phase.
 *
 * (한국어) 캐시된 mesh 색상 버퍼에서 움직이는 둘레 행만 갱신합니다. draw loop는 새 객체
 * 없이 기존 배열을 갱신하고, JVM 테스트는 surface의 마지막 중심 행이 phase에 따라
 * 바뀌지 않는다는 계약을 직접 검증할 수 있습니다.
 */
internal fun applyPerimeterPhaseColors(
    palette: List<Color>,
    fractions: FloatArray,
    phaseRowCount: Int,
    target: IntArray,
    angleDegrees: Float,
) {
    val rowSize = fractions.size
    repeat(rowSize) { index ->
        val color = cyclicColorAt(
            palette,
            perimeterGradientFraction(fractions[index], 1f, angleDegrees),
        )
        repeat(phaseRowCount) { row ->
            target[row * rowSize + index] = color
        }
    }
}

/**
 * Reports whether one non-duplicated polygon loop is convex.
 *
 * Why this is pure: inward halo geometry can choose the seam-free center fan for
 * built-in convex shapes while retaining a contour-following ribbon fallback for
 * custom concave shapes, and the choice stays JVM-testable.
 *
 * (한국어) 마지막 점을 중복하지 않은 polygon loop가 볼록한지 판별합니다. 기본 볼록 shape는
 * 이음새 없는 중심 fan을 쓰고, 오목한 커스텀 shape는 둘레 ribbon으로 폴백하는 분기를
 * JVM 테스트로 검증할 수 있도록 순수 함수로 둡니다.
 */
internal fun polygonIsConvex(coordinates: FloatArray): Boolean {
    if (coordinates.size < 6 || coordinates.size % 2 != 0) return false
    val pointCount = coordinates.size / 2
    var turnSign = 0
    repeat(pointCount) { index ->
        val nextIndex = (index + 1) % pointCount
        val afterNextIndex = (index + 2) % pointCount
        val ax = coordinates[index * 2]
        val ay = coordinates[index * 2 + 1]
        val bx = coordinates[nextIndex * 2]
        val by = coordinates[nextIndex * 2 + 1]
        val cx = coordinates[afterNextIndex * 2]
        val cy = coordinates[afterNextIndex * 2 + 1]
        val cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
        if (abs(cross) > PointEpsilon) {
            val currentSign = if (cross > 0f) 1 else -1
            if (turnSign != 0 && currentSign != turnSign) return false
            turnSign = currentSign
        }
    }
    return turnSign != 0
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
    val meshHeight: Int,
    val phaseRowCount: Int,
    val vertices: FloatArray,
    val fractions: FloatArray,
    val colors: IntArray,
)

/**
 * Cached color carrier for both perimeter ribbons and perimeter-origin surface fans.
 *
 * Why one carrier supports both geometries: ring, halo, bloom, and surface fill share
 * the same palette phase and bitmap-mesh draw path. Only the cached rows differ, so
 * named geometry factories avoid duplicating animation and rendering code.
 *
 * Why `drawBitmapMesh`: unlike `drawVertices`, its hardware path is available on API
 * 26–28. The mesh supplies only the color field; a separate continuous outline mask
 * restores exact anti-aliased joins and applies blur once per whole contour.
 *
 * (한국어) 둘레 ribbon과 테두리 기점 surface fan을 함께 처리하는 캐시 색상
 * carrier입니다. 링·halo·bloom·surface는 같은 palette phase와 bitmap-mesh draw 경로를
 * 쓰고 캐시된 행만 다르므로, 이름 있는 geometry factory로 애니메이션·렌더링 코드 중복을
 * 피합니다.
 * drawVertices와 달리 drawBitmapMesh는 API 26~28 하드웨어에서도 동작하며, 실제
 * AA/join/blur는 별도의 연속 외곽선 mask가 담당합니다.
 */
internal class PerimeterGradient private constructor(
    colors: List<Color>,
    private val meshes: List<PerimeterMesh>,
    private val centerFillColor: Int?,
) {
    constructor(
        path: Path,
        colors: List<Color>,
        carrierWidthPx: Float,
    ) : this(
        colors = colors,
        meshes = approximateContours(path).mapNotNull { points ->
            createRibbonMesh(points, carrierWidthPx)
        },
        centerFillColor = null,
    )

    /**
     * Named construction keeps direction-specific geometries explicit at call sites.
     * (한국어) 호출부에서 방향별 geometry를 명확히 구분하도록 이름 있는 생성 경로를 둡니다.
     */
    companion object {
        /**
         * Creates a one-sided carrier for a single convex contour so broad blur cannot
         * reach the opposite exterior. Concave and multi-contour paths keep the
         * symmetric fallback so path fill rules select each filled side.
         *
         * (한국어) 단일 볼록 contour에는 넓은 blur가 반대편 바깥까지 닿지 않는 단방향
         * carrier를 만듭니다. 오목하거나 contour가 여러 개인 path는 fill rule이 채워진
         * 쪽을 고르도록 대칭 ribbon 폴백을 유지합니다.
         */
        fun outward(path: Path, colors: List<Color>, carrierOutsetPx: Float): PerimeterGradient {
            val contours = approximateContours(path)
            val meshes = if (contours.size == 1 && isConvexContour(contours.single())) {
                listOfNotNull(
                    createOneSidedRibbonMesh(
                        contours.single(),
                        carrierOutsetPx,
                        pointsOutward = true,
                    ),
                )
            } else {
                contours.mapNotNull { points ->
                    createRibbonMesh(points, carrierOutsetPx * 2f)
                }
            }
            return PerimeterGradient(
                colors = colors,
                meshes = meshes,
                centerFillColor = null,
            )
        }

        /**
         * Creates an inward carrier without narrowing the edge API's custom-shape
         * support. A single convex contour gets the seamless center fan; other paths
         * retain symmetric contour ribbons so their fill rule selects the correct
         * inward side, with the mixed palette color filling any deeper blur support.
         *
         * (한국어) 테두리 API의 커스텀 shape 지원을 좁히지 않는 안쪽 carrier를 만듭니다.
         * 단일 볼록 contour는 중심 fan을 쓰고, 그 밖의 path는 fill rule이 올바른 안쪽을
         * 고르도록 대칭 contour ribbon을 유지하며 더 깊은 blur 영역은 혼합색으로 채웁니다.
         */
        fun inward(path: Path, colors: List<Color>, carrierInsetPx: Float): PerimeterGradient {
            val centerColor = mixedGradientColorArgb(colors)
            val contours = approximateContours(path)
            val meshes = if (contours.size == 1 && isConvexContour(contours.single())) {
                listOfNotNull(createFillMesh(contours.single(), centerColor))
            } else {
                contours.mapNotNull { points ->
                    createRibbonMesh(points, carrierInsetPx * 2f)
                }
            }
            return PerimeterGradient(
                colors = colors,
                meshes = meshes,
                centerFillColor = centerColor,
            )
        }

        /**
         * Creates a boundary-to-center fan for a single-contour convex surface.
         * (한국어) 단일 외곽선 볼록 surface용 경계→중심 fan을 만듭니다.
         */
        fun surface(path: Path, colors: List<Color>): PerimeterGradient {
            val centerColor = mixedGradientColorArgb(colors)
            return PerimeterGradient(
                colors = colors,
                meshes = approximateContours(path).mapNotNull { points ->
                    createFillMesh(points, centerColor)
                },
                centerFillColor = centerColor,
            )
        }
    }

    private val palette = closeGradientLoop(colors)
    private val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.WHITE)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
        if (centerFillColor != null) xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }

    /**
     * Updates cached perimeter rows without rebuilding geometry or triggering layout.
     * (한국어) geometry/layout은 건드리지 않고 캐시된 둘레 행의 색만 갱신합니다.
     */
    fun applyPhase(angleDegrees: Float) {
        meshes.forEach { mesh ->
            applyPerimeterPhaseColors(
                palette = palette,
                fractions = mesh.fractions,
                phaseRowCount = mesh.phaseRowCount,
                target = mesh.colors,
                angleDegrees = angleDegrees,
            )
        }
    }

    /**
     * Draws only the color carrier; [PerimeterMaskLayer] restores exact edges.
     * (한국어) 색상 carrier만 그리며 정확한 외곽선은 [PerimeterMaskLayer]가 복원합니다.
     */
    fun draw(canvas: Canvas) {
        // Fill the temporary color layer, not the AA outline itself. The outline mask
        // applies edge coverage once at composition time; drawing the same AA path here
        // would square coverage on any fringe the fan misses.
        // (한국어) 임시 색상 레이어 전체를 채우고 외곽선 AA는 최종 mask에서 한 번만
        // 적용합니다. 여기서 같은 AA path를 그리면 fan이 놓친 fringe의 coverage가
        // 제곱되어 가장자리가 불필요하게 흐려집니다.
        centerFillColor?.let { color -> canvas.drawColor(color) }
        meshes.forEach { mesh ->
            canvas.drawBitmapMesh(
                bitmap,
                mesh.meshWidth,
                mesh.meshHeight,
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
        val point = points[pointIndex]
        val offset = contourMiterOffset(points, pointIndex, halfWidth)

        vertices[index * 2] = point.x - offset.first
        vertices[index * 2 + 1] = point.y - offset.second
        val secondRowOffset = rowSize * 2 + index * 2
        vertices[secondRowOffset] = point.x + offset.first
        vertices[secondRowOffset + 1] = point.y + offset.second
        fractions[index] = if (index == meshWidth) 1f else point.fraction
    }
    return PerimeterMesh(meshWidth, 1, 2, vertices, fractions, colors)
}

private fun createOneSidedRibbonMesh(
    points: List<PerimeterPoint>,
    carrierOutsetPx: Float,
    pointsOutward: Boolean,
): PerimeterMesh? {
    if (points.size < 3) return null
    val meshWidth = points.lastIndex
    val rowSize = meshWidth + 1
    val vertices = FloatArray(rowSize * 4)
    val fractions = FloatArray(rowSize)
    val colors = IntArray(rowSize * 2)
    val outwardDirection = if (signedArea(points) >= 0f) -1f else 1f
    val direction = if (pointsOutward) outwardDirection else -outwardDirection

    repeat(rowSize) { index ->
        val pointIndex = if (index == meshWidth) 0 else index
        val point = points[pointIndex]
        val offset = contourMiterOffset(points, pointIndex, carrierOutsetPx.coerceAtLeast(1f))

        vertices[index * 2] = point.x
        vertices[index * 2 + 1] = point.y
        val outerRowOffset = rowSize * 2 + index * 2
        vertices[outerRowOffset] = point.x + offset.first * direction
        vertices[outerRowOffset + 1] = point.y + offset.second * direction
        fractions[index] = if (index == meshWidth) 1f else point.fraction
    }
    return PerimeterMesh(meshWidth, 1, 2, vertices, fractions, colors)
}

private fun createFillMesh(points: List<PerimeterPoint>, centerColor: Int): PerimeterMesh? {
    if (points.size < 3) return null
    val meshWidth = points.lastIndex
    val rowSize = meshWidth + 1
    val vertices = FloatArray(rowSize * 6)
    val fractions = FloatArray(rowSize)
    val colors = IntArray(rowSize * 3)
    val centerX = points.take(meshWidth).sumOf { it.x.toDouble() }.toFloat() / meshWidth
    val centerY = points.take(meshWidth).sumOf { it.y.toDouble() }.toFloat() / meshWidth

    repeat(rowSize) { index ->
        val pointIndex = if (index == meshWidth) 0 else index
        val point = points[pointIndex]
        val offset = contourMiterOffset(points, pointIndex, FillCarrierOutsetPx)
        val pointsOutward = offset.first * (point.x - centerX) +
            offset.second * (point.y - centerY) >= 0f
        val direction = if (pointsOutward) 1f else -1f

        vertices[index * 2] = point.x + offset.first * direction
        vertices[index * 2 + 1] = point.y + offset.second * direction

        val boundaryOffset = rowSize * 2 + index * 2
        vertices[boundaryOffset] = point.x
        vertices[boundaryOffset + 1] = point.y

        val centerOffset = rowSize * 4 + index * 2
        vertices[centerOffset] = centerX
        vertices[centerOffset + 1] = centerY

        fractions[index] = if (index == meshWidth) 1f else point.fraction
        colors[rowSize * 2 + index] = centerColor
    }
    return PerimeterMesh(meshWidth, 2, 2, vertices, fractions, colors)
}

private fun isConvexContour(points: List<PerimeterPoint>): Boolean {
    val meshWidth = points.lastIndex
    val coordinates = FloatArray(meshWidth * 2)
    repeat(meshWidth) { index ->
        coordinates[index * 2] = points[index].x
        coordinates[index * 2 + 1] = points[index].y
    }
    return polygonIsConvex(coordinates)
}

private fun contourMiterOffset(
    points: List<PerimeterPoint>,
    pointIndex: Int,
    outsetPx: Float,
): Pair<Float, Float> {
    val meshWidth = points.lastIndex
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
    val miterLength = min(outsetPx / denominator.coerceAtLeast(0.25f), outsetPx * 4f)
    return miter.first * miterLength to miter.second * miterLength
}

private fun signedArea(points: List<PerimeterPoint>): Float {
    val meshWidth = points.lastIndex
    var twiceArea = 0f
    repeat(meshWidth) { index ->
        val current = points[index]
        val next = points[if (index == meshWidth - 1) 0 else index + 1]
        twiceArea += current.x * next.y - next.x * current.y
    }
    return twiceArea / 2f
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
