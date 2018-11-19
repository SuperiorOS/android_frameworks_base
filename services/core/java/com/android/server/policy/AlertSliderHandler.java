/*
 * Copyright (C) 2018 CypherOS
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

package com.android.server.policy;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;

import com.android.internal.R;

public class AlertSliderHandler {

    private static final String TAG = AlertSliderHandler.class.getSimpleName();

    private static final boolean DEBUG = false;
    private boolean mSystemReady = false;

    private AudioManager mAudioManager;
    private Context mContext;
    private Handler mHandler;
    private SwapSliderObserver mSwapSliderObserver;
    private Vibrator mVibrator;

    private boolean mHasAlertSlider = false;
    private boolean mSliderSwapped;
    private int mSliderModeTop;
    private int mSliderModeMiddle;
    private int mSliderModeBottom;

    public AlertSliderHandler(Context context) {
        mAudioManager = mContext.getSystemService(AudioManager.class);
        mContext = context;
        mHandler = new Handler();
        mVibrator = mContext.getSystemService(Vibrator.class);

        mSliderModeTop = mContext.getResources().getInteger(R.integer.config_sliderTopCode);
        mSliderModeMiddle = mContext.getResources().getInteger(R.integer.config_sliderMiddleCode);
        mSliderModeBottom = mContext.getResources().getInteger(R.integer.config_sliderBottomCode);

        mHasAlertSlider = mContext.getResources().getBoolean(R.bool.config_hasAlertSlider)
                && mSliderModeTop != 0 && mSliderModeMiddle != 0 && mSliderModeBottom != 0;

        if (mHasAlertSlider) {
            mSwapSliderObserver = new SwapSliderObserver(mHandler);
            mSwapSliderObserver.observe();
            mSwapSliderObserver.updateSwappedStatus();
        }
    }

    public void systemReady() {
        mSystemReady = true;
    }

    public boolean handleKeyEvent(KeyEvent event) {
        int scanCode = event.getScanCode();
        if (!mSystemReady || !mHasAlertSlider) {
            return false;
        }

        if (scanCode <= 0) {
            if (DEBUG) {
                Log.d(TAG, "handleKeyEvent(): scanCode is invalid, returning." );
            }
            return false;
        }

        if (!mSliderSwapped) {
            if (scanCode == mSliderModeTop) {
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
            } else if (scanCode == mSliderModeMiddle) {
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
            } else if (scanCode == mSliderModeBottom) {
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);
            }
        } else {
            if (scanCode == mSliderModeTop) {
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);
            } else if (scanCode == mSliderModeMiddle) {
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
            } else if (scanCode == mSliderModeBottom) {
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
            }
        }
        doHapticFeedback();
        return true;
    }

    private void doHapticFeedback() {
        if (mVibrator != null || mVibrator.hasVibrator()) {
            mVibrator.vibrate(50);
        }
    }

    private class SwapSliderObserver extends ContentObserver {
        SwapSliderObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ALERT_SLIDER_ORDER),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (uri.equals(Settings.System.getUriFor(Settings.System.ALERT_SLIDER_ORDER))) {
                updateSwappedStatus();
            }
        }

        public void updateSwappedStatus() {
            mSliderSwapped = Settings.System.getInt(mContext.getContentResolver(), Settings.System.ALERT_SLIDER_ORDER, 0) != 0;
        }
    }
}
