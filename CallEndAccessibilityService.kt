package com.example.studentphone

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CallEndAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: CallEndAccessibilityService? = null

        fun endCurrentCall() {
            instance?.endCall()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    private fun endCall() {
        val labels = listOf(
            "end call", "end", "hang up", "decline", "dismiss", "reject", "ignore",
            "समाप्त", "कॉल समाप्त", "काट", "अस्वीकार", "टालें", "टाल", "बंद करें"
        )
        Handler(Looper.getMainLooper()).postDelayed({
            val root = rootInActiveWindow
            if (root == null) {
                openPhoneApp()
                Handler(Looper.getMainLooper()).postDelayed({
                    val root2 = rootInActiveWindow ?: return@postDelayed
                    clickNode(findNode(root2, labels))
                }, 1200)
            } else {
                clickNode(findNode(root, labels))
            }
        }, 500)
    }

    private fun openPhoneApp() {
        val packages = listOf(
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.oneplus.dialer",
            "com.android.incallui"
        )
        for (pkg in packages) {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                try {
                    startActivity(intent)
                    return
                } catch (e: Exception) { }
            }
        }
    }

    private fun findNode(node: AccessibilityNodeInfo?, labels: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (labels.any { text.contains(it) || desc.contains(it) }) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNode(child, labels)
            if (found != null) return found
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
            parent = parent.parent
        }
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
