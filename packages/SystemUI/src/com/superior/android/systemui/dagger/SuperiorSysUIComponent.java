package com.superior.android.systemui.dagger;

import com.android.systemui.dagger.DefaultComponentBinder;
import com.android.systemui.dagger.DependencyProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.SystemUIBinder;
import com.android.systemui.dagger.SysUIComponent;
import com.android.systemui.dagger.SystemUIModule;

import com.superior.android.systemui.keyguard.SuperiorKeyguardSliceProvider;
import com.superior.android.systemui.smartspace.KeyguardSmartspaceController;

import dagger.Subcomponent;

@SysUISingleton
@Subcomponent(modules = {
        DefaultComponentBinder.class,
        DependencyProvider.class,
        SystemUIBinder.class,
        SystemUIModule.class,
        SuperiorSystemUIModule.class})
public interface SuperiorSysUIComponent extends SysUIComponent {
    @SysUISingleton
    @Subcomponent.Builder
    interface Builder extends SysUIComponent.Builder {
        SuperiorSysUIComponent build();
    }

    /**
     * Member injection into the supplied argument.
     */
    void inject(SuperiorKeyguardSliceProvider keyguardSliceProvider);

    @SysUISingleton
    KeyguardSmartspaceController createKeyguardSmartspaceController();
}
