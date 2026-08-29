package com.vaniflow.app.ui.avatar

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Milestone 23: Native 2D Bitmap Mesh Lip-Sync & Face Transform.
 */
class Milestone23ActualLipSyncTest {

    private lateinit var faceTransform: AvatarFaceTransform

    @Before
    fun setup() {
        faceTransform = AvatarFaceTransform(meshWidth = 20, meshHeight = 20)
    }

    @Test
    fun testMeshDimensionsAndVertexBuffers() {
        assertEquals(20, faceTransform.meshWidth)
        assertEquals(20, faceTransform.meshHeight)
        assertEquals(441, faceTransform.totalVertices) // (20+1)*(20+1)
        assertEquals(882, faceTransform.totalFloats) // 441 * 2
        assertEquals(882, faceTransform.deformedVertices.size)
    }

    @Test
    fun testZeroDisplacementAtRest() {
        val raya = CharacterFacialRegistry.getProfile("raya")
        val verts = faceTransform.computeDeformedMesh(
            canvasW = 400f,
            canvasH = 300f,
            mouthCenterX = raya.mouthCenterX,
            mouthCenterY = raya.mouthCenterY,
            openness = 0.0f,
            sourceW = 1024f,
            sourceH = 1024f
        )

        // All floats must be finite numbers
        for (i in verts.indices) {
            assertFalse("Vertices must not be NaN", verts[i].isNaN())
            assertFalse("Vertices must not be Infinite", verts[i].isInfinite())
        }

        // At rest, top-left vertex should match offset exactly
        val transform = faceTransform.calculateTransform(400f, 300f, 1024f, 1024f)
        val expectedOffsetX = transform[3]
        val expectedOffsetY = transform[4]

        assertEquals(expectedOffsetX, verts[0], 0.01f)
        assertEquals(expectedOffsetY, verts[1], 0.01f)
    }

    @Test
    fun testSpeakingDisplacementAffectsMouthAndJawRegion() {
        val raya = CharacterFacialRegistry.getProfile("raya")
        val restVerts = faceTransform.computeDeformedMesh(
            canvasW = 400f,
            canvasH = 300f,
            mouthCenterX = raya.mouthCenterX,
            mouthCenterY = raya.mouthCenterY,
            openness = 0.0f
        ).clone()

        val speakingVerts = faceTransform.computeDeformedMesh(
            canvasW = 400f,
            canvasH = 300f,
            mouthCenterX = raya.mouthCenterX,
            mouthCenterY = raya.mouthCenterY,
            openness = 0.85f
        )

        var displacedCount = 0
        var maxDeltaY = 0f

        for (i in 0 until faceTransform.totalVertices) {
            val restY = restVerts[i * 2 + 1]
            val speakY = speakingVerts[i * 2 + 1]
            val deltaY = kotlin.math.abs(speakY - restY)

            if (deltaY > 0.05f) {
                displacedCount++
                if (deltaY > maxDeltaY) {
                    maxDeltaY = deltaY
                }
            }
        }

        assertTrue("Vertices in mouth zone must be deformed during speech", displacedCount > 5)
        assertTrue("Max displacement must be positive and bounded", maxDeltaY in 1.0f..30.0f)
    }

    @Test
    fun testAllFourFacialProfilesExist() {
        assertNotNull(CharacterFacialRegistry.getProfile("raya"))
        assertNotNull(CharacterFacialRegistry.getProfile("rudra"))
        assertNotNull(CharacterFacialRegistry.getProfile("adwaita"))
        assertNotNull(CharacterFacialRegistry.getProfile("shub"))
    }
}