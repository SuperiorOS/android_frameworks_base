/*
 * Copyright (C) 2023-2024 The risingOS Android Project
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
package com.android.systemui.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.systemui.Dependency;
import com.android.systemui.qs.QSImpl;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.tuner.TunerService;

public class WallpaperDepthUtils {

    private static final String WALLPAPER_DEPTH_KEY = "system:depth_wallpaper_subject_image_uri";
    private static final String WALLPAPER_DEPTH_ENABLED_KEY = "system:depth_wallpaper_enabled";
    private static final String WALLPAPER_DEPTH_OPACITY_KEY = "system:depth_wallpaper_opacity";
    private static final String WALLPAPER_DEPTH_OFFSET_X_KEY = "system:depth_wallpaper_offset_x";
    private static final String WALLPAPER_DEPTH_OFFSET_Y_KEY = "system:depth_wallpaper_offset_y";

    private static WallpaperDepthUtils instance;
    private FrameLayout mLockScreenSubject;
    private Drawable mDimmingOverlay;

    private final Context mContext;
    private final ConfigurationController mConfigurationController;
    private final ScrimController mScrimController;
    private final StatusBarStateController mStatusBarStateController;
    private final QSImpl mQS;

    private boolean mDWallpaperEnabled;
    private int mDWallOpacity = 255;
    private String mWallpaperSubjectPath;
    private boolean mDozing;
    private boolean mWallpaperLoaded = false;
    private String mPreviousWallpaperPath;
    private Bitmap mWallpaperBitmap;
    private int mOffsetX;
    private int mOffsetY;

    private ConfigurationController.ConfigurationListener mConfigurationListener =
            new ConfigurationController.ConfigurationListener() {
                @Override
                public void onThemeChanged() {
                    updateDepthWallpaper();
                }

                @Override
                public void onUiModeChanged() {
                    updateDepthWallpaper();
                }

                @Override
                public void onConfigChanged(Configuration newConfig) {
                    updateDepthWallpaper();
                }
            };

    private WallpaperDepthUtils(Context context) {
        mContext = context;
        mQS = Dependency.get(QSImpl.class);
        mScrimController = Dependency.get(ScrimController.class);
        mStatusBarStateController = Dependency.get(StatusBarStateController.class);
        mConfigurationController = Dependency.get(ConfigurationController.class);
        mStatusBarStateController.addCallback(mStatusBarStateListener);
        mStatusBarStateListener.onDozingChanged(mStatusBarStateController.isDozing());
        Dependency.get(TunerService.class).addTunable(mTunable, WALLPAPER_DEPTH_KEY, 
            WALLPAPER_DEPTH_ENABLED_KEY, WALLPAPER_DEPTH_OPACITY_KEY, WALLPAPER_DEPTH_OFFSET_X_KEY, WALLPAPER_DEPTH_OFFSET_Y_KEY);
        mLockScreenSubject = new FrameLayout(mContext);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        mLockScreenSubject.setLayoutParams(lp);
        mConfigurationController.addCallback(mConfigurationListener);
    }

    public static WallpaperDepthUtils getInstance(Context context) {
        if (instance == null) {
            instance = new WallpaperDepthUtils(context);
        }
        return instance;
    }

    private final StatusBarStateController.StateListener mStatusBarStateListener =
            new StatusBarStateController.StateListener() {
                @Override
                public void onStateChanged(int newState) {
                }

                @Override
                public void onDozingChanged(boolean dozing) {
                    if (mDozing == dozing) {
                        return;
                    }
                    mDozing = dozing;
                    updateDepthWallpaperVisibility();
                }
            };

    private final TunerService.Tunable mTunable = new TunerService.Tunable() {
        @Override
        public void onTuningChanged(String key, String newValue) {
            switch (key) {
                case WALLPAPER_DEPTH_ENABLED_KEY:
                    mDWallpaperEnabled = TunerService.parseIntegerSwitch(newValue, false);
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_KEY:
                    mPreviousWallpaperPath = mWallpaperSubjectPath;
                    mWallpaperSubjectPath = newValue;
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_OPACITY_KEY:
                    int opacity = TunerService.parseInteger(newValue, 100);
                    mDWallOpacity = Math.round(opacity * 2.55f);
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_OFFSET_X_KEY:
                    mOffsetX = TunerService.parseInteger(newValue, -16);
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_OFFSET_Y_KEY:
                    mOffsetY = TunerService.parseInteger(newValue, -16);
                    updateDepthWallpaper(true);
                    break;
                default:
                    break;
            }
        }
    };
    
    public void updateDepthWallpaper() {
        updateDepthWallpaper(false);
    }

    public FrameLayout getDepthWallpaperView() {
        return mLockScreenSubject;
    }

    private boolean isDWallpaperEnabled() {
        return mDWallpaperEnabled && mWallpaperSubjectPath != null
                && !mWallpaperSubjectPath.isEmpty();
    }

    private boolean canShowDepthWallpaper() {
        return mLockScreenSubject != null && isDWallpaperEnabled()
                && mScrimController.getState().toString().equals("KEYGUARD")
                && mQS.isFullyCollapsed() && !mDozing
                && mContext.getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE;
    }

    public void updateDepthWallpaperVisibility() {
        if (mLockScreenSubject == null || !isDWallpaperEnabled()) return;
        int subjectVisibility = canShowDepthWallpaper() ? View.VISIBLE : View.GONE;
        if (mLockScreenSubject.getVisibility() == subjectVisibility) return;
        mLockScreenSubject.post(() -> mLockScreenSubject.setVisibility(subjectVisibility));
    }

    public Bitmap getResizedBitmap(Bitmap wallpaperBitmap, float xOffsetDp, float yOffsetDp) {
        Rect displayBounds = mContext.getSystemService(WindowManager.class)
                .getCurrentWindowMetrics()
                .getBounds();
        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        float xOffsetPx = xOffsetDp * displayMetrics.density;
        float yOffsetPx = yOffsetDp * displayMetrics.density;
        float ratioW = displayBounds.width() / (float) wallpaperBitmap.getWidth();
        float ratioH = displayBounds.height() / (float) wallpaperBitmap.getHeight();
        int desiredHeight = Math.round(Math.max(ratioH, ratioW) * wallpaperBitmap.getHeight());
        int desiredWidth = Math.round(Math.max(ratioH, ratioW) * wallpaperBitmap.getWidth());
        desiredHeight = Math.max(desiredHeight, 0);
        desiredWidth = Math.max(desiredWidth, 0);
        Bitmap scaledWallpaperBitmap = Bitmap.createScaledBitmap(wallpaperBitmap, desiredWidth, desiredHeight, true);
        int xPixelShift = Math.max((desiredWidth - displayBounds.width()) / 2, 0) - Math.round(xOffsetPx);
        int yPixelShift = Math.max((desiredHeight - displayBounds.height()) / 2, 0) - Math.round(yOffsetPx);
        int cropWidth = Math.min(displayBounds.width(), scaledWallpaperBitmap.getWidth() - xPixelShift);
        int cropHeight = Math.min(displayBounds.height(), scaledWallpaperBitmap.getHeight() - yPixelShift);
        scaledWallpaperBitmap = Bitmap.createBitmap(scaledWallpaperBitmap, Math.max(xPixelShift, 0), Math.max(yPixelShift, 0), cropWidth, cropHeight);
        return scaledWallpaperBitmap;
    }

    public void updateDepthWallpaper(boolean forced) {
        if (mLockScreenSubject == null || !isDWallpaperEnabled()) return;
        boolean pathChanged = (mPreviousWallpaperPath != null && !mPreviousWallpaperPath.equals(mWallpaperSubjectPath));
        if (!mWallpaperLoaded || pathChanged || forced) {
            Log.d("WallpaperDepthUtils", "updateDepthWallpaper: " + (mWallpaperLoaded || forced ? "update required" : "first load"));
            new LoadWallpaperTask().execute();
            mWallpaperLoaded = true;
            mPreviousWallpaperPath = mWallpaperSubjectPath;
        }
        updateDepthWallpaperVisibility();
    }

    private class LoadWallpaperTask extends AsyncTask<Void, Void, Drawable> {
        @Override
        protected Drawable doInBackground(Void... voids) {
            try {
                Log.d("LoadWallpaperTask", "Wallpaper path: " + mWallpaperSubjectPath);
                Bitmap bitmap = BitmapFactory.decodeFile(mWallpaperSubjectPath);
                if (bitmap == null) {
                    Log.d("LoadWallpaperTask", "Failed to decode bitmap from file");
                    return null;
                }
                mWallpaperBitmap = getResizedBitmap(bitmap, mOffsetX, mOffsetY);
                if (mWallpaperBitmap == null) {
                    Log.d("LoadWallpaperTask", "Failed to decode resized bitmap from file");
                    return null;
                } 
                Drawable bitmapDrawable = new BitmapDrawable(mContext.getResources(), mWallpaperBitmap);
                bitmapDrawable.setAlpha(255);
                mDimmingOverlay = bitmapDrawable.getConstantState().newDrawable().mutate();
                mDimmingOverlay.setTint(Color.BLACK);
                return new LayerDrawable(new Drawable[]{bitmapDrawable, mDimmingOverlay});
            } catch (OutOfMemoryError e) {
                Log.e("LoadWallpaperTask", "Out of memory error", e);
                return null;
            } catch (Exception e) {
                Log.e("LoadWallpaperTask", "Error loading wallpaper", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Drawable drawable) {
            if (drawable == null || mWallpaperBitmap.isRecycled()) {
                Log.d("LoadWallpaperTask", "decodeFile returned nothing, skipping application of subject as background");
                mWallpaperLoaded = false;
                return;
            }
            if (drawable != null) {
                mLockScreenSubject.setBackground(drawable);
                mLockScreenSubject.getBackground().setAlpha(mDWallOpacity);
                mDimmingOverlay.setAlpha(Math.round(mScrimController.getScrimBehindAlpha() * 240));
                Log.d("LoadWallpaperTask", "Subject Loaded!");
            } else {
                updateDepthWallpaperVisibility();
            }
        }
    }
}
