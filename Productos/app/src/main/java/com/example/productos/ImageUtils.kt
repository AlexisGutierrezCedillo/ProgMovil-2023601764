package com.example.productos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

fun getDefaultBase64(ctx: Context): String {
    // Decodificamos un drawable del sistema
    val orig: Bitmap = BitmapFactory.decodeResource(
        ctx.resources,
        android.R.drawable.ic_menu_gallery
    )
    // Escalamos al tamaño de almacenamiento
    val scaled: Bitmap = Bitmap.createScaledBitmap(orig, 1100, 1100, true)
    // Convertimos a JPEG y codificamos en Base64
    return ByteArrayOutputStream().use { baos ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }
}
