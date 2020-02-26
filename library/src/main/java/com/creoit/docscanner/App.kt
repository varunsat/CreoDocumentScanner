package com.creoit.docscanner

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig

class App: Application(), CameraXConfig.Provider {

    /** @returns Camera2 default configuration */
    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }
}