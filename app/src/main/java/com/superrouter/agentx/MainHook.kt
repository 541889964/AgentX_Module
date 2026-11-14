package com.superrouter.agentx

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.deepseek.chat") return

        XposedBridge.log("【AgentX】开始 Hook DeepSeek (悬浮注入模式)...")

        // 1. 拦截 Activity，直接给根视图挂载悬浮按钮
        XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onPostResume", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                AgentEngine.currentActivity = activity
                
                // 延迟 1.5 秒，等官方界面加载完
                Handler(Looper.getMainLooper()).postDelayed({
                    injectFloatingAgentBtn(activity)
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

        // 3. 网络拦截（已经成功，保留）
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

    private fun injectFloatingAgentBtn(activity: Activity) {
        val decorView = activity.window.decorView as? ViewGroup ?: return
        
        // 防止重复添加
        if (decorView.findViewWithTag<View>("AgentXFloatingBtn") != null) return

        XposedBridge.log("【AgentX】正在向 ${activity.javaClass.simpleName} 注入悬浮按钮...")

        val btn = TextView(activity).apply {
            tag = "AgentXFloatingBtn"
            text = " Agent"
            setTextColor(Color.WHITE)
            setPadding(40, 20, 40, 20)
            background = GlassDrawable()
            elevation = 20f
            
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

        // 使用 FrameLayout.LayoutParams 悬浮在屏幕中下方（不挡住输入框）
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            bottomMargin = 300 // 距离底部 300 像素，避开发送框
            rightMargin = 50   // 靠右
        }

        // 直接把按钮添加到最顶层的 DecorView 中
        decorView.addView(btn, params)
        
        // 顺便尝试寻找一下输入框
        AgentEngine.findOfficialInput(decorView)
    }
}
