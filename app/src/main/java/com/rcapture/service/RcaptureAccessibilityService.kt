package com.rcapture.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.Toast
import com.rcapture.data.CaptureRepository
import com.rcapture.data.RcaptureDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class RcaptureAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var captureRepository: CaptureRepository
    private var floatingView: View? = null
    private var windowManager: WindowManager? = null

    companion object {
        const val TAG = "RcaptureAccessService"
        const val MAX_TRAVERSAL_DEPTH = 25
        const val MAX_NODE_COUNT = 1500

        var isServiceRunning = false
            private set

        fun extractTextFromNodeTree(rootNode: AccessibilityNodeInfo?): String {
            if (rootNode == null) return ""
            val visitedTexts = mutableSetOf<String>()
            val builder = StringBuilder()
            val nodeCounter = AtomicInteger(0)
            traverseNode(rootNode, visitedTexts, builder, depth = 0, nodeCounter = nodeCounter)
            return builder.toString().trim()
        }

        fun extractMultiWindowText(
            windows: List<AccessibilityWindowInfo>?,
            selfPackageName: String,
            fallbackRoot: AccessibilityNodeInfo? = null
        ): String {
            val visitedTexts = mutableSetOf<String>()
            val builder = StringBuilder()
            val nodeCounter = AtomicInteger(0)

            if (!windows.isNullOrEmpty()) {
                for (window in windows) {
                    if (nodeCounter.get() >= MAX_NODE_COUNT) break
                    val root = try {
                        window.root
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting window root", e)
                        null
                    }

                    if (root != null) {
                        val pkg = root.packageName?.toString()
                        if (pkg != selfPackageName && !pkg.isNullOrEmpty()) {
                            traverseNode(root, visitedTexts, builder, depth = 0, nodeCounter = nodeCounter)
                        }
                        try {
                            root.recycle()
                        } catch (_: Exception) {}
                    }
                }
            }

            // Fallback to active window root if windows iteration was empty
            if (builder.isEmpty() && fallbackRoot != null) {
                val pkg = fallbackRoot.packageName?.toString()
                if (pkg != selfPackageName) {
                    traverseNode(fallbackRoot, visitedTexts, builder, depth = 0, nodeCounter = nodeCounter)
                }
            }

            return builder.toString().trim()
        }

        private fun traverseNode(
            node: AccessibilityNodeInfo?,
            visitedTexts: MutableSet<String>,
            builder: StringBuilder,
            depth: Int,
            nodeCounter: AtomicInteger
        ) {
            if (node == null) return
            if (depth > MAX_TRAVERSAL_DEPTH) return
            if (nodeCounter.incrementAndGet() > MAX_NODE_COUNT) return

            try {
                // Privacy Protection: Never extract text or descriptions from password fields
                if (node.isPassword) {
                    return
                }

                val text = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()

                val candidate = when {
                    !text.isNullOrEmpty() -> text
                    !desc.isNullOrEmpty() -> desc
                    else -> null
                }

                if (candidate != null && visitedTexts.add(candidate)) {
                    builder.append(candidate).append("\n")
                }

                for (i in 0 until node.childCount) {
                    if (nodeCounter.get() >= MAX_NODE_COUNT) break
                    val child = try {
                        node.getChild(i)
                    } catch (e: Exception) {
                        null
                    }
                    if (child != null) {
                        traverseNode(child, visitedTexts, builder, depth + 1, nodeCounter)
                        try {
                            child.recycle()
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error traversing accessibility node", e)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        captureRepository = CaptureRepository(RcaptureDatabase.getDatabase(this).captureDao())
        setupFloatingOverlay()
        Log.d(TAG, "R-Capture Accessibility Service connected with multi-window support.")
    }

    private fun setupFloatingOverlay() {
        if (floatingView != null) return // Prevent duplicate overlays
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            // Floating capture button
            val button = Button(this).apply {
                text = "📷 R-Cap"
                setPadding(16, 8, 16, 8)
                setBackgroundColor(android.graphics.Color.parseColor("#0F1522"))
                setTextColor(android.graphics.Color.parseColor("#00F0FF"))
                textSize = 12f
                setOnClickListener {
                    performFloatingCapture()
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 16
                y = 200
            }

            windowManager?.addView(button, params)
            floatingView = button
            Log.d(TAG, "Floating capture overlay added successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup floating overlay", e)
        }
    }

    private fun performFloatingCapture() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Multi-window and split-screen aggregation
                val currentWindows = try { windows } catch (_: Exception) { null }
                val fallbackRoot = try { rootInActiveWindow } catch (_: Exception) { null }

                val extracted = extractMultiWindowText(
                    windows = currentWindows,
                    selfPackageName = packageName,
                    fallbackRoot = fallbackRoot
                )

                try {
                    fallbackRoot?.recycle()
                } catch (_: Exception) {}

                if (extracted.isNotBlank()) {
                    captureRepository.insert(extracted, source = "Floating Overlay")
                    launch(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "R-Capture: Screen text saved!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    launch(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "R-Capture: No readable text found in active windows.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing floating capture", e)
            }
        }
    }

    fun findTargetWindowRoot(): AccessibilityNodeInfo? {
        val windows = windows
        if (!windows.isNullOrEmpty()) {
            for (window in windows) {
                val root = window.root
                if (root != null) {
                    val pkg = root.packageName?.toString()
                    if (pkg != packageName && !pkg.isNullOrEmpty()) {
                        return root
                    }
                    root.recycle()
                }
            }
        }
        val fallbackRoot = rootInActiveWindow
        if (fallbackRoot != null && fallbackRoot.packageName?.toString() != packageName) {
            return fallbackRoot
        }
        fallbackRoot?.recycle()
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // On-demand: continuous background monitoring is disabled.
    }

    private fun removeOverlay() {
        try {
            if (floatingView != null && windowManager != null) {
                windowManager?.removeView(floatingView)
                floatingView = null
                Log.d(TAG, "Floating overlay removed safely.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay", e)
        }
    }

    override fun onInterrupt() {
        isServiceRunning = false
        removeOverlay()
        Log.d(TAG, "R-Capture Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        removeOverlay()
        Log.d(TAG, "R-Capture Accessibility Service destroyed.")
    }
}
