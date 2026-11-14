package com.superrouter.agentx

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.deepseek.chat") return

        XposedBridge.log("【AgentX】开始 Hook DeepSeek...")

        // 1. ★ 核心：拦截所有 TextView 的文字设置，只要设置"深度思考"就注入
        XposedHelpers.findAndHookMethod("android.widget.TextView", lpparam.classLoader, "setText", CharSequence::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val textView = param.thisObject as TextView
                val text = param.args[0] as? CharSequence ?: return
                
                // 模糊匹配文字
                if (text.toString().contains("深度思考") || text.toString().contains("深度思索")) {
                    val parent = textView.parent as? ViewGroup
                    if (parent != null && parent.findViewWithTag<View>("AgentXBtn") == null) {
                        XposedBridge.log("【AgentX】截获目标文字，准备注入按钮！")
                        val activity = getActivityFromContext(textView.context)
                        if (activity != null) {
                            injectButtonToRow(activity, parent)
                            AgentEngine.findOfficialInput(activity.window.decorView)
                        }
                    }
                }
            }
        })

        // 2. 接收相册选择结果（保留）
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

        // 3. 拦截网络流（保留）
        try {
            XposedHelpers.findAndHookMethod("java.io.BufferedReader", lpparam.classLoader, "readLine", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val line = param.result as String? ?: return
                    if (AgentEngine.isAgentMode && (line.startsWith("data:") || line.contains("#@{"))) {
                        val intercepted = AgentEngine.onStreamLineReceived(line)
                        if (intercepted) param.result = "" 
                    }
                }
            })
            XposedBridge.log("【AgentX】网络拦截 Hook 成功！")
        } catch (e: Throwable) { }
    }

    private fun injectButtonToRow(activity: Activity, targetRow: ViewGroup) {
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
    }

    // 从 Context 中提取 Activity 的通用方法
    private fun getActivityFromContext(context: Context?): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
