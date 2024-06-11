/*
 * Copyright (C) 2022 The Pixel Experience Project
 *               2021-2022 crDroid Android Project
 * Copyright (C) 2022 Paranoid Android
 * Copyright (C) 2022 StatiXOS
 * Copyright (C) 2023 the RisingOS Android Project
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

package com.android.internal.util.superior;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Process;
import android.os.SystemProperties;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.R;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PixelPropsUtils {

    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final Boolean sEnablePixelProps =
            SystemProperties.getBoolean("persist.sys.pihooks.enable", true);

    private static final Map<String, Object> propsToChangeGeneric;

    private static final Map<String, Object> propsToChangeRecentPixel =
            createGoogleSpoofProps("Pixel 8 Pro",
                    "google/husky/husky:14/AP1A.240505.005.B1/11766002:user/release-keys");

    private static final Map<String, Object> propsToChangePixelTablet =
            createGoogleSpoofProps("Pixel Tablet",
                    "google/tangorpro/tangorpro:14/AP1A.240505.004/11583682:user/release-keys");

    private static final Map<String, Object> propsToChangePixel5a =
            createGoogleSpoofProps("Pixel 5a",
                    "google/barbet/barbet:14/AP1A.240505.004/11583682:user/release-keys");

    private static final Map<String, Object> propsToChangePixelXL =
            createGoogleSpoofProps("Pixel XL",
                    "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");

    private static final Map<String, ArrayList<String>> propsToKeep;

    static {
        propsToKeep = new HashMap<>();
        propsToKeep.put("com.google.android.settings.intelligence", new ArrayList<>(Collections.singletonList("FINGERPRINT")));
        propsToChangeGeneric = new HashMap<>();
        propsToChangeGeneric.put("TYPE", "user");
        propsToChangeGeneric.put("TAGS", "release-keys");
    }

    private static final ArrayList<String> packagesToChangeRecentPixel = 
        new ArrayList<String> (
            Arrays.asList(
                "com.google.android.apps.bard",
                "com.google.android.apps.customization.pixel",
                "com.google.android.apps.photos",
                "com.google.android.apps.privacy.wildlife",
                "com.google.android.apps.subscriptions.red",
                "com.google.android.apps.wallpaper.pixel",
                "com.google.android.apps.wallpaper",
                "com.google.android.gms",
                "com.google.android.googlequicksearchbox",
                "com.google.android.wallpaper.effects",
                "com.google.pixel.livewallpaper"
        ));

    private static final ArrayList<String> packagesToChangePixel5a = 
        new ArrayList<String> (
            Arrays.asList(
                "com.android.chrome",
                "com.breel.wallpapers20",
                "com.nhs.online.nhsonline"
        ));

    private static final ArrayList<String> extraPackagesToChange = 
        new ArrayList<String> (
            Arrays.asList(
                "com.amazon.avod.thirdpartyclient",
                "com.disney.disneyplus",
                "com.microsoft.android.smsorganizer",
                "com.netflix.mediaclient",
                "com.nothing.smartcenter",
                "in.startv.hotstar",
                "jp.id_credit_sp2.android"
        ));

    private static final ArrayList<String> customGoogleCameraPackages = 
        new ArrayList<String> (
            Arrays.asList(
                "com.google.android.apps.cameralite",
                "com.google.android.MTCL83",
                "com.google.android.UltraCVM"
        ));

    private static final ArrayList<String> packagesToKeep = 
        new ArrayList<String> (
            Arrays.asList(
                "com.google.android.apps.dreamliner",
                "com.google.android.apps.dreamlinerupdater",
                "com.google.android.apps.miphone.aiai.AiaiApplication",
                "com.google.android.apps.motionsense.bridge",
                "com.google.android.apps.pixelmigrate",
                "com.google.android.apps.restore",
                "com.google.android.apps.subscriptions.red",
                "com.google.android.apps.tachyon",
                "com.google.android.apps.tips",
                "com.google.android.apps.tycho",
                "com.google.android.apps.wearables.maestro.companion",
                "com.google.android.apps.youtube.kids",
                "com.google.android.apps.youtube.music",
                "com.google.android.as",
                "com.google.android.backuptransport",
                "com.google.android.backupuses",
                "com.google.android.euicc",
                "com.google.android.youtube",
                "com.google.ar.core",
                "com.google.intelligence.sense",
                "com.google.oslo",
                "it.ingdirect.app"
        ));

    private static final String sNetflixModel =
            SystemProperties.get("persist.sys.pihooks.netflix_model", "");

    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

    private static volatile boolean sIsGms, sIsFinsky, sIsSetupWizard, sIsGoogle, sIsSamsung;

    private static String getBuildID(String fingerprint) {
        Pattern pattern = Pattern.compile("([A-Za-z0-9]+\\.\\d+\\.\\d+\\.\\w+)");
        Matcher matcher = pattern.matcher(fingerprint);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String getDeviceName(String fingerprint) {
        String[] parts = fingerprint.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "";
    }

    private static Map<String, Object> createGoogleSpoofProps(String model, String fingerprint) {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "google");
        props.put("MANUFACTURER", "Google");
        props.put("ID", getBuildID(fingerprint));
        props.put("DEVICE", getDeviceName(fingerprint));
        props.put("PRODUCT", getDeviceName(fingerprint));
        props.put("MODEL", model);
        props.put("FINGERPRINT", fingerprint);
        props.put("TYPE", "user");
        props.put("TAGS", "release-keys");
        return props;
    }

    private static boolean isGoogleCameraPackage(String packageName){
        return packageName.startsWith("com.google.android.GoogleCamera") ||
            customGoogleCameraPackages.contains(packageName);
    }

    private static final String cert_device = SystemProperties.get("persist.sys.pihooks.device", "");
    private static final String cert_fp = SystemProperties.get("persist.sys.pihooks.fingerprint", "");
    private static final String cert_model = SystemProperties.get("persist.sys.pihooks.model", "");
    private static final String cert_spl = SystemProperties.get("persist.sys.pihooks.security_patch", "");
    private static final String cert_manufacturer = SystemProperties.get("persist.sys.pihooks.manufacturer", "");
    private static final int cert_sdk = SystemProperties.getInt("persist.sys.pihooks.api_level", 0);

    private static final HashMap<String, Object> certifiedProps;
    static {
        Map<String, Object> tMap = new HashMap<>();
        String[] sections = cert_fp.split("/");
        if (!cert_manufacturer.isEmpty()) tMap.put("MANUFACTURER", cert_manufacturer);
        if (!cert_model.isEmpty()) tMap.put("MODEL", cert_model);
        if (!cert_fp.isEmpty()) {
            tMap.put("FINGERPRINT", cert_fp);
            tMap.put("BRAND", sections[0]);
            tMap.put("PRODUCT", sections[1]);
            tMap.put("RELEASE", sections[2].split(":")[1]);
            tMap.put("ID", sections[3]);
            tMap.put("INCREMENTAL", sections[4].split(":")[0]);
            tMap.put("TYPE", sections[4].split(":")[1]);
            tMap.put("TAGS", sections[5]);
        }
        if (!cert_device.isEmpty()) tMap.put("DEVICE", cert_device);
        if (!cert_spl.isEmpty()) tMap.put("SECURITY_PATCH", cert_spl);
        if (cert_sdk != 0) tMap.put("DEVICE_INITIAL_SDK_INT", cert_sdk);
        certifiedProps = new HashMap<>(tMap);
    }

    public static void setProps(Context context) {
        if (!sEnablePixelProps) {
            dlog("Pixel props is disabled by config");
            return;
        }

        if (context == null) return;

        final String packageName = context.getPackageName();
        if (packageName == null || packageName.isEmpty()) {
            return;
        }

        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return;
        List<ActivityManager.RunningAppProcessInfo> runningProcesses = null;
        try {
            runningProcesses = manager.getRunningAppProcesses();
        } catch (Exception e) {
            runningProcesses = null;
        }
        if (runningProcesses == null) return;

        String processName = null;
        for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
            if (processInfo.pid == android.os.Process.myPid()) {
                processName = processInfo.processName;
                break;
            }
        }
        if (processName == null) return;

        propsToChangeGeneric.forEach((k, v) -> setPropValue(k, v));

        final boolean sIsTablet = isDeviceTablet(context);
        sIsGoogle = packageName.toLowerCase().contains("google") || processName.toLowerCase().contains("google");
        sIsSamsung = packageName.toLowerCase().contains("samsung") || processName.toLowerCase().contains("samsung");
        sIsGms = packageName.equals("com.google.android.gms");
        sIsFinsky = packageName.equals("com.android.vending");
        sIsSetupWizard = packageName.equals("com.google.android.setupwizard");

        if (shouldTryToCertifyDevice()) {
            return;
        }

        if (packagesToKeep.contains(packageName)
            || packagesToKeep.contains(processName)) {
            return;
        }

        if (isGoogleCameraPackage(packageName)) {
            return;
        }

        Map<String, Object> propsToChange = new HashMap<>();
        if (sIsGoogle || sIsSamsung
            || extraPackagesToChange.contains(packageName)
            || extraPackagesToChange.contains(processName)) {

            if (packagesToChangeRecentPixel.contains(packageName)
                || packagesToChangeRecentPixel.contains(processName)) {
                propsToChange = propsToChangeRecentPixel;
            } else if (sIsTablet) {
                propsToChange = propsToChangePixelTablet;
            } else if (packagesToChangePixel5a.contains(packageName)
                || packagesToChangePixel5a.contains(processName)
                || sIsSamsung) {
                propsToChange = propsToChangePixel5a;
            }
            if (sIsGms
                && (processName.toLowerCase().contains("ui")
                || processName.toLowerCase().contains("gapps")
                || processName.toLowerCase().contains("gservice")
                || processName.toLowerCase().contains("learning")
                || processName.toLowerCase().contains("persistent")
                || processName.toLowerCase().contains("search"))) {
                propsToChange = propsToChangePixel5a;
            }
            if (packageName.equals("com.google.android.apps.photos")) {
                if (SystemProperties.getBoolean("persist.sys.pixelprops.gphotos", false)) {
                    propsToChange = propsToChangePixelXL;
                }
            }
        }
        if (propsToChange == null || propsToChange.isEmpty()) return;
        dlog("Defining props for: " + packageName);
        for (Map.Entry<String, Object> prop : propsToChange.entrySet()) {
            String key = prop.getKey();
            Object value = prop.getValue();
            if (propsToKeep.containsKey(packageName) && propsToKeep.get(packageName).contains(key)) {
                dlog("Not defining " + key + " prop for: " + packageName);
                continue;
            }
            dlog("Defining " + key + " prop for: " + packageName);
            setPropValue(key, value);
        }
        // Set proper indexing fingerprint
        if (packageName.equals("com.google.android.settings.intelligence")) {
            setPropValue("FINGERPRINT", Build.VERSION.INCREMENTAL);
            return;
        }
        if (!sNetflixModel.isEmpty() && packageName.equals("com.netflix.mediaclient")) {
            dlog("Setting model to " + sNetflixModel + " for Netflix");
            setPropValue("MODEL", sNetflixModel);
            return;
        }
    }

    private static boolean isDeviceTablet(Context context) {
        if (context == null) {
            return false;
        }
        Configuration configuration = context.getResources().getConfiguration();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        }
        return (configuration.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE
                || displayMetrics.densityDpi == DisplayMetrics.DENSITY_XHIGH
                || displayMetrics.densityDpi == DisplayMetrics.DENSITY_XXHIGH
                || displayMetrics.densityDpi == DisplayMetrics.DENSITY_XXXHIGH;
    }

    private static void setPropValue(String key, Object value) {
        try {
            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                dlog(TAG + " Skipping setting empty value for key: " + key);
                return;
            }
            dlog(TAG + " Setting property for key: " + key + ", value: " + value.toString());
            Field field;
            Class<?> targetClass;
            try {
                targetClass = Build.class;
                field = targetClass.getDeclaredField(key);
            } catch (NoSuchFieldException e) {
                targetClass = Build.VERSION.class;
                field = targetClass.getDeclaredField(key);
            }
            if (field != null) {
                field.setAccessible(true);
                Class<?> fieldType = field.getType();
                if (fieldType == int.class || fieldType == Integer.class) {
                    if (value instanceof Integer) {
                        field.set(null, value);
                    } else if (value instanceof String) {
                        int convertedValue = Integer.parseInt((String) value);
                        field.set(null, convertedValue);
                        dlog(TAG + " Converted value for key " + key + ": " + convertedValue);
                    }
                } else if (fieldType == String.class) {
                    field.set(null, String.valueOf(value));
                }
                field.setAccessible(false);
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            dlog(TAG + " Failed to set prop " + key);
        } catch (NumberFormatException e) {
            dlog(TAG + " Failed to parse value for field " + key);
        }
    }

    private static boolean shouldTryToCertifyDevice() {
        if (!sIsGms) return false;

        final String processName = Application.getProcessName();
        if (!processName.toLowerCase().contains("unstable")) {
            return false;
        }

        setPropValue("TIME", System.currentTimeMillis());

        if (certifiedProps.isEmpty()) return false;

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
            certifiedProps.forEach(PixelPropsUtils::setPropValue);
        } else {
            dlog("Skip spoofing build for GMS, because GmsAddAccountActivityOnTop!");
        }
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
        }
        return true;
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
            gmsUid = context.getPackageManager().getApplicationInfo("com.google.android.gms", 0).uid;
            dlog("shouldBypassTaskPermission: gmsUid:" + gmsUid + " callingUid:" + callingUid);
        } catch (Exception e) {
            Log.e(TAG, "shouldBypassTaskPermission: unable to get gms uid", e);
            return false;
        }
        return gmsUid == callingUid;
    }

    private static boolean isCallerSafetyNet() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                        .anyMatch(elem -> elem.getClassName().toLowerCase()
                            .contains("droidguard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet or Play Integrity
        if (isCallerSafetyNet() || sIsFinsky) {
            dlog("Blocked key attestation");
            throw new UnsupportedOperationException();
        }
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }
}
