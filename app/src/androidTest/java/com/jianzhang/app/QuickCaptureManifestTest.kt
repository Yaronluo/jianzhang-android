package com.jianzhang.app

import android.Manifest
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jianzhang.app.quickcapture.QuickCaptureAccessibilityService
import com.jianzhang.app.quickcapture.QuickCaptureCollapseActivity
import com.jianzhang.app.quickcapture.QuickCaptureTileService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickCaptureManifestTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun tileService_isProtectedBySystemBindingPermission() {
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, QuickCaptureTileService::class.java),
            PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        )
        assertEquals(Manifest.permission.BIND_QUICK_SETTINGS_TILE, info.permission)
    }

    @Test
    fun accessibilityService_isPrivateAndProtected() {
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, QuickCaptureAccessibilityService::class.java),
            PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        )
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, info.permission)
        assertFalse(info.exported)
    }

    @Test
    fun collapseTrampoline_usesIsolatedEphemeralTask() {
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, QuickCaptureCollapseActivity::class.java),
            PackageManager.ComponentInfoFlags.of(0)
        )
        assertTrue(info.taskAffinity.isNullOrEmpty())
        assertFalse(info.exported)
        assertEquals(ActivityInfo.FLAG_NO_HISTORY, info.flags and ActivityInfo.FLAG_NO_HISTORY)
        assertEquals(ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS, info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS)
    }
}
