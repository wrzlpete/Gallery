package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.davemorrissey.labs.subscaleview.ImageDecoder

class MyGlideImageDecoder : ImageDecoder {

    override fun decode(context: Context, uri: Uri): Bitmap {
        val newUri = Uri.parse(uri.toString().replace("%", "%25").replace("#", "%23"))
        val inputStream = context.contentResolver.openInputStream(newUri)
            ?: throw RuntimeException("Cannot open input stream for $uri")
        inputStream.use { stream ->
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeStream(stream, null, options)
                ?: throw RuntimeException("BitmapFactory returned null bitmap - image format may not be supported")
        }
    }
}
