/*
 * Copyright (C) 2024 SuperiorOS
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

package com.android.systemui.superior;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.android.systemui.res.R;

import java.util.concurrent.TimeUnit;

public class RebootSuggestion {

    private static final String TAG = "RebootSuggestion";

    private static final String CHANNEL_ID = "Reboot Suggestion";
    private static final int REBOOT_SUGGESTION_NOTIFICATION_ID = 9367838;
    private static final int INITIAL_REMINDER = 7; // days
    private static final int REMIND_INTERVAL = 12; // hours
    private static final boolean DEBUG = false;

    private boolean rebootSuggestionNotified = false;
    private long currentUpTime;
    private long lastSuggestionTime = 0;

    private final Context context;
    private final NotificationManager notificationManager;

    private final BroadcastReceiver rebootSuggestionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "RebootSuggestionReceiver triggered");

            currentUpTime = SystemClock.elapsedRealtime();

            if (lastSuggestionTime != 0 &&
                (currentUpTime - lastSuggestionTime) > TimeUnit.HOURS.toMillis(REMIND_INTERVAL)) {
                rebootSuggestionNotified = false;
            }

            if (currentUpTime > TimeUnit.DAYS.toMillis(INITIAL_REMINDER) && !rebootSuggestionNotified) {
                lastSuggestionTime = currentUpTime;
                showRebootSuggestionNotification();
            } else {
                if (DEBUG) Log.d(TAG, "Less than initial reminder or already notified.");
            }
        }
    };

    public RebootSuggestion(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        if (DEBUG) Log.d(TAG, "Notification channel created");
        context.registerReceiver(rebootSuggestionReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_reboot_suggestion),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_reboot_suggestion_description));
        notificationManager.createNotificationChannel(channel);
    }

    private void showRebootSuggestionNotification() {
        StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
        if (activeNotifications != null) {
            for (StatusBarNotification sbn : activeNotifications) {
                if (sbn.getId() == REBOOT_SUGGESTION_NOTIFICATION_ID) {
                    if (DEBUG) Log.d(TAG, "Reboot suggestion notification already present. Skipping.");
                    rebootSuggestionNotified = true;
                    return;
                }
            }
        }

        // Create an intent to trigger the reboot action
        Intent rebootIntent = new Intent(Intent.ACTION_REBOOT);
        rebootIntent.putExtra(Intent.EXTRA_KEY_CONFIRM, false); // No confirmation dialog
        rebootIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // Wrap the intent in a PendingIntent
        PendingIntent rebootPendingIntent = PendingIntent.getActivity(
                context, 0, rebootIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT
        );

        // Add the reboot action to the notification
        NotificationCompat.Action rebootAction = new NotificationCompat.Action.Builder(
                0, // No icon for the action
                context.getString(R.string.reboot_suggestion_now),
                rebootPendingIntent
        ).build();

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_info)
                .setContentTitle(context.getString(R.string.reboot_suggestion_title))
                .setContentText(context.getString(R.string.reboot_suggestion_description))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .addAction(rebootAction) // Add the reboot action
                .build();

        notificationManager.notify(REBOOT_SUGGESTION_NOTIFICATION_ID, notification);
        if (DEBUG) Log.d(TAG, "Reboot suggestion notification posted.");
        rebootSuggestionNotified = true;
    }
}
