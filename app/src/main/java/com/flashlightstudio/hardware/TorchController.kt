package com.flashlightstudio.hardware

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TorchController(context: Context) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val id: String? = runCatching {
        manager.cameraIdList.firstOrNull { cameraId ->
            val c = manager.getCameraCharacteristics(cameraId)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()
    private val _available = MutableStateFlow(id != null)
    val available: StateFlow<Boolean> = _available
    private val _isOn = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _isOn
    private val _strengthSupported = MutableStateFlow(false)
    val strengthSupported: StateFlow<Boolean> = _strengthSupported
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val strengthRange = runCatching {
        if (Build.VERSION.SDK_INT >= 33 && id != null) manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) else null
    }.getOrNull()

    init {
        _strengthSupported.value = Build.VERSION.SDK_INT >= 33 && (strengthRange ?: 1) > 1
        manager.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == id) _isOn.value = enabled }
            override fun onTorchModeUnavailable(cameraId: String) { if (cameraId == id) { _isOn.value = false; _error.value = "Camera is temporarily in use by another app" } }
        }, null)
    }
    fun setEnabled(enabled: Boolean, level: Int = strengthRange ?: 1) {
        val cameraId = id ?: run { _error.value = "This device has no camera flash"; return }
        try {
            if (enabled && Build.VERSION.SDK_INT >= 33 && (strengthRange ?: 1) > 1) manager.turnOnTorchWithStrengthLevel(cameraId, level.coerceIn(1, strengthRange!!))
            else manager.setTorchMode(cameraId, enabled)
            _isOn.value = enabled; _error.value = null
        } catch (e: SecurityException) { _isOn.value = false; _error.value = "Camera permission is required to use the flash" }
        catch (_: Exception) { _isOn.value = false; _error.value = "Flash unavailable. Another app may be using the camera." }
    }
    fun toggle() = setEnabled(!_isOn.value)
}
