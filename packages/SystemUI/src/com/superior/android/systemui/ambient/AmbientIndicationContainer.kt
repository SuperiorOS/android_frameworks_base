package com.superior.android.systemui.ambient

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.PendingIntent
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.media.MediaMetadata
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.TextUtils
import android.util.AttributeSet
import android.util.MathUtils
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

import com.android.systemui.AutoReinflateContainer
import com.android.systemui.Dependency
import com.android.systemui.R
import com.android.systemui.animation.Interpolators
import com.android.systemui.doze.DozeReceiver
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.statusbar.NotificationMediaManager
import com.android.systemui.statusbar.phone.CentralSurfaces
import com.android.systemui.util.wakelock.DelayedWakeLock
import com.android.systemui.util.wakelock.WakeLock

class AmbientIndicationContainer(private val context: Context, attrs: AttributeSet) : AutoReinflateContainer(context, attrs), DozeReceiver, StatusBarStateController.StateListener, NotificationMediaManager.MediaListener {

    private val handler: Handler = Handler(Looper.getMainLooper())
    private val iconBounds: Rect = Rect()
    private val wakeLock: WakeLock = DelayedWakeLock(handler, WakeLock.createPartial(context, "AmbientIndication"))
    private var ambientIconOverride: Drawable? = null
    private var ambientIndicationIconSize: Int = 0
    private lateinit var ambientMusicAnimation: Drawable
    private lateinit var ambientMusicNoteIcon: Drawable
    private var ambientMusicNoteIconSize: Int = 0
    private var ambientMusicText: String? = null
    private var ambientSkipUnlock: Boolean = false
    private var bottomMarginPx: Int = 0
    private var dozing: Boolean = false
    private var favoritingIntent: PendingIntent? = null
    private var iconDescription: String? = null
    private var iconOverride: Int = -1
    private lateinit var iconView: ImageView
    private var indicationTextMode: Int = 0
    private var mediaPlaybackState: Int = 0
    private var openIntent: PendingIntent? = null
    private lateinit var centralSurfaces: CentralSurfaces
    private var centralSurfacesState: Int = 0
    private var textColor: Int = 0
    private var textColorAnimator: ValueAnimator? = null
    private lateinit var textView: TextView
    private var reverseChargingMessage: String = ""

