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

        XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onResume", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                AgentEngine.currentActivity = activity
                Handler(Looper.getMainLooper()).postDelayed({ injectAgentUI(activity) }, 1000)
            }
        })

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
        } catch (e: Throwable) { }
    }

    private fun injectAgentUI(activity: Activity) {
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        val targetRow = findViewRowByText(rootView, "深度思考")
        
        if (targetRow != null && targetRow.findViewWithTag<View>("AgentXBtn") == null) {
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

    private fun findViewRowByText(root: View, text: String): ViewGroup? {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child is TextView && child.text?.contains(text) == true && root.childCount < 15) return root
            }
            for (i in 0 until root.childCount) {
                val res = findViewRowByText(root.getChildAt(i), text)
                if (res != null) return res
            }
        }
        return null
    }
}