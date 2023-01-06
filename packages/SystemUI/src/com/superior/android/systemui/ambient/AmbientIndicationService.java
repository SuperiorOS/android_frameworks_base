/*
 * Copyright (C) 2022 The PixelExperience Project
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

package com.superior.android.systemui.ambient;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.Dependency;

public class AmbientIndicationService extends BroadcastReceiver {
    private final AlarmManager mAlarmManager;
    private final AmbientIndicationContainer mAmbientIndicationContainer;
    private final Context mContext;
    private final KeyguardUpdateMonitorCallback mCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onUserSwitchComplete(int i) {
            onUserSwitched();
        }
    };
    private final AlarmManager.OnAlarmListener mHideIndicationListener;

    private String lastTitle;

    private static final String HIDE_AMBIENT_ACTION = "com.google.android.ambientindication.action.AMBIENT_INDICATION_HIDE";
    private static final String SHOW_AMBIENT_ACTION = "com.google.android.ambientindication.action.AMBIENT_INDICATION_SHOW";

    public AmbientIndicationService(Context context, AmbientIndicationContainer ambientIndicationContainer, AlarmManager alarmManager) {
        mContext = context;
        mAmbientIndicationContainer = ambientIndicationContainer;
        mAlarmManager = alarmManager;
        mHideIndicationListener = () -> mAmbientIndicationContainer.hideAmbientMusic();
        start();
    }

    void start() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SHOW_AMBIENT_ACTION);
        intentFilter.addAction(HIDE_AMBIENT_ACTION);
        mContext.registerReceiverAsUser(this, UserHandle.ALL, intentFilter, "com.google.android.ambientindication.permission.AMBIENT_INDICATION", null);
        ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).registerCallback(mCallback);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isForCurrentUser()) {
            Log.i("AmbientIndication", "Suppressing ambient, not for this user.");
        } else if (verifyAmbientApiVersion(intent)) {
            String action = intent.getAction();
            if (action.equals(HIDE_AMBIENT_ACTION)) {
                mAlarmManager.cancel(mHideIndicationListener);
                mAmbientIndicationContainer.hideAmbientMusic();
                Log.i("AmbientIndication", "Hiding ambient indication.");
            } else if (action.equals(SHOW_AMBIENT_ACTION)) {
                long min = Math.min(Math.max(intent.getLongExtra("com.google.android.ambientindication.extra.TTL_MILLIS", 180000L), 0L), 180000L);
                boolean booleanExtra = intent.getBooleanExtra("com.google.android.ambientindication.extra.SKIP_UNLOCK", false);
                int intExtra = intent.getIntExtra("com.google.android.ambientindication.extra.ICON_OVERRIDE", 0);
                String stringExtra = intent.getStringExtra("com.google.android.ambientindication.extra.ICON_DESCRIPTION");
                String newTitle = intent.getCharSequenceExtra("com.google.android.ambientindication.extra.TEXT").toString();
                mAmbientIndicationContainer.setAmbientMusic(
                        newTitle,
                        (PendingIntent) intent.getParcelableExtra("com.google.android.ambientindication.extra.OPEN_INTENT"),
                        (PendingIntent) intent.getParcelableExtra("com.google.android.ambientindication.extra.FAVORITING_INTENT"),
                        booleanExtra,
                        intExtra,
                        stringExtra);
                mAlarmManager.setExact(2, SystemClock.elapsedRealtime() + min, "AmbientIndication", mHideIndicationListener, null);
                // Trigger an ambient pulse event only if a new title has been recognized
                if (!newTitle.equals(lastTitle)) {
                    lastTitle = newTitle;
                    Log.i("AmbientIndication", "Sending ambient pulse event for: " + newTitle);
                    mContext.sendBroadcastAsUser(new Intent("com.android.systemui.doze.pulse"),
                        new UserHandle(UserHandle.USER_CURRENT));
                }
                Log.i("AmbientIndication", "Showing ambient indication.");
            }
        }
    }

    private boolean verifyAmbientApiVersion(Intent intent) {
        int intExtra = intent.getIntExtra("com.google.android.ambientindication.extra.VERSION", 0);
        if (intExtra != 1) {
            Log.e("AmbientIndication", "AmbientIndicationApi.EXTRA_VERSION is 1, but received an intent with version " + intExtra + ", dropping intent.");
            return false;
        }
        return true;
    }

    private boolean isForCurrentUser() {
        return getSendingUserId() == getCurrentUser() || getSendingUserId() == -1;
    }

    private int getCurrentUser() {
        return KeyguardUpdateMonitor.getCurrentUser();
    }

    private void onUserSwitched() {
        mAmbientIndicationContainer.hideAmbientMusic();
    }
}