    public fun initializeView(centralSurfaces: CentralSurfaces) {
        this.centralSurfaces = centralSurfaces
        addInflateListener(object : AutoReinflateContainer.InflateListener {
            override fun onInflated(view: View) {
                textView = findViewById<TextView>(R.id.ambient_indication_text)
                iconView = findViewById<ImageView>(R.id.ambient_indication_icon)
                ambientMusicAnimation = context.getDrawable(R.anim.audioanim_animation)
                ambientMusicNoteIcon = context.getDrawable(R.drawable.ic_music_note)
                textColor = textView.currentTextColor
                ambientIndicationIconSize = resources.getDimensionPixelSize(R.dimen.ambient_indication_icon_size)
                ambientMusicNoteIconSize = resources.getDimensionPixelSize(R.dimen.ambient_indication_note_icon_size)
                textView.setEnabled(!dozing)
                updateColors()
                updatePill()
                textView.setOnClickListener({v -> onTextClick(v)})
                iconView.setOnClickListener({v -> onIconClick(v)})
            }
        })
        addOnLayoutChangeListener({
            _, _, _, _, _, _, _, _, _ -> updateBottomSpacing()
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (Dependency.get(StatusBarStateController::class.java) as StatusBarStateController).addCallback(this)
        (Dependency.get(NotificationMediaManager::class.java) as NotificationMediaManager).addCallback(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        (Dependency.get(StatusBarStateController::class.java) as StatusBarStateController).removeCallback(this)
        (Dependency.get(NotificationMediaManager::class.java) as NotificationMediaManager).removeCallback(this)
    }

    fun setAmbientMusic(text: String?, openIntent: PendingIntent?, favoriteIntent: PendingIntent?, skipUnlock: Boolean, iconOverride: Int, iconDescription: String?) {
        if (this.ambientMusicText != text || this.openIntent != openIntent || this.favoritingIntent != favoriteIntent || this.iconOverride != iconOverride || this.ambientSkipUnlock != skipUnlock || this.iconDescription != iconDescription) {
            this.ambientMusicText = text
            this.openIntent = openIntent
            this.favoritingIntent = favoriteIntent
            this.ambientSkipUnlock = skipUnlock
            this.iconOverride = iconOverride
            this.iconDescription = iconDescription
            this.ambientIconOverride = getAmbientIconOverride(iconOverride)
            updatePill()
        }
    }

    private fun getAmbientIconOverride(iconOverride: Int): Drawable? {
        return when(iconOverride) {
            1 -> context.getDrawable(R.drawable.ic_music_search)
            2 -> null
            3 -> context.getDrawable(R.drawable.ic_music_not_found)
            4 -> context.getDrawable(R.drawable.ic_cloud_off)
            5 -> context.getDrawable(R.drawable.ic_favorite)
            6 -> context.getDrawable(R.drawable.ic_favorite_border)
            7 -> context.getDrawable(R.drawable.ic_error)
            8 -> context.getDrawable(R.drawable.ic_favorite_note)
            else -> null
        }
    }

    private fun updatePill() {
        val oldIndicationTextMode = indicationTextMode
        var updatePill = true
        indicationTextMode = 1
        var text = ambientMusicText
        val textVisible = textView.visibility == View.VISIBLE
        var icon: Drawable? = if (textVisible) { ambientMusicNoteIcon } else { ambientMusicAnimation }
        if (ambientIconOverride != null) {
            icon = ambientIconOverride
        }
        var showAmbientMusicText = ambientMusicText != null && ambientMusicText!!.length == 0
        textView.setClickable(openIntent != null)
        iconView.setClickable(favoritingIntent != null || openIntent != null)
        var iconDescription = if (TextUtils.isEmpty(iconDescription)) { text } else { this.iconDescription }
        if (!TextUtils.isEmpty(reverseChargingMessage)) {
            indicationTextMode = 2
            text = reverseChargingMessage
            icon = null
            textView.setClickable(false)
            iconView.setClickable(false)
            showAmbientMusicText = false
            iconDescription = null
        }
        textView.text = text
        textView.setContentDescription(text)
        iconView.setContentDescription(iconDescription)
        var drawableWrapper: Drawable? = null
        if (icon != null) {
            iconBounds.set(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight())
            MathUtils.fitRect(iconBounds, if (icon == ambientMusicNoteIcon) { ambientMusicNoteIconSize } else { ambientIndicationIconSize })
            drawableWrapper = object : DrawableWrapper(icon) {
                override fun getIntrinsicWidth(): Int {
                    return iconBounds.width()
                }

                override fun getIntrinsicHeight(): Int {
                    return iconBounds.height()
                }
            }
            val endPadding: Int = if (!TextUtils.isEmpty(text)) { (getResources().getDisplayMetrics().density * 24).toInt() } else { 0 }
            textView.setPaddingRelative(textView.getPaddingStart(), textView.getPaddingTop(), endPadding, textView.getPaddingBottom())
        } else {
            textView.setPaddingRelative(textView.getPaddingStart(), textView.getPaddingTop(), 0, textView.getPaddingBottom())
        }
        iconView.setImageDrawable(drawableWrapper)
        if ((TextUtils.isEmpty(text) && !showAmbientMusicText)) {
            updatePill = false
        }
        val vis = if (updatePill) { View.VISIBLE } else { View.GONE }
        textView.setVisibility(vis)
        if (icon == null) {
            iconView.visibility = View.GONE
        } else {
            iconView.visibility = vis
        }
        if (!updatePill) {
            textView.animate().cancel()
            if (icon is AnimatedVectorDrawable) {
                icon.reset()
            }
            handler.post(wakeLock.wrap({}))
        } else if (!textVisible) {
            wakeLock.acquire("AmbientIndication")
            if (icon is AnimatedVectorDrawable) {
                icon.start()
            }
            textView.setTranslationY((textView.getHeight().toFloat()) / 2F)
            textView.setAlpha(0.0f)
            textView.animate().alpha(1.0f).translationY(0.0f).setStartDelay(150L).setDuration(100L).setListener(object: AnimatorListenerAdapter() {
                override fun onAnimationEnd(animator: Animator) {
                    wakeLock.release("AmbientIndication")
                    textView.animate().setListener(null)
                }
            }).setInterpolator(Interpolators.DECELERATE_QUINT).start()
        } else if (oldIndicationTextMode != this.indicationTextMode) {
            if (icon is AnimatedVectorDrawable) {
                wakeLock.acquire("AmbientIndication")
                icon.start()
                wakeLock.release("AmbientIndication")
            }
        } else {
            handler.post(wakeLock.wrap({}))
        }
        updateBottomSpacing()
    }

    private fun updateBottomSpacing() {
        val marginBottom = resources.getDimensionPixelSize(R.dimen.ambient_indication_margin_bottom)
        if (bottomMarginPx != marginBottom) {
            bottomMarginPx = marginBottom
            (layoutParams as FrameLayout.LayoutParams).bottomMargin = bottomMarginPx
        }
        centralSurfaces.notificationPanelViewController.setAmbientIndicationTop(top, textView.visibility == View.VISIBLE)
    }

    public fun hideAmbientMusic() {
        setAmbientMusic(null, null, null, false, 0, null)
    }

    fun onTextClick(view: View) {
        openIntent?.let {
            centralSurfaces.wakeUpIfDozing(SystemClock.uptimeMillis(), view, "AMBIENT_MUSIC_CLICK", PowerManager.WAKE_REASON_GESTURE)
            if (ambientSkipUnlock) {
                sendBroadcastWithoutDismissingKeyguard(it)
            } else {
                centralSurfaces.startPendingIntentDismissingKeyguard(openIntent)
            }
        }
    }

    fun onIconClick(view: View) {
        favoritingIntent?.let {
            centralSurfaces.wakeUpIfDozing(SystemClock.uptimeMillis(), view, "AMBIENT_MUSIC_CLICK", PowerManager.WAKE_REASON_GESTURE)
            sendBroadcastWithoutDismissingKeyguard(it)
            return
        }
        onTextClick(view)
    }

    override fun onDozingChanged(isDozing: Boolean) {
        dozing = isDozing
        updateVisibility()
        textView.let {
            it.setEnabled(!isDozing)
            updateColors()
        }
    }

    override fun dozeTimeTick() = updatePill()

    fun updateColors() {
        textColorAnimator?.let {
            if (it.isRunning()) {
                it.cancel()
            }
        }
        val defaultColor = textView.textColors.defaultColor
        val dozeColor = if (dozing) { -1 } else { textColor }
        if (dozeColor == defaultColor) {
            textView.setTextColor(dozeColor)
            iconView.imageTintList = ColorStateList.valueOf(dozeColor)
        }
        textColorAnimator = ValueAnimator.ofArgb(defaultColor, dozeColor)
        textColorAnimator!!.interpolator = Interpolators.LINEAR_OUT_SLOW_IN
        textColorAnimator!!.duration = 500L
        textColorAnimator!!.addUpdateListener({_ ->
            textView.setTextColor(textColorAnimator!!.animatedValue as Int)
            iconView.imageTintList = ColorStateList.valueOf(textColorAnimator!!.animatedValue as Int)
        })
        textColorAnimator!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator) {
                textColorAnimator = null
            }
        })
        textColorAnimator!!.start()
    }

    override fun onStateChanged(state: Int) {
        centralSurfacesState = state
        updateVisibility()
    }

    private fun sendBroadcastWithoutDismissingKeyguard(pendingIntent: PendingIntent) {
        if (pendingIntent.isActivity()) {
            return
        }
        try {
            pendingIntent.send()
        } catch (e: PendingIntent.CanceledException) {
            Log.w("AmbientIndication", "Sending intent failed: " + e)
        }
    }

    private fun updateVisibility() {
        if (centralSurfacesState == 1) {
            visibility = View.VISIBLE
        } else {
            visibility = View.INVISIBLE
        }
    }

    override fun onPrimaryMetadataOrStateChanged(mediaMetadata: MediaMetadata?, mediaState: Int) {
        if (mediaPlaybackState != mediaState) {
            mediaPlaybackState = mediaState
            if (!isMediaPlaying()) {
                return
            }
            hideAmbientMusic()
        }
    }

    private fun isMediaPlaying(): Boolean {
        return NotificationMediaManager.isPlayingState(mediaPlaybackState)
    }

    public fun setReverseChargingMessage(message: String) {
        if (TextUtils.isEmpty(message) && reverseChargingMessage == message) {
            return
        }
        reverseChargingMessage = message
        updatePill()
    }
}
