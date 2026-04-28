package kr.ac.kopo.talkti.utils

import android.content.Context
import android.provider.Settings

object PermissionUtils {

    /**
     * 오버레이(다른 앱 위에 표시) 권한 상태 확인
     */
    fun hasOverlayPermission(context: Context): Boolean {
        // 안드로이드 마시멜로(6.0) 이상에서 제공되는 오버레이 권한을 확인합니다.
        return Settings.canDrawOverlays(context)
    }

    /**
     * 현재 앱의 접근성 권한이 활성화되어 있는지 확인
     */
    fun hasAccessibilityPermission(context: Context): Boolean {
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }

        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            // 현재 패키지명이 허용된 접근성 서비스 목록에 포함되어 있다면 true 반환
            if (settingValue?.contains(context.packageName) == true) {
                return true
            }
        }
        return false
    }
}
