// Minimal client for Shizuku's IShizukuService.newProcess transaction.
//
// Derived from RikkaApps/Shizuku-API
// (aidl/src/main/aidl/moe/shizuku/server/IShizukuService.aidl, Apache-2.0).
// newProcess is transaction 7 per the explicit AIDL numbering.
package moe.shizuku.server;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IShizukuService extends IInterface {
    String DESCRIPTOR = "moe.shizuku.server.IShizukuService";
    int TRANSACTION_newProcess = 7;

    IRemoteProcess newProcess(String[] cmd, String[] env, String dir) throws RemoteException;

    final class Proxy implements IShizukuService {
        private final IBinder remote;

        public Proxy(IBinder remote) {
            this.remote = remote;
        }

        @Override public IBinder asBinder() {
            return remote;
        }

        @Override public IRemoteProcess newProcess(String[] cmd, String[] env, String dir)
                throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeStringArray(cmd);
                data.writeStringArray(env);
                data.writeString(dir);
                remote.transact(TRANSACTION_newProcess, data, reply, 0);
                reply.readException();
                return IRemoteProcess.asInterface(reply.readStrongBinder());
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
    }

    static IShizukuService asInterface(IBinder binder) {
        if (binder == null) return null;
        android.os.IInterface local = binder.queryLocalInterface(DESCRIPTOR);
        if (local instanceof IShizukuService) return (IShizukuService) local;
        return new Proxy(binder);
    }
}
