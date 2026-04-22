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
            println("실행")
            if (node == null) return null
            println("바로 리턴")

            // 화면에 안 보이는 노드는 제외
            if (!node.isVisibleToUser) {
                return null
            }
            println("안 보이는 것들 제외")

            val isClickable = node.isClickable
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            val resId = node.viewIdResourceName
            val className = node.className?.toString()

            val children = JSONArray()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                val childJson = extractNodeTree(child)
                if (childJson != null) {
                    children.put(childJson)
                }
                child?.recycle()
            }
            println("요소 개수" + node.childCount)

            // 조건 완화: 거의 대부분의 가시적 노드를 허용하되, 완전히 텅 빈 불필요한 레이아웃만 제외
            // 자식이 있거나, 텍스트가 있거나, 클릭 가능하거나, ID가 있거나, Button/Image 타입인 경우
            val isMeaningfulInfo = !text.isNullOrBlank() || isClickable || !resId.isNullOrBlank() || 
                                   className?.contains("Button") == true || className?.contains("Image") == true
            val hasMeaningfulChildren = children.length() > 0

            // 너무 빈껍데기인 노드만 필터링 (원하시면 이 조건 자체를 주석처리하여 모든 노드 전송 가능)
            //if (!isMeaningfulInfo && !hasMeaningfulChildren) {
            //    return null
            //}

            val obj = JSONObject()
            if (!resId.isNullOrBlank()) obj.put("resourceId", resId)
            if (!text.isNullOrBlank()) obj.put("text", text)
            if (!className.isNullOrBlank()) obj.put("className", className)
            
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
