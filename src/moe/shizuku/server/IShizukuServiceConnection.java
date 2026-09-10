// Minimal type for Shizuku's IShizukuServiceConnection (user-service APIs,
// which this app does not use). Declared so the service interface compiles;
// never instantiated here.
package moe.shizuku.server;

import android.os.IBinder;
import android.os.IInterface;

public interface IShizukuServiceConnection extends IInterface {
    String DESCRIPTOR = "moe.shizuku.server.IShizukuServiceConnection";

    abstract class Stub extends android.os.Binder implements IShizukuServiceConnection {
        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        @Override public IBinder asBinder() {
            return this;
        }
    }
}
