package com.bodo121.s22updater;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.*;
import com.facebook.react.uimanager.ViewManager;
import java.util.*;

public final class S22Package implements ReactPackage {
    public List<NativeModule> createNativeModules(ReactApplicationContext context) {
        return Collections.singletonList(new S22Module(context));
    }
    public List<ViewManager> createViewManagers(ReactApplicationContext context) { return Collections.emptyList(); }
}
