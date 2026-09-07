// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
// Platformski ExifInterface, ne androidx: cita iz InputStream-a od API 24, a minSdk je
// 26. Jedna zavisnost manje za jednu jedinu stvar koja nam treba — citanje orijentacije.
import android.media.ExifInterface
import java.io.File
import java.util.UUID

/**
 * Fotografija stavke kataloga.
 *
 * Bira se kroz Android Photo Picker, koji **ne traži nijednu dozvolu** — zato slika i
 * postoji u aplikaciji koja inače nema pristup ničemu.
 *
 * Tri stvari koje moraju da se urade odmah, u ovom redosledu:
 *
 *  1. **Kopirati bajtove.** URI iz birača je privremena dozvola i neće preživeti ponovno
 *     pokretanje. Prikaz koji ga zadrži radi do prvog restarta i onda tiho ostane prazan.
 *  2. **Smanjiti.** Slika sa telefona je 4000 px i par megabajta; za sličicu koja
 *     razlikuje tri punjača dovoljno je 1024 px po dužoj ivici.
 *  3. **Skinuti EXIF.** Fotografija punjača nosi GPS koordinate stana u kome je slikana.
 *     Aplikacija koja se hvali da nema mrežu ne sme da čuva lokaciju bez razloga —
 *     a razloga nema.
 */
class PhotoStore(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    /**
     * Kopira sliku sa datog URI-ja u privatni prostor aplikacije.
     *
     * @return relativna putanja za `CatalogItem.photoPath`, ili `null` ako se ne može pročitati
     */
    fun import(uri: Uri): String? {
        val source = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decoded = BitmapFactory.decodeByteArray(
            source,
            0,
            source.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            },
        ) ?: return null

        // Orijentacija se PRIMENJUJE na piksele pre nego sto se EXIF odbaci, inace bi
        // slika snimljena uspravno ostala polozena zauvek.
        val rotated = applyOrientation(decoded, source)
        val scaled = scaleToMaxEdge(rotated)

        val name = "${UUID.randomUUID()}.jpg"
        File(directory, name).outputStream().use { out ->
            // JPEG zapisan iz Bitmap-a nema EXIF: nema orijentacije, nema GPS-a, nema
            // modela uredjaja. Skidanje metapodataka je posledica, ne poseban korak.
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        }
        if (scaled !== rotated) rotated.recycle()
        if (rotated !== decoded) decoded.recycle()
        scaled.recycle()

        return "photos/$name"
    }

    fun file(path: String): File = File(context.filesDir, path)

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        file(path).delete()
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > MAX_EDGE * 2) sample *= 2
        return sample
    }

    private fun scaleToMaxEdge(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_EDGE) return bitmap
        val ratio = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun applyOrientation(bitmap: Bitmap, source: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val MAX_EDGE = 1024
        const val QUALITY = 85
    }
}
