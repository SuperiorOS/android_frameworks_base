/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.internal.util.superior;

import static android.view.DisplayCutout.BOUNDS_POSITION_LEFT;
import static android.view.DisplayCutout.BOUNDS_POSITION_RIGHT;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.input.InputManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.DisplayInfo;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

import com.android.internal.statusbar.IStatusBarService;

/**
 * Some custom utilities
 */
public class SuperiorUtils {

    public static final String INTENT_SCREENSHOT = "action_handler_screenshot";
    public static final String INTENT_REGION_SCREENSHOT = "action_handler_region_screenshot";

    private static final String TAG = "SuperiorUtils";

    private static final boolean DEBUG = false;

    private static final int NO_CUTOUT = -1;

    public static void switchScreenOff(Context ctx) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm!= null) {
            pm.goToSleep(SystemClock.uptimeMillis());
        }
    }


    public static boolean isPackageInstalled(Context context, String pkg, boolean ignoreState) {
        if (pkg != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(pkg, 0);
                if (!pi.applicationInfo.enabled && !ignoreState) {
                    return false;
                }
            } catch (NameNotFoundException e) {
                return false;
            }
        }

        return true;
    }

    public static boolean isPackageInstalled(Context context, String pkg) {
        return isPackageInstalled(context, pkg, true);
    }

    public static boolean deviceHasFlashlight(Context ctx) {
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    public static void toggleCameraFlash() {
        FireActions.toggleCameraFlash();
    }

    public static void killForegroundApp() {
        FireActions.killForegroundApp();
    }

    public static void sendKeycode(int keycode) {
        long when = SystemClock.uptimeMillis();
        final KeyEvent evDown = new KeyEvent(when, when, KeyEvent.ACTION_DOWN, keycode, 0,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);
        final KeyEvent evUp = KeyEvent.changeAction(evDown, KeyEvent.ACTION_UP);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                InputManager.getInstance().injectInputEvent(evDown,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        });
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputManager.getInstance().injectInputEvent(evUp,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        }, 20);
    }

    public static void takeScreenshot(boolean full) {
        IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
        try {
            wm.sendCustomAction(new Intent(full? INTENT_SCREENSHOT : INTENT_REGION_SCREENSHOT));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private static final class FireActions {
        private static IStatusBarService mStatusBarService = null;
        private static IStatusBarService getStatusBarService() {
            synchronized (FireActions.class) {
                if (mStatusBarService == null) {
                    mStatusBarService = IStatusBarService.Stub.asInterface(
                            ServiceManager.getService("statusbar"));
                }
                return mStatusBarService;
            }
        }

        public static void killForegroundApp() {
            IStatusBarService service = getStatusBarService();
            if (service != null) {
                try {
                    service.killForegroundApp();
                } catch (RemoteException e) {
                    // do nothing.
                }
            }
        }

        public static void toggleCameraFlash() {
            IStatusBarService service = getStatusBarService();
            if (service != null) {
                try {
                    service.toggleCameraFlash();
                } catch (RemoteException e) {
                    // do nothing.
                }
            }
        }

	    // Check if device has a notch
        public static boolean hasNotch(Context context) {
            String displayCutout = context.getResources().getString(R.string.config_mainBuiltInDisplayCutout);
            boolean maskDisplayCutout = context.getResources().getBoolean(R.bool.config_maskMainBuiltInDisplayCutout);
            boolean displayCutoutExists = (!TextUtils.isEmpty(displayCutout) && !maskDisplayCutout);
            return displayCutoutExists;
        }

        public static int getCutoutType(Context context) {
            final DisplayInfo info = new DisplayInfo();
            context.getDisplay().getDisplayInfo(info);
            final DisplayCutout cutout = info.displayCutout;
            if (cutout == null) {
                if (DEBUG) Log.v(TAG, "noCutout");
                return NO_CUTOUT;
            }
            final Point displaySize = new Point();
            context.getDisplay().getRealSize(displaySize);
            List<Rect> cutOutBounds = cutout.getBoundingRects();
            if (cutOutBounds != null) {
                for (Rect cutOutRect : cutOutBounds) {
                    if (DEBUG) Log.v(TAG, "cutout left= " + cutOutRect.left);
                    if (DEBUG) Log.v(TAG, "cutout right= " + cutOutRect.right);
                    if (cutOutRect.left == 0 && cutOutRect.right > 0) {  //cutout is located on top left
                        if (DEBUG) Log.v(TAG, "cutout position= " + BOUNDS_POSITION_LEFT);
                        return BOUNDS_POSITION_LEFT;
                    } else if (cutOutRect.right == displaySize.x && (displaySize.x - cutOutRect.left) > 0) {  //cutout is located on top right
                        if (DEBUG) Log.v(TAG, "cutout position= " + BOUNDS_POSITION_RIGHT);
                        return BOUNDS_POSITION_RIGHT;
                    }
                }
            }
            return NO_CUTOUT;
        }
    }
}
