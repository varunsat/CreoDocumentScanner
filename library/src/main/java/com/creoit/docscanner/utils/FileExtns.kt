package com.creoit.docscanner.utils

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.core.net.toUri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel


fun Context.getLocalUri(uri: Uri): Uri {
    val newFile = File(getOutputDirectory(this), "${System.currentTimeMillis()}.jpg")
    var outputChannel: FileChannel? = null
    var inputChannel: FileChannel? = null
    try {
        outputChannel = FileOutputStream(newFile).channel
        inputChannel = contentResolver.openAssetFileDescriptor(uri, "r")?.createInputStream()?.channel
        inputChannel?.transferTo(0, inputChannel.size(), outputChannel)
        inputChannel?.close()
    } finally {
        inputChannel?.close()
        outputChannel?.close()
    }
    return Uri.fromFile(newFile)
}