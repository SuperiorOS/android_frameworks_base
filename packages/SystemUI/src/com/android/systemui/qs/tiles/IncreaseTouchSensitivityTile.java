/*
 * Copyright (C) 2021 SuperiorOS
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
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.service.quicksettings.Tile;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;

import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.qs.SecureSetting;
import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.util.settings.SecureSettings;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import javax.inject.Inject;

/** Quick settings tile: Increase Touch Sensitivity **/
public class IncreaseTouchSensitivityTile extends QSTileImpl<BooleanState> {

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_increase_touch_sensitivity);

    private static final Intent DISPLAY_SETTINGS = new Intent("android.settings.DISPLAY_SETTINGS");

    private final SecureSetting mSetting;

    @Inject
    public IncreaseTouchSensitivityTile(
            QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            SecureSettings secureSettings
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);

        mSetting = new SecureSetting(secureSettings, mHandler, Secure.TOUCH_SENSITIVITY_ENABLED) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                handleRefreshState(value);
            }
        };
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable View view) {
        setEnabled(!mState.value);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return DISPLAY_SETTINGS;
    }

    private void setEnabled(boolean enabled) {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.TOUCH_SENSITIVITY_ENABLED,
                enabled ? 1 : 0);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer) arg : mSetting.getValue();
        final boolean enable = value != 0;
        state.value = enable;
        state.label = mContext.getString(R.string.quick_settings_increase_touch_sensitivity_label);
        state.icon = mIcon;
        if (enable) {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_increase_touch_sensitivity_on);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_increase_touch_sensitivity_off);
            state.state = Tile.STATE_INACTIVE;
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_increase_touch_sensitivity_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.SUPERIOR;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.accessibility_quick_settings_increase_touch_sensitivity_changed_on);
        } else {
            return mContext.getString(
                    R.string.accessibility_quick_settings_increase_touch_sensitivity_changed_off);
        }
    }

    @Override
    public void handleSetListening(boolean listening) {
        // Do nothing
    }
}
