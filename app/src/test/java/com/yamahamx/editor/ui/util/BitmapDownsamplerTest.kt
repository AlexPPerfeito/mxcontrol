package com.yamahamx.editor.ui.util

import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapDownsamplerTest {

    @Test
    fun calculateInSampleSize_returns1_whenImageIsSmallerThanRequested() {
        val options = BitmapFactory.Options().apply {
            outWidth = 500
            outHeight = 500
        }
        val sampleSize = BitmapDownsampler.calculateInSampleSize(options, reqWidth = 1000, reqHeight = 1000)
        assertEquals(1, sampleSize)
    }

    @Test
    fun calculateInSampleSize_returns1_whenDimensionsMatch() {
        val options = BitmapFactory.Options().apply {
            outWidth = 1080
            outHeight = 2400
        }
        val sampleSize = BitmapDownsampler.calculateInSampleSize(options, reqWidth = 1080, reqHeight = 2400)
        assertEquals(1, sampleSize)
    }

    @Test
    fun calculateInSampleSize_returnsPowerOfTwo_whenImageIsLarger() {
        val options = BitmapFactory.Options().apply {
            outWidth = 4000
            outHeight = 3000
        }
        // 4000x3000 -> req 1000x750: 4x downsampling (inSampleSize = 4)
        val sampleSize = BitmapDownsampler.calculateInSampleSize(options, reqWidth = 1000, reqHeight = 750)
        assertEquals(4, sampleSize)
    }

    @Test
    fun calculateInSampleSize_handlesNonSquareAspectRatios() {
        val options = BitmapFactory.Options().apply {
            outWidth = 2000
            outHeight = 8000
        }
        // Height needs 4x, Width needs 2x -> Should pick 2 or 4 according to power of 2
        val sampleSize = BitmapDownsampler.calculateInSampleSize(options, reqWidth = 1000, reqHeight = 2000)
        assertEquals(2, sampleSize)
    }

    @Test
    fun calculateInSampleSize_returns1_forInvalidDimensions() {
        val options = BitmapFactory.Options().apply {
            outWidth = 2000
            outHeight = 2000
        }
        assertEquals(1, BitmapDownsampler.calculateInSampleSize(options, reqWidth = 0, reqHeight = 100))
        assertEquals(1, BitmapDownsampler.calculateInSampleSize(options, reqWidth = 100, reqHeight = -1))
    }
}
