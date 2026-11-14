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
        // 1. 强制打印包名日志，确认是否进入了目标App
        XposedBridge.log("【AgentX】尝试注入包名: ${lpparam.packageName}")
        
        if (lpparam.packageName != "com.deepseek.chat") return

        XposedBridge.log("【AgentX】成功匹配 DeepSeek，开始 Hook Activity 生命周期...")

        // 2. 移除特定的类名判断，对所有 Activity 的 onResume 都进行监听
        XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onResume", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                AgentEngine.currentActivity = activity
                
                XposedBridge.log("【AgentX】检测到 Activity 恢复: ${activity.javaClass.name}")

                // 每次 onResume 延迟 2 秒后去扫描屏幕，直到找到按钮并注入
                Handler(Looper.getMainLooper()).postDelayed({
                    injectAgentUI(activity)
                }, 2000)
            }
        })

        // 3. Hook 相册返回
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

        // 4. 拦截网络流
        try {
            val okioBuffer = XposedHelpers.findClass("okio.RealBufferedSource", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(okioBuffer, "readUtf8Line", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val line = param.result as String? ?: return
                    if (AgentEngine.isAgentMode) {
                        val intercepted = AgentEngine.onStreamLineReceived(line)
                        if (intercepted) param.result = "" 
                    }
                }
            })
            XposedBridge.log("【AgentX】Okio 网络拦截 Hook 成功！")
        } catch (e: Throwable) { 
            XposedBridge.log("【AgentX】Okio Hook 失败: ${e.message}")
        }
    }

    private fun injectAgentUI(activity: Activity) {
        val rootView = activity.window.decorView
        val targetRow = findViewRowByText(rootView, "深度思考")
        
        if (targetRow != null) {
            if (targetRow.findViewWithTag<View>("AgentXBtn") == null) {
                XposedBridge.log("【AgentX】找到了目标行，正在注入按钮！")
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
        } else {
            // 如果在这个 Activity 找不到，无需打印过多日志，因为可能在非聊天页面
            // XposedBridge.log("【AgentX】当前界面未找到包含'深度思考'的按钮行")
        }
    }

    private fun findViewRowByText(root: View, text: String): ViewGroup? {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
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
