package com.superior.android.systemui;

import android.app.AlarmManager;
import android.content.Context;

import com.android.systemui.Dumpable;
import com.android.systemui.R;
import com.android.systemui.VendorServices;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.statusbar.phone.CentralSurfaces;

import com.superior.android.systemui.ambient.AmbientIndicationContainer;
import com.superior.android.systemui.ambient.AmbientIndicationService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.inject.Inject;

import dagger.Lazy;

@SysUISingleton
public class SuperiorServices extends VendorServices {

    private final ArrayList<Object> mServices = new ArrayList<>();
    private final AlarmManager mAlarmManager;
    private final CentralSurfaces mCentralSurfaces;

    @Inject
    public SuperiorServices(Context context, AlarmManager alarmManager, CentralSurfaces centralSurfaces) {
        super(context);
        mAlarmManager = alarmManager;
        mCentralSurfaces = centralSurfaces;
    }

    @Override
    public void start() {
        AmbientIndicationContainer ambientIndicationContainer = (AmbientIndicationContainer) mCentralSurfaces.getNotificationShadeWindowView().findViewById(R.id.ambient_indication_container);
        ambientIndicationContainer.initializeView(mCentralSurfaces);
        addService(new AmbientIndicationService(mContext, ambientIndicationContainer, mAlarmManager));
    }

    @Override
    public void dump(PrintWriter printWriter, String[] strArr) {
        for (int i = 0; i < mServices.size(); i++) {
            if (mServices.get(i) instanceof Dumpable) {
                ((Dumpable) mServices.get(i)).dump(printWriter, strArr);
            }
        }
    }

    private void addService(Object obj) {
        if (obj != null) {
            mServices.add(obj);
        }
    }

}
