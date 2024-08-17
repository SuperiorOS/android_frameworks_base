/*
 * Copyright (C) 2022 Paranoid Android
 *           (C) 2023 StatiXOS
 *           (C) 2023 ArrowOS
 *           (C) 2023 The LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util;

import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Binder;
import android.os.Process;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class PropImitationHooks {

    private static final String TAG = "PropImitationHooks";
    private static final boolean DEBUG = SystemProperties.getBoolean("debug.pihooks.log", false);

    private static final String PACKAGE_AIWALLPAPERS = "com.google.android.apps.aiwallpapers";
    private static final String PACKAGE_ARCORE = "com.google.ar.core";
    private static final String PACKAGE_ASI = "com.google.android.as";
    private static final String PACKAGE_ASSISTANT = "com.google.android.apps.googleassistant";
    private static final String PACKAGE_EMOJIWALLPAPER = "com.google.android.apps.emojiwallpaper";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";
    private static final String PACKAGE_NETFLIX = "com.netflix.mediaclient";
    private static final String PACKAGE_PIXELTHEMES = "com.google.android.apps.customization.pixel";
    private static final String PACKAGE_PIXELWALLPAPER = "com.google.android.apps.wallpaper.pixel";
    private static final String PACKAGE_LIVEWALLPAPER = "com.google.pixel.livewallpaper";
    private static final String PACKAGE_SUBSCRIPTION_RED = "com.google.android.apps.subscriptions.red";
    private static final String PACKAGE_VELVET = "com.google.android.googlequicksearchbox";
    private static final String PACKAGE_WALLPAPER = "com.google.android.apps.wallpaper";
    private static final String PACKAGE_WALLPAPEREFFECTS = "com.google.android.wallpaper.effects";
    private static final String PROCESS_GMS_GAPPS = PACKAGE_GMS + ".gapps";
    private static final String PROCESS_GMS_GSERVICE = PACKAGE_GMS + ".gservice";
    private static final String PROCESS_GMS_LEARNING = PACKAGE_GMS + ".learning";
    private static final String PROCESS_GMS_PERSISTENT = PACKAGE_GMS + ".persistent";
    private static final String PROCESS_GMS_SEARCH = PACKAGE_GMS + ".search";
    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";
    private static final String PROCESS_GMS_UPDATE = PACKAGE_GMS + ".update";

    private static final String PROP_SECURITY_PATCH = "persist.sys.pihooks.security_patch";
    private static final String PROP_FIRST_API_LEVEL = "persist.sys.pihooks.first_api_level";

    private static final String SPOOF_PIHOOKS_PI = "persist.sys.pihooks.pi";

    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

    private static final Map<String, String> sPixelNineXLProps = Map.of(
            "PRODUCT", "komodo",
            "DEVICE", "komodo",
            "HARDWARE", "komodo",
            "MANUFACTURER", "Google",
            "BRAND", "google",
            "MODEL", "Pixel 9 Pro XL",
            "ID", "AD1A.240905.004",
            "FINGERPRINT", "google/komodo/komodo:14/AD1A.240905.004/12196292:user/release-keys"
    );

    private static final Map<String, String> sPixelFiveProps = Map.of(
            "PRODUCT", "barbet",
            "DEVICE", "barbet",
            "HARDWARE", "barbet",
            "MANUFACTURER", "Google",
            "BRAND", "google",
            "MODEL", "Pixel 5a",
            "ID", "AP2A.240805.005",
            "FINGERPRINT", "google/barbet/barbet:14/AP2A.240805.005/12025142:user/release-keys"
    );

    private static final Map<String, String> sPixelXLProps = Map.of(
            "PRODUCT", "marlin",
            "DEVICE", "marlin",
            "HARDWARE", "marlin",
            "MANUFACTURER", "Google",
            "BRAND", "google",
            "MODEL", "Pixel XL",
            "ID", "QP1A.191005.007.A3",
            "FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys"
    );

    private static final Set<String> sNexusFeatures = Set.of(
            "NEXUS_PRELOAD",
            "nexus_preload",
            "GOOGLE_BUILD",
            "GOOGLE_EXPERIENCE",
            "PIXEL_EXPERIENCE"
    );

    private static final Set<String> sPixelFeatures = Set.of(
            "GOOGLE_BUILD",
            "GOOGLE_EXPERIENCE",
            "PIXEL_2017_EXPERIENCE",
            "PIXEL_2017_PRELOAD",
            "PIXEL_2018_EXPERIENCE",
            "PIXEL_2018_PRELOAD",
            "PIXEL_2019_EXPERIENCE",
            "PIXEL_2019_MIDYEAR_EXPERIENCE",
            "PIXEL_2019_MIDYEAR_PRELOAD",
            "PIXEL_2019_PRELOAD",
            "PIXEL_2020_EXPERIENCE",
            "PIXEL_2020_MIDYEAR_EXPERIENCE",
            "PIXEL_2021_MIDYEAR_EXPERIENCE"
    );

    private static final Set<String> sTensorFeatures = Set.of(
            "PIXEL_2021_EXPERIENCE",
            "PIXEL_2022_EXPERIENCE",
            "PIXEL_2022_MIDYEAR_EXPERIENCE",
            "PIXEL_2023_EXPERIENCE",
            "PIXEL_2023_MIDYEAR_EXPERIENCE",
            "PIXEL_2024_EXPERIENCE",
            "PIXEL_2024_MIDYEAR_EXPERIENCE",
            "PIXEL_2025_EXPERIENCE",
            "PIXEL_2025_MIDYEAR_EXPERIENCE"
    );

    private static volatile String[] sCertifiedProps;
    private static volatile String sStockFp;

    private static volatile String sProcessName;
    private static volatile boolean sIsGms, sIsFinsky, sIsPhotos;

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();

        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(processName)) {
            Log.e(TAG, "Null package or process name");
            return;
        }

        final Resources res = context.getResources();
        if (res == null) {
            Log.e(TAG, "Null resources");
            return;
        }

        sCertifiedProps = res.getStringArray(R.array.config_certifiedBuildProperties);
        sStockFp = res.getString(R.string.config_stockFingerprint);

        sProcessName = processName;
        sIsGms = packageName.equals(PACKAGE_GMS) && processName.equals(PROCESS_GMS_UNSTABLE);
        sIsFinsky = packageName.equals(PACKAGE_FINSKY);
        sIsPhotos = packageName.equals(PACKAGE_GPHOTOS);

        /* Set certified properties for GMSCore
         * Set stock fingerprint for ARCore
         * Set Pixel 8 Pro for Google, ASI and GMS device configurator
         * Set Pixel XL for Google Photos
         * Set Pixel 5a for Netflix
         */

        switch (processName) {
            case PROCESS_GMS_UNSTABLE:
                dlog("Setting certified props for: " + packageName + " process: " + processName);
                setCertifiedPropsForGms();
                return;
            case PROCESS_GMS_PERSISTENT:
            case PROCESS_GMS_GAPPS:
            case PROCESS_GMS_GSERVICE:
            case PROCESS_GMS_LEARNING:
            case PROCESS_GMS_SEARCH:
            case PROCESS_GMS_UPDATE:
                dlog("Spoofing Pixel 5a for: " + packageName + " process: " + processName);
                setProps(sPixelFiveProps);
                return;
        }

        if (!sStockFp.isEmpty() && packageName.equals(PACKAGE_ARCORE)) {
            dlog("Setting stock fingerprint for: " + packageName);
            setPropValue("FINGERPRINT", sStockFp);
            return;
        }

        switch (packageName) {
            case PACKAGE_AIWALLPAPERS:
            case PACKAGE_ASSISTANT:
            case PACKAGE_ASI:
            case PACKAGE_EMOJIWALLPAPER:
            case PACKAGE_GMS:
            case PACKAGE_LIVEWALLPAPER:
            case PACKAGE_PIXELTHEMES:
            case PACKAGE_PIXELWALLPAPER:
            case PACKAGE_SUBSCRIPTION_RED:
            case PACKAGE_VELVET:
            case PACKAGE_WALLPAPER:
            case PACKAGE_WALLPAPEREFFECTS:
                dlog("Spoofing Pixel 9 Pro XL for: " + packageName + " process: " + processName);
                setProps(sPixelNineXLProps);
                return;
            case PACKAGE_GPHOTOS:
                dlog("Spoofing Pixel XL for Google Photos");
                setProps(sPixelXLProps);
                return;
            case PACKAGE_NETFLIX:
                dlog("Spoofing Pixel 5a for Netflix");
                setProps(sPixelFiveProps);
                return;
        }
    }

    private static void setProps(Map<String, String> props) {
        props.forEach(PropImitationHooks::setPropValue);
    }

    private static void setPropValue(String key, String value) {
        try {
            dlog("Setting prop " + key + " to " + value.toString());
            Class clazz = Build.class;
            if (key.startsWith("VERSION.")) {
                clazz = Build.VERSION.class;
                key = key.substring(8);
            }
            Field field = clazz.getDeclaredField(key);
            field.setAccessible(true);
            // Cast the value to int if it's an integer field, otherwise string.
            field.set(null, field.getType().equals(Integer.TYPE) ? Integer.parseInt(value) : value);
            field.setAccessible(false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static void setCertifiedPropsForGms() {
        if (sCertifiedProps.length == 0) {
            dlog("Certified props are not set");
            return;
        }
        final boolean was = isGmsAddAccountActivityOnTop();
        final TaskStackListener taskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                final boolean is = isGmsAddAccountActivityOnTop();
                if (is ^ was) {
                    dlog("GmsAddAccountActivityOnTop is:" + is + " was:" + was +
                            ", killing myself!"); // process will restart automatically later
                    Process.killProcess(Process.myPid());
                }
            }
        };
        if (!was) {
            dlog("Spoofing build for GMS");
            setCertifiedProps();
        } else {
            dlog("Skip spoofing build for GMS, because GmsAddAccountActivityOnTop");
        }
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
        }
    }

    private static void setCertifiedProps() {
        for (String entry : sCertifiedProps) {
            // Each entry must be of the format FIELD:value
            final String[] fieldAndProp = entry.split(":", 2);
            if (fieldAndProp.length != 2) {
                Log.e(TAG, "Invalid entry in certified props: " + entry);
                continue;
            }
            setPropValue(fieldAndProp[0], fieldAndProp[1]);
        }
        setSystemProperty(PROP_SECURITY_PATCH, Build.VERSION.SECURITY_PATCH);
        setSystemProperty(PROP_FIRST_API_LEVEL,
                Integer.toString(Build.VERSION.DEVICE_INITIAL_SDK_INT));
    }

    private static void setSystemProperty(String name, String value) {
        try {
            SystemProperties.set(name, value);
            dlog("Set system prop " + name + "=" + value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set system prop " + name + "=" + value, e);
        }
    }

    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    public static boolean shouldBypassTaskPermission(Context context) {
        // GMS doesn't have MANAGE_ACTIVITY_TASKS permission
        final int callingUid = Binder.getCallingUid();
        final int gmsUid;
        try {
            gmsUid = context.getPackageManager().getApplicationInfo(PACKAGE_GMS, 0).uid;
            dlog("shouldBypassTaskPermission: gmsUid:" + gmsUid + " callingUid:" + callingUid);
        } catch (Exception e) {
            Log.e(TAG, "shouldBypassTaskPermission: unable to get gms uid", e);
            return false;
        }
        return gmsUid == callingUid;
    }

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        if (!SystemProperties.getBoolean(SPOOF_PIHOOKS_PI, true))
            return;
        // Check stack for SafetyNet or Play Integrity
        if (isCallerSafetyNet() || sIsFinsky) {
            dlog("Blocked key attestation sIsGms=" + sIsGms + " sIsFinsky=" + sIsFinsky);
            throw new UnsupportedOperationException();
        }
    }

    public static boolean hasSystemFeature(String name, boolean has) {
        if (sIsPhotos) {
            if (has && (sPixelFeatures.stream().anyMatch(name::contains)
                    || sTensorFeatures.stream().anyMatch(name::contains))) {
                dlog("Blocked system feature " + name + " for Google Photos");
                has = false;
            } else if (!has && sNexusFeatures.stream().anyMatch(name::contains)) {
                dlog("Enabled system feature " + name + " for Google Photos");
                has = true;
            }
        }
        return has;
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, "[" + sProcessName + "] " + msg);
    }
}
