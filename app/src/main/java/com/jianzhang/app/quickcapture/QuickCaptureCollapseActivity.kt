package com.jianzhang.app.quickcapture

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/** A no-display system trampoline so TileService can use the platform shade-collapse contract. */
class QuickCaptureCollapseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val started = QuickCaptureAccessibilityService.connectedInstance?.requestQuickCapture() == true
        if (!started) Toast.makeText(this, "正在处理上一张账单", Toast.LENGTH_SHORT).show()
        finishAndRemoveTask()
        overridePendingTransition(0, 0)
    }
}
