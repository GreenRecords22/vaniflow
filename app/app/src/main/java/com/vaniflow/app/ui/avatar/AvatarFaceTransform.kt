package com.vaniflow.app.ui.avatar

import kotlin.math.exp
import kotlin.math.max

/**
 * High-performance, mathematically exact transform for mapping 1024x1024 source image
 * coordinates to screen viewport coordinates and generating hardware-accelerated 2D
 * bitmap deformation mesh vertices for natural lip & jaw speech articulation.
 */
class AvatarFaceTransform(
    val meshWidth: Int = 20,
    val meshHeight: Int = 20
) {
    val totalVertices: Int = (meshWidth + 1) * (meshHeight + 1)
    val totalFloats: Int = totalVertices * 2

    // Reusable buffers to avoid per-frame GC allocations
    val baseVertices = FloatArray(totalFloats)
    val deformedVertices = FloatArray(totalFloats)

    fun calculateTransform(
        canvasW: Float,
        canvasH: Float,
        sourceW: Float = 1024f,
        sourceH: Float = 1024f
    ): FloatArray {
        val scale = max(canvasW / sourceW, canvasH / sourceH)
        val scaledW = sourceW * scale
        val scaledH = sourceH * scale
        val offsetX = (canvasW - scaledW) / 2f
        val offsetY = (canvasH - scaledH) / 2f
        return floatArrayOf(scale, scaledW, scaledH, offsetX, offsetY)
    }

    /**
     * Computes the deformed mesh vertices that animate the ACTUAL lips and jaw of the
     * portrait bitmap. Zero fake vector paths or secondary mouth overlays are drawn.
     */
    fun computeDeformedMesh(
        canvasW: Float,
        canvasH: Float,
        mouthCenterX: Float,
        mouthCenterY: Float,
        openness: Float,
        sourceW: Float = 1024f,
        sourceH: Float = 1024f
    ): FloatArray {
        val transform = calculateTransform(canvasW, canvasH, sourceW, sourceH)
        val scale = transform[0]
        val scaledW = transform[1]
        val scaledH = transform[2]
        val offsetX = transform[3]
        val offsetY = transform[4]

        val mCenterX = offsetX + (scaledW * mouthCenterX)
        val mCenterY = offsetY + (scaledH * mouthCenterY)

        // Gaussian influence radius around the mouth & jaw
        val sigmaX = scaledW * 0.11f
        val sigmaY = scaledH * 0.075f
        val maxJawDrop = scaledH * 0.038f * openness.coerceIn(0f, 1f)
        val maxUpperLipLift = scaledH * 0.010f * openness.coerceIn(0f, 1f)

        var index = 0
        for (yIndex in 0..meshHeight) {
            val normY = yIndex.toFloat() / meshHeight
            val screenY = offsetY + (scaledH * normY)
            val dyNorm = (screenY - mCenterY) / sigmaY

            for (xIndex in 0..meshWidth) {
                val normX = xIndex.toFloat() / meshWidth
                val screenX = offsetX + (scaledW * normX)
                val dxNorm = (screenX - mCenterX) / sigmaX

                val distSq = (dxNorm * dxNorm) + (dyNorm * dyNorm)

                if (openness > 0.02f && distSq < 5.0f) {
                    val weight = exp(-0.5f * distSq)

                    val vertexDisplacementY = if (screenY >= mCenterY) {
                        // Lower lip and jaw smoothly move down with speech energy
                        maxJawDrop * weight
                    } else {
                        // Upper lip slightly lifts
                        -maxUpperLipLift * weight
                    }

                    // Subtle horizontal mouth corner compression/expansion
                    val vertexDisplacementX = if (dxNorm != 0f) {
                        (screenX - mCenterX) * 0.04f * openness * weight
                    } else 0f

                    deformedVertices[index] = screenX + vertexDisplacementX
                    deformedVertices[index + 1] = screenY + vertexDisplacementY
                } else {
                    deformedVertices[index] = screenX
                    deformedVertices[index + 1] = screenY
                }
                index += 2
            }
        }
        return deformedVertices
    }
}