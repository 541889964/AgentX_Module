package com.superrouter.agentx

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.deepseek.chat") return

        XposedBridge.log("【AgentX】开始 Hook DeepSeek...")

        // 1. 监听 Activity，找 UI
        XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onResume", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                AgentEngine.currentActivity = activity
                Handler(Looper.getMainLooper()).postDelayed({
                    injectAgentUI(activity)
                }, 1500)
            }
        })

        // 2. 接收相册选择结果
        XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onActivityResult", 
            Int::class.java, Int::class.java, Intent::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args[0] as Int
                    val resultCode = param.args[1] as Int
                    val data = param.args[2] as? Intent
                    if (requestCode == 9999 && resultCode == Activity.RESULT_OK && data?.data != null) {
                        AgentEngine.setBackgroundFromUri(param.thisObject as Activity, data.data!!)
                    }
                }
            })

        // 3. ★ 核心：改用 Java 底层最稳的 BufferedReader 拦截，通杀所有网络流
        try {
            XposedHelpers.findAndHookMethod("java.io.BufferedReader", lpparam.classLoader, "readLine", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val line = param.result as String? ?: return
                    // 只处理包含 data: 或 #@{ 的行
                    if (AgentEngine.isAgentMode && (line.startsWith("data:") || line.contains("#@{"))) {
                        val intercepted = AgentEngine.onStreamLineReceived(line)
                        if (intercepted) param.result = "" 
                    }
                }
            })
            XposedBridge.log("【AgentX】BufferedReader 网络拦截 Hook 成功！")
        } catch (e: Throwable) { 
            XposedBridge.log("【AgentX】BufferedReader Hook 失败: ${e.message}")
        }
    }

    private fun injectAgentUI(activity: Activity) {
        val rootView = activity.window.decorView
        
        // 扩大搜索范围：同时寻找“深度思考”、“深度思索”、“深度”
        val targetRow = findViewRowByText(rootView, "深度思考") 
            ?: findViewRowByText(rootView, "深度思索") 
            ?: findViewRowByText(rootView, "深度")
        
        if (targetRow != null) {
            if (targetRow.findViewWithTag<View>("AgentXBtn") == null) {
                XposedBridge.log("【AgentX】找到了目标行，准备注入按钮！")
                val btn = TextView(activity).apply {
                    tag = "AgentXBtn"
                    text = " Agent"
                    setTextColor(Color.WHITE)
                    setPadding(30, 15, 30, 15)
                    background = GlassDrawable()
                    
                    val icon = AgentIcon("lightning", Color.WHITE)
                    icon.setBounds(0, 0, 40, 40)
                    setCompoundDrawables(icon, null, null, null)
                    compoundDrawablePadding = 10
                    
                    setOnClickListener { AgentEngine.toggleKimiMode() }
                    
                    setOnLongClickListener {
                        val intent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        activity.startActivityForResult(intent, 9999)
                        true
                    }
                }
                targetRow.addView(btn)
                AgentEngine.findOfficialInput(rootView)
            }
        }
    }

    private fun findViewRowByText(root: View, text: String): ViewGroup? {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                // 只要文字包含目标字符，就返回父容器
                if (child is TextView && child.text?.toString()?.contains(text) == true) {
                    return root
                }
            }
            for (i in 0 until root.childCount) {
                val res = findViewRowByText(root.getChildAt(i), text)
                if (res != null) return res
            }
        }
        return null
    }
}
