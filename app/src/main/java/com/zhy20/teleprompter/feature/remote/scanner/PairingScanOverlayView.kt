package com.zhy20.teleprompter.feature.remote.scanner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.min

/** Draws the pairing-specific dimmed mask, rounded frame and scan indicator. */
class PairingScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(237, 143, 25)
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 10, 14, 15)
        style = Paint.Style.FILL
    }
    private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f * density
    }
    private val scanGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 10f * density
    }
    private val frame = RectF()
    private val maskPath = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val cornerPath = Path()
    private var scanProgress = 0.5f
    private val scanAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1_600L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { animation ->
            scanProgress = animation.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val requestedSize = 280f * density
        val size = min(requestedSize, min(width.toFloat(), height.toFloat()))
        if (size <= 0f) {
            frame.setEmpty()
            maskPath.reset()
            cornerPath.reset()
            scanLinePaint.shader = null
            scanGlowPaint.shader = null
            return
        }

        val left = (width - size) / 2f
        val top = (height - size) / 2f
        frame.set(left, top, left + size, top + size)
        val radius = 22f * density
        maskPath.apply {
            reset()
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addRoundRect(frame, radius, radius, Path.Direction.CW)
        }
        buildRoundedCornerPath(cornerPath, frame, radius, segmentLength = 34f * density)

        scanLinePaint.shader = LinearGradient(
            frame.left + 22f * density,
            0f,
            frame.right - 22f * density,
            0f,
            intArrayOf(Color.TRANSPARENT, Color.rgb(237, 143, 25), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        scanGlowPaint.shader = LinearGradient(
            frame.left + 22f * density,
            0f,
            frame.right - 22f * density,
            0f,
            intArrayOf(Color.TRANSPARENT, Color.argb(70, 237, 143, 25), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (frame.isEmpty) return
        canvas.drawPath(maskPath, maskPaint)
        canvas.drawPath(cornerPath, cornerPaint)
        val scanInset = 24f * density
        val scanY = frame.top + scanInset + (frame.height() - scanInset * 2f) * scanProgress
        canvas.drawLine(
            frame.left + 22f * density,
            scanY,
            frame.right - 22f * density,
            scanY,
            scanGlowPaint,
        )
        canvas.drawLine(
            frame.left + 22f * density,
            scanY,
            frame.right - 22f * density,
            scanY,
            scanLinePaint,
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startScanAnimation()
    }

    override fun onDetachedFromWindow() {
        scanAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) startScanAnimation() else scanAnimator.cancel()
    }

    private fun startScanAnimation() {
        if (!isAttachedToWindow || scanAnimator.isStarted) return
        if (ValueAnimator.areAnimatorsEnabled()) {
            scanAnimator.start()
        } else {
            scanProgress = 0.5f
            invalidate()
        }
    }

    private fun buildRoundedCornerPath(path: Path, rect: RectF, radius: Float, segmentLength: Float) {
        path.reset()

        path.moveTo(rect.left, rect.top + radius + segmentLength)
        path.lineTo(rect.left, rect.top + radius)
        path.quadTo(rect.left, rect.top, rect.left + radius, rect.top)
        path.lineTo(rect.left + radius + segmentLength, rect.top)

        path.moveTo(rect.right - radius - segmentLength, rect.top)
        path.lineTo(rect.right - radius, rect.top)
        path.quadTo(rect.right, rect.top, rect.right, rect.top + radius)
        path.lineTo(rect.right, rect.top + radius + segmentLength)

        path.moveTo(rect.right, rect.bottom - radius - segmentLength)
        path.lineTo(rect.right, rect.bottom - radius)
        path.quadTo(rect.right, rect.bottom, rect.right - radius, rect.bottom)
        path.lineTo(rect.right - radius - segmentLength, rect.bottom)

        path.moveTo(rect.left + radius + segmentLength, rect.bottom)
        path.lineTo(rect.left + radius, rect.bottom)
        path.quadTo(rect.left, rect.bottom, rect.left, rect.bottom - radius)
        path.lineTo(rect.left, rect.bottom - radius - segmentLength)
    }
}
