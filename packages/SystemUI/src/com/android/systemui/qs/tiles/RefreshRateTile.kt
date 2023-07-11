/*
 * Copyright (C) 2020 The Android Open Source Project
 *               2021 AOSP-Krypton Project
 *               2023 the RisingOS android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.systemui.qs.tiles

import android.content.ComponentName
import android.content.Intent
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DeviceConfig
import android.provider.Settings.System.MIN_REFRESH_RATE
import android.provider.Settings.System.PEAK_REFRESH_RATE
import android.service.quicksettings.Tile
import android.util.Log
import android.view.Display
import android.view.View
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.internal.logging.MetricsLogger
import com.android.systemui.R
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.qs.QSTile.Icon
import com.android.systemui.plugins.qs.QSTile.State
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.QSHost
import com.android.systemui.util.settings.SystemSettings
import javax.inject.Inject

class RefreshRateTile @Inject constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main private val mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    private val systemSettings: SystemSettings,
) : QSTileImpl<State>(
    host,
    backgroundLooper,
    mainHandler,
    falsingManager,
    metricsLogger,
    statusBarStateController,
    activityStarter,
    qsLogger,
) {
    private val settingsObserver: SettingsObserver
    private val tileLabel: String
    private val autoModeLabel: String
    private val defaultPeakRefreshRate: Float

    private var ignoreSettingsChange = false
    private var refreshRateMode = Mode.MIN
    private var peakRefreshRate = DEFAULT_REFRESH_RATE

    init {
        with(mContext.resources) {
            tileLabel = getString(R.string.refresh_rate_tile_label)
            autoModeLabel = getString(R.string.auto_mode_label)
            defaultPeakRefreshRate =
                getDefaultPeakRefreshRate(getInteger(com.android.internal.R.integer.config_defaultPeakRefreshRate).toFloat())
        }

        val display: Display? =
            mContext.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        peakRefreshRate = display?.supportedModes?.maxByOrNull { it.refreshRate }?.refreshRate ?: DEFAULT_REFRESH_RATE
        logD("peakRefreshRate = $peakRefreshRate, defaultPeakRefreshRate = $defaultPeakRefreshRate")
        settingsObserver = SettingsObserver()
    }

    override fun newTileState() =
        State().apply {
            icon = Companion.icon
            state = Tile.STATE_ACTIVE
        }

    override fun getLongClickIntent() = Companion.displaySettingsIntent

    override fun isAvailable() = mContext.getSystemService(DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)
        ?.supportedModes
        ?.any { it.refreshRate > 60f }
        ?: false

    override fun getTileLabel(): CharSequence = tileLabel

    override fun handleInitialize() {
        logD("handleInitialize")
        updateMode()
        settingsObserver.observe()
    }

    override fun handleClick(view: View?) {
        logD("handleClick")
        refreshRateMode = Mode.values()[(refreshRateMode.ordinal + 1) % Mode.values().size]
        logD("refreshRateMode = $refreshRateMode")
        updateRefreshRateForMode(refreshRateMode)
        refreshState()
    }

    override fun handleUpdateState(state: State, arg: Any?) {
        if (state.label == null) {
            state.label = tileLabel
            state.contentDescription = tileLabel
        }
        logD("handleUpdateState, state = $state")
        state.secondaryLabel = getTitleForMode(refreshRateMode)
        logD("secondaryLabel = ${state.secondaryLabel}")
    }

    override fun getMetricsCategory(): Int = MetricsEvent.SUPERIOR

    override fun destroy() {
        settingsObserver.unobserve()
        super.destroy()
    }

    private fun updateMode() {
        val minRate = systemSettings.getFloat(MIN_REFRESH_RATE, DEFAULT_REFRESH_RATE)
        val maxRate = systemSettings.getFloat(PEAK_REFRESH_RATE, defaultPeakRefreshRate)
        logD("minRate = $minRate, maxRate = $maxRate")

        refreshRateMode = when {
            minRate == maxRate && minRate == DEFAULT_REFRESH_RATE -> Mode.MIN
            minRate == maxRate && minRate != DEFAULT_REFRESH_RATE -> Mode.MAX
            else -> Mode.AUTO
        }
        logD("refreshRateMode = $refreshRateMode")
    }

    private fun getDefaultPeakRefreshRate(def: Float): Float {
        val displayManager = mContext.getSystemService(DisplayManager::class.java)
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

        val highestRefreshRate = defaultDisplay?.supportedModes?.maxByOrNull { it.refreshRate }?.refreshRate

        return highestRefreshRate ?: def
    }

    private fun updateRefreshRateForMode(mode: Mode) {
        logD("updateRefreshRateForMode, mode = $mode")
        val (minRate, maxRate) = when (mode) {
            Mode.AUTO -> DEFAULT_REFRESH_RATE to peakRefreshRate
            Mode.MAX -> peakRefreshRate to peakRefreshRate
            Mode.MIN -> DEFAULT_REFRESH_RATE to DEFAULT_REFRESH_RATE
        }
        ignoreSettingsChange = true
        systemSettings.putFloat(MIN_REFRESH_RATE, minRate)
        systemSettings.putFloat(PEAK_REFRESH_RATE, maxRate)
        ignoreSettingsChange = false
    }

    private fun getTitleForMode(mode: Mode) =
        when (mode) {
            Mode.AUTO -> autoModeLabel
            Mode.MAX -> "${peakRefreshRate.toInt()}Hz"
            Mode.MIN -> "${DEFAULT_REFRESH_RATE.toInt()}Hz"
        }

    private inner class SettingsObserver : ContentObserver(mainHandler) {
        private var isObserving = false

        override fun onChange(selfChange: Boolean, uri: Uri) {
            if (!ignoreSettingsChange) updateMode()
        }

        fun observe() {
            if (isObserving) return
            isObserving = true
            systemSettings.registerContentObserver(MIN_REFRESH_RATE, this)
            systemSettings.registerContentObserver(PEAK_REFRESH_RATE, this)
        }

        fun unobserve() {
            if (!isObserving) return
            isObserving = false
            systemSettings.unregisterContentObserver(this)
        }
    }

    companion object {
        const val TILE_SPEC = "refresh_rate"
        private const val TAG = "RefreshRateTile"
        private const val DEBUG = false

        private const val DEFAULT_REFRESH_RATE = 60f

        private val icon: Icon = ResourceIcon.get(R.drawable.ic_refresh_rate)
        private val displaySettingsIntent =
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$DisplaySettingsActivity"))

        private fun logD(msg: String?) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }

    private enum class Mode {
        MIN,
        MAX,
        AUTO,
    }
}
