package com.superrouter.agentx

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class KimiUI(private val context: Context) {
    val rootLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.TRANSPARENT)
    }
    private val scrollView = ScrollView(context).apply { setBackgroundColor(Color.TRANSPARENT) }
    val chatLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(30, 30, 30, 30)
    }
    val inputEditText = EditText(context).apply {
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#8B93A7"))
        hint = "输入指令..."
        background = GlassDrawable()
        setPadding(40, 25, 40, 25)
    }
    val sendButton = FrameLayout(context).apply {
        background = GlassDrawable()
        val icon = ImageView(context).apply {
            setImageDrawable(AgentIcon("lightning", Color.WHITE))
            layoutParams = FrameLayout.LayoutParams(40, 40, Gravity.CENTER)
        }
        addView(icon)
    }
    var currentAiTextView: TextView? = null
    private var statusView: LinearLayout? = null
    private var statusText: TextView? = null
    private var statusIcon: ImageView? = null
    private var thinkingAnimator: ObjectAnimator? = null
    private val handler = Handler(Looper.getMainLooper())

    init {
        scrollView.addView(chatLayout)
        rootLayout.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val inputContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(30, 20, 30, 50)
            setBackgroundColor(Color.TRANSPARENT)
        }
        val editParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        editParams.marginEnd = 20
        inputContainer.addView(inputEditText, editParams)
        inputContainer.addView(sendButton, LinearLayout.LayoutParams(120, 120))
        rootLayout.addView(inputContainer)
    }

    private fun scrollToBottom() { scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) } }

    fun addUserBubble(text: String) {
        val row = LinearLayout(context).apply { gravity = Gravity.END; setPadding(0, 20, 0, 20); alpha = 0f; translationX = 50f }
        val bubble = TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(40, 25, 40, 25)
            background = GlassDrawable()
        }
        row.addView(bubble)
        chatLayout.addView(row)
        row.animate().alpha(1f).translationX(0f).setDuration(250).start()
        scrollToBottom()
    }

    fun startNewAiBubble() {
        hideStatus()
        val row = LinearLayout(context).apply { gravity = Gravity.START; setPadding(0, 20, 0, 20); alpha = 0f; translationY = 50f }
        currentAiTextView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setLineSpacing(10f, 1f)
        }
        row.addView(currentAiTextView)
        chatLayout.addView(row)
        row.animate().alpha(1f).translationY(0f).setDuration(300).start()
        scrollToBottom()
    }

    fun appendAiText(chunk: String) {
        currentAiTextView?.append(chunk)
        scrollToBottom()
    }

    fun showFinalResult(result: String) {
        handler.post {
            hideStatus()
            currentAiTextView = null
            val row = LinearLayout(context).apply { gravity = Gravity.START; setPadding(0, 20, 0, 20); alpha = 0f; translationY = 50f }
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 50, 50, 50)
                background = GradientDrawable().apply { cornerRadius = 40f; setColor(Color.parseColor("#1A10B981")); setStroke(3, Color.parseColor("#10B981")) }
            }
            val title = TextView(context).apply {
                text = "任务执行完毕"
                setTextColor(Color.parseColor("#10B981"))
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val desc = TextView(context).apply {
                text = result
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(0, 20, 0, 0)
            }
            card.addView(title)
            card.addView(desc)
            row.addView(card)
            chatLayout.addView(row)
            row.animate().alpha(1f).translationY(0f).setDuration(400).start()
            scrollToBottom()
        }
    }

    fun showThinking() {
        handler.post {
            if (statusView == null) {
                statusView = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(20, 20, 20, 20)
                    background = GlassDrawable()
                }
                statusIcon = ImageView(context).apply {
                    setImageDrawable(AgentIcon("gear", Color.WHITE))
                    layoutParams = LinearLayout.LayoutParams(40, 40).apply { marginEnd = 20 }
                }
                statusText = TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    text = "DeepSeek 正在思考中"
                }
                statusView?.addView(statusIcon)
                statusView?.addView(statusText)
                chatLayout.addView(statusView)
            }
            statusView?.visibility = LinearLayout.VISIBLE
            statusText?.text = "DeepSeek 正在思考中"
            if (thinkingAnimator?.isRunning != true) {
                thinkingAnimator = ObjectAnimator.ofFloat(statusView, "alpha", 0.3f, 1f, 0.3f).apply {
                    duration = 1200
                    repeatCount = ObjectAnimator.INFINITE
                    start()
                }
            }
            scrollToBottom()
        }
    }

    fun showExecuting(toolName: String, command: String) {
        handler.post {
            if (statusView == null) {
                statusView = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(20, 20, 20, 20)
                    background = GlassDrawable()
                }
                statusIcon = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(40, 40).apply { marginEnd = 20 }
                }
                statusText = TextView(context).apply {
                    setTextColor(Color.parseColor("#A5B4FC"))
                    textSize = 14f
                }
                statusView?.addView(statusIcon)
                statusView?.addView(statusText)
                chatLayout.addView(statusView)
            }
            statusIcon?.setImageDrawable(AgentIcon("gear", Color.parseColor("#A5B4FC")))
            statusText?.text = "正在执行: $toolName\n指令: $command"
            statusView?.visibility = LinearLayout.VISIBLE
            scrollToBottom()
        }
    }

    fun hideStatus() {
        handler.post { 
            thinkingAnimator?.cancel()
            thinkingAnimator = null
            if (statusView != null) {
                statusView?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
                    statusView?.visibility = LinearLayout.GONE
                }?.start()
            }
        }
    }
}