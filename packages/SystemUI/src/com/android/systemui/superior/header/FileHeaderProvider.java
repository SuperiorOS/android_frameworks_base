/*
 *  Copyright (C) 2018 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.android.systemui.superior.header;

import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

import com.android.systemui.res.R;

public class FileHeaderProvider implements
        StatusBarHeaderMachine.IStatusBarHeaderProvider {

    public static final String TAG = "FileHeaderProvider";
    private static final boolean DEBUG = false;
    private static final String HEADER_FILE_NAME = "custom_file_header_image";

    private Context mContext;
    private Drawable mImage = null;

    public FileHeaderProvider(Context context) {
        mContext = context;
        if (isCustomHeaderEnabled()) {
            loadHeaderImage();
        }
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public void settingsChanged(Uri uri) {
        if (isCustomHeaderEnabled()) {
            loadHeaderImage();
        }
    }
    
    private boolean isCustomHeaderEnabled() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER, 0,
                UserHandle.USER_CURRENT) == 1;
    }
    
    private String getCustomHeaderPath() {
        return Settings.System.getStringForUser(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_FILE_HEADER_IMAGE,
                    UserHandle.USER_CURRENT);
    }

    @Override
    public void enableProvider() {
        settingsChanged(null);
    }

    @Override
    public void disableProvider() {
    }

    private void loadHeaderImage() {
        if (mContext == null) return;
        String path = getCustomHeaderPath();
        if (path == null || path.isEmpty()) return;
        final Bitmap bitmap = BitmapFactory.decodeFile(path);
        if (bitmap == null) {
            Log.d(TAG + "loadHeaderImage: ", "Failed to decode bitmap from file");
            return;
        }
        mImage = new BitmapDrawable(mContext.getResources(), bitmap);
    }

    @Override
    public Drawable getCurrent(final Calendar now) {
        loadHeaderImage();
        return mImage;
    }
}
