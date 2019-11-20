package com.creoit.docscanner.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.creoit.docscanner.custom.BaseActivity
import splitties.init.appCtx
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


fun BaseActivity.convertToPdf(
    context: Context,
    mediaFiles: List<MediaFile>,
    filename: String?
): PdfDocument {
    val document = PdfDocument()

    mediaFiles.forEachIndexed { index, mediaFile ->
        val scalingHeight = 500 * (mediaFile.uri.getDimens(DimensionType.DIMEN_HEIGHT, context)
            ?: 0) / mediaFile.uri.getDimens(DimensionType.DIMEN_WIDTH, context)
        val file = mediaFiles.maxBy { it.width } ?: mediaFiles[0]
        document.startPage(
            PdfDocument.PageInfo.Builder(
                mediaFile.width,
                mediaFile.height,
                index
            ).create()
        )?.let { page ->
            mediaFile.uri.getRotationFixedImage()?.let {
                page.canvas.drawBitmap(
                    it,
                    0f,
                    0f,
                    null
                )
            }
            document.finishPage(page)
        }
    }
    context.openFileOutput(filename, Context.MODE_PRIVATE)?.use {
        document.writeTo(it)
    }

    return document
}

private fun AppCompatActivity.getImageSize(uri: Uri?, dimensionType: DimensionType): Int {
    val options = android.graphics.BitmapFactory.Options()
    options.inJustDecodeBounds = true
    uri?.let {
        android.graphics.BitmapFactory.decodeStream(contentResolver.openInputStream(uri), null, options)
        return when (dimensionType) {
            DimensionType.DIMEN_WIDTH -> options.outWidth
            else -> options.outHeight
        }
    }
    return options.outHeight
}

fun Uri.getDimens(dimenType: DimensionType, context: Context): Int {
    val path = getPath(context)
    val ei = ExifInterface(path)
    val orientation = ei.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_UNDEFINED
    )

    val bitmap = BitmapFactory.decodeFile(path)

    return when (orientation) {

        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270 -> if(dimenType == DimensionType.DIMEN_WIDTH) bitmap.height else bitmap.width

        else -> if(dimenType == DimensionType.DIMEN_WIDTH) bitmap.width else bitmap.height
    }
}

suspend fun getGlideBitmap(uri: Uri) = suspendCoroutine<Bitmap?> {

   Glide.with(appCtx).asBitmap().load(uri).addListener(
        object : RequestListener<Bitmap> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Bitmap>?,
                isFirstResource: Boolean
            ): Boolean {
                it.resume(null)
                return true
            }

            override fun onResourceReady(
                resource: Bitmap?,
                model: Any?,
                target: Target<Bitmap>?,
                dataSource: DataSource?,
                isFirstResource: Boolean
            ): Boolean {
                it.resume(resource)
                return true
            }

        }).submit()
}

fun Uri.getRotationFixedImage(): Bitmap? {
    val path = getPath(appCtx)
    return path?.let {
        val ei = ExifInterface(path)
        val orientation = ei.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        val bitmap = BitmapFactory.decodeFile(path)

        when (orientation) {

            ExifInterface.ORIENTATION_ROTATE_90 -> bitmap.rotate(90f)

            ExifInterface.ORIENTATION_ROTATE_180 -> bitmap.rotate(180f)

            ExifInterface.ORIENTATION_ROTATE_270 -> bitmap.rotate(270f)

            ExifInterface.ORIENTATION_NORMAL -> bitmap
            else -> bitmap
        }
    }
}

/**
 * Get a file path from a Uri. This will get the the path for Storage Access
 * Framework Documents, as well as the _data field for the MediaStore and
 * other file-based ContentProviders.
 *
 * @param context The context.
 * @param uri The Uri to query.
 * @author paulburke
 */
fun Uri.getPath(context: Context): String? {

    val uri = this

    // DocumentProvider
    if (DocumentsContract.isDocumentUri(context, this)) {
        // ExternalStorageProvider
        if (isExternalStorageDocument(uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":").toTypedArray()
            val type = split[0]

            if ("primary".equals(type, ignoreCase = true)) {
                return Environment.getExternalStorageDirectory().absolutePath + "/" + split[1]
            }

            // TODO handle non-primary volumes
        } else if (isDownloadsDocument(uri)) {

            val id = DocumentsContract.getDocumentId(uri)
            val contentUri = ContentUris.withAppendedId(
                Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)
            )

            return contentUri?.getDataColumn(null, null)
        } else if (isMediaDocument(uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":").toTypedArray()
            val type = split[0]

            var contentUri: Uri? = null
            when (type) {
                "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val selection = "_id=?"
            val selectionArgs = arrayOf(split[1])

            return contentUri?.getDataColumn(selection, selectionArgs)
        }// MediaProvider
        // DownloadsProvider
    } else if ("content".equals(uri.scheme, ignoreCase = true)) {
        return uri.getDataColumn(null, null)
    } else if ("file".equals(uri.scheme, ignoreCase = true)) {
        return uri.path
    }// File
    // MediaStore (and general)

    return null
}

fun isExternalStorageDocument(uri: Uri) = "com.android.externalstorage.documents" == uri.authority

fun isDownloadsDocument(uri: Uri) = "com.android.providers.downloads.documents" == uri.authority

fun isMediaDocument(uri: Uri) = "com.android.providers.media.documents" == uri.authority

/**
 * Get the value of the data column for this Uri. This is useful for
 * MediaStore Uris, and other file-based ContentProviders.
 *
 * @param selection (Optional) Filter used in the query.
 * @param selectionArgs (Optional) Selection arguments used in the query.
 * @return The value of the _data column, which is typically a file path.
 */
fun Uri.getDataColumn(
    selection: String?,
    selectionArgs: Array<String>?,
    contentResolver: ContentResolver = appCtx.contentResolver
): String? {

    var cursor: Cursor? = null
    val column = "_data"
    val projection = arrayOf(column)

    try {
        cursor = contentResolver.query(this, projection, selection, selectionArgs, null)
        if (cursor?.moveToFirst() == true) {
            val columnIndex = cursor.getColumnIndexOrThrow(column)
            return cursor.getString(columnIndex)
        }
    } finally {
        cursor?.close()
    }
    return null
}

enum class DimensionType {
    DIMEN_WIDTH, DIMEN_HEIGHT
}