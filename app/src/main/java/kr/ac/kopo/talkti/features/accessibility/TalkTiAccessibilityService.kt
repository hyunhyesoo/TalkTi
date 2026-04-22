package kr.ac.kopo.talkti.features.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

class TalkTiAccessibilityService : AccessibilityService() {

    companion object {
        var instance: TalkTiAccessibilityService? = null

        fun extractScreenTreeJson(): String? {
            val rootNode = instance?.rootInActiveWindow ?: return null
            val jsonTree = extractNodeTree(rootNode)
            return jsonTree?.toString()
        }

        private fun extractNodeTree(node: AccessibilityNodeInfo?): JSONObject? {
            if (node == null) return null

            val isClickable = node.isClickable
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            val resId = node.viewIdResourceName

            val children = JSONArray()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                val childJson = extractNodeTree(child)
                if (childJson != null) {
                    children.put(childJson)
                }
                child?.recycle()
            }

            // 의미 있는 노드인지 판별 (글자, 클릭 가능 여부, ID 존재)
            val isMeaningfulInfo = !text.isNullOrBlank() || isClickable || !resId.isNullOrBlank()
            val hasMeaningfulChildren = children.length() > 0

            // 자신도 의미 없고, 자식도 의미 없으면 트리에서 제외 (null 반환)
            if (!isMeaningfulInfo && !hasMeaningfulChildren) {
                return null
            }

            val obj = JSONObject()
            if (!resId.isNullOrBlank()) obj.put("resourceId", resId)
            if (!text.isNullOrBlank()) obj.put("text", text)
            obj.put("isClickable", isClickable)

            val rect = Rect()
            node.getBoundsInScreen(rect)

            val boundsObj = JSONObject()
            boundsObj.put("left", rect.left)
            boundsObj.put("top", rect.top)
            boundsObj.put("right", rect.right)
            boundsObj.put("bottom", rect.bottom)
            boundsObj.put("centerX", (rect.left + rect.right) / 2)
            boundsObj.put("centerY", (rect.top + rect.bottom) / 2)

            obj.put("bounds", boundsObj)

            if (hasMeaningfulChildren) {
                obj.put("children", children)
            }

            return obj
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 이벤트 처리 (현재는 주기별로 캡처 서비스에서 불러오므로 생략)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
