package com.yamahamx.editor.ui.util

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.InputStream

/**
 * Utilitário profissional de carregamento e redução de resolução de bitmaps (Downsampling).
 *
 * Implementa a estratégia recomendada pelo Android Developers para contenção de uso de memória
 * e eliminação de alertas de desempenho do Google Play Console:
 *
 * 1. Primeiro passo: leitura apenas de metadados (`inJustDecodeBounds = true`) sem alocar memória de pixels na JVM.
 * 2. Cálculo dinâmico do `inSampleSize` como potência de 2, respeitando as dimensões máximas necessárias em tela.
 * 3. Segundo passo: decodificação definitiva com alocação subamostrada (`inJustDecodeBounds = false`).
 */
object BitmapDownsampler {

    private const val TAG = "BitmapDownsampler"

    /**
     * Calcula o valor de inSampleSize como potência de 2 baseado nas dimensões originais e desejadas.
     * Retorna a maior potência de 2 que mantém a imagem maior ou igual aos limites requisitados.
     */
    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val rawHeight = options.outHeight
        val rawWidth = options.outWidth
        var inSampleSize = 1

        if (reqWidth <= 0 || reqHeight <= 0) {
            return inSampleSize
        }

        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2

            // Calcular a maior potência de 2 que seja <= às metades da largura e altura
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Decodifica um recurso Drawable/Bitmap com redução segura de resolução.
     */
    fun decodeSampledBitmapFromResource(
        res: Resources,
        resId: Int,
        reqWidth: Int,
        reqHeight: Int,
        preferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        return try {
            // Passo 1: Obter apenas dimensões
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeResource(res, resId, options)

            // Passo 2: Calcular inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Passo 3: Decodificar com subamostragem
            options.inJustDecodeBounds = false
            options.inPreferredConfig = preferredConfig
            BitmapFactory.decodeResource(res, resId, options)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError ao decodificar recurso $resId", oom)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao decodificar recurso $resId", e)
            null
        }
    }

    /**
     * Decodifica um arquivo do sistema de arquivos com redução segura de resolução.
     */
    fun decodeSampledBitmapFromFile(
        pathName: String,
        reqWidth: Int,
        reqHeight: Int,
        preferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        return try {
            // Passo 1: Obter apenas dimensões
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(pathName, options)

            // Passo 2: Calcular inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Passo 3: Decodificar com subamostragem
            options.inJustDecodeBounds = false
            options.inPreferredConfig = preferredConfig
            BitmapFactory.decodeFile(pathName, options)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError ao decodificar arquivo $pathName", oom)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao decodificar arquivo $pathName", e)
            null
        }
    }

    /**
     * Decodifica um ByteArray com redução segura de resolução.
     */
    fun decodeSampledBitmapFromByteArray(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size,
        reqWidth: Int,
        reqHeight: Int,
        preferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        return try {
            // Passo 1: Obter apenas dimensões
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(data, offset, length, options)

            // Passo 2: Calcular inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Passo 3: Decodificar com subamostragem
            options.inJustDecodeBounds = false
            options.inPreferredConfig = preferredConfig
            BitmapFactory.decodeByteArray(data, offset, length, options)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError ao decodificar byte array", oom)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao decodificar byte array", e)
            null
        }
    }

    /**
     * Decodifica um Stream com redução segura de resolução.
     * Recebe uma função provider para reabrir o Stream no Passo 2 sem corromper a leitura.
     */
    fun decodeSampledBitmapFromStream(
        streamProvider: () -> InputStream?,
        reqWidth: Int,
        reqHeight: Int,
        preferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        return try {
            // Passo 1: Obter apenas dimensões
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            streamProvider()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            // Passo 2: Calcular inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Passo 3: Decodificar com subamostragem
            options.inJustDecodeBounds = false
            options.inPreferredConfig = preferredConfig
            streamProvider()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError ao decodificar stream", oom)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao decodificar stream", e)
            null
        }
    }
}
