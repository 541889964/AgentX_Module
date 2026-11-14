package com.superrouter.agentx

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import org.json.JSONObject
import java.io.File

object AgentEngine {
    var isAgentMode = false
    var currentActivity: Activity? = null
    var officialEditText: EditText? = null
    var officialSendButton: View? = null

    private var kimiUI: KimiUI? = null
    private var overlayView: FrameLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private var fullTextBuffer = StringBuilder()

    fun toggleKimiMode() {
        val activity = currentActivity ?: return
        isAgentMode = !isAgentMode
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)

        if (isAgentMode) {
            if (overlayView == null) {
                overlayView = FrameLayout(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(Color.TRANSPARENT)
                }
                kimiUI = KimiUI(activity)
                overlayView?.addView(kimiUI?.rootLayout)

                kimiUI?.sendButton?.setOnClickListener {
                    val text = kimiUI?.inputEditText?.text.toString()
                    if (text.isNotBlank()) {
                        kimiUI?.addUserBubble(text)
                        kimiUI?.inputEditText?.setText("")
                        val agentPrompt = "【系统最高指令】\n" +
                                          "用户指令：" + text + "\n" +
                                          "1. 编写代码后必须自测，报错必须修复，直到成功。\n" +
                                          "2. 成功后输出 #@{attempt_completion...}@。"
                        sendToOfficialChat(agentPrompt)
                        kimiUI?.showThinking()
                    }
                }
            }
            rootView.addView(overlayView)
        } else {
            rootView.removeView(overlayView)
        }
    }

    fun setBackgroundFromUri(activity: Activity, uri: Uri) {
        try {
            val inputStream = activity.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val drawable = BitmapDrawable(activity.resources, bitmap)
            activity.window.decorView.background = drawable
            Toast.makeText(activity, "官方背景壁纸已替换为液态透光", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "壁纸设置失败: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    fun findOfficialInput(root: View) {
        if (root is ViewGroup) {
            if (root is EditText) officialEditText = root
            if (root is android.widget.ImageButton || (root is android.widget.ImageView && (root as android.view.View).isClickable)) {
                officialSendButton = root
            }
            for (i in 0 until root.childCount) findOfficialInput(root.getChildAt(i))
        }
    }

    fun onStreamLineReceived(line: String): Boolean {
        try {
            if (line.startsWith("data:")) {
                val jsonStr = line.substring(5).trim()
                val json = JSONObject(jsonStr)
                if (json.has("v") && json.get("v") is String) {
                    val v = json.getString("v")
                    if (v.contains("#@{") || v.contains("}@")) {
                        fullTextBuffer.append(v)
                        return true 
                    } else if (fullTextBuffer.isNotEmpty()) {
                        fullTextBuffer.append(v)
                        return true
                    } else {
                        handler.post { kimiUI?.appendAiText(v) }
                    }
                }
                if (jsonStr.contains("FINISHED") || jsonStr.contains("[DONE]")) {
                    val finalText = fullTextBuffer.toString()
                    fullTextBuffer.clear()
                    if (finalText.contains("#@{") && finalText.contains("}@")) {
                        processAgentAction(finalText)
                        return true
                    } else {
                        handler.post { kimiUI?.hideStatus() }
                    }
                }
            }
        } catch (e: Exception) {}
        return false
    }

    private fun processAgentAction(text: String) {
        val jsonStr = extractJson(text) ?: return
        try {
            val obj = JSONObject(jsonStr)
            val tool = obj.optString("tool")
            val params = obj.optJSONObject("params")
            val action = obj.optString("action", "执行操作")
            val command = params?.optString("command") ?: ""

            if (tool == "attempt_completion") {
                val finalResult = params?.optString("result") ?: "任务完成。"
                handler.post { kimiUI?.showFinalResult(finalResult) }
                return
            }

            handler.post { kimiUI?.showExecuting(action, command) }
            val execResult = executeTool(tool, params)

            handler.postDelayed({
                kimiUI?.showThinking()
                sendToOfficialChat("[执行结果回传]\n结果：\n" + execResult + "\n\n请分析并继续。")
            }, 1000)

        } catch (e: Exception) { }
    }

    private fun sendToOfficialChat(text: String) {
        val input = officialEditText ?: return
        val btn = officialSendButton ?: return
        handler.post {
            input.setText(text)
            input.setSelection(text.length)
            handler.postDelayed({ btn.performClick() }, 300)
        }
    }

    private fun executeTool(tool: String, params: JSONObject?): String {
        return try {
            when (tool) {
                "execute_command" -> {
                    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", params?.optString("command")))
                    val out = p.inputStream.bufferedReader().readText().trim()
                    val err = p.errorStream.bufferedReader().readText().trim()
                    if (out.isBlank() && err.isBlank()) "(无输出)" else out + "\n" + err
                }
                "write_to_file" -> {
                    val f = File(params?.optString("path") ?: return "无路径")
                    f.parentFile?.mkdirs()
                    f.writeText(params?.optString("content") ?: "")
                    "文件写入成功"
                }
                else -> "未知工具"
            }
        } catch (e: Exception) { "执行异常: " + e.message }
    }

    private fun extractJson(text: String): String? {
        val s = text.indexOf("#@{")
        val e = text.lastIndexOf("}@")
        if (s != -1 && e != -1 && e > s) return text.substring(s + 2, e + 1)
        return null
    }
}