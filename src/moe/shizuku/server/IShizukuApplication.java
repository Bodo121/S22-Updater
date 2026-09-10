// Client-side stubs for Shizuku's IShizukuApplication.
//
// Derived from RikkaApps/Shizuku-API
// (aidl/src/main/aidl/moe/shizuku/server/IShizukuApplication.aidl, Apache-2.0).
// The nested Stub exists because rikka.shizuku.Shizuku$1 extends it; without
// this class the Shizuku client crashes when the binder arrives and the app
// is never registered.
package moe.shizuku.server;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IShizukuApplication extends IInterface {
    String DESCRIPTOR = "moe.shizuku.server.IShizukuApplication";

    void bindApplication(Bundle data) throws RemoteException;

    void dispatchRequestPermissionResult(int requestCode, Bundle data) throws RemoteException;

    void showPermissionConfirmation(int requestUid, int requestPid, String requestPackageName,
                                    int requestCode) throws RemoteException;

    abstract class Stub extends Binder implements IShizukuApplication {
        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IShizukuApplication asInterface(IBinder binder) {
            if (binder == null) return null;
            IInterface local = binder.queryLocalInterface(DESCRIPTOR);
            if (local instanceof IShizukuApplication) return (IShizukuApplication) local;
            return new Proxy(binder);
        }

        @Override public IBinder asBinder() {
            return this;
        }

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case 1: {
                    data.enforceInterface(DESCRIPTOR);
                    bindApplication(data.readBundle(getClass().getClassLoader()));
                    return true;
                }
                case 2: {
                    data.enforceInterface(DESCRIPTOR);
                    int requestCode = data.readInt();
                    dispatchRequestPermissionResult(requestCode,
                            data.readBundle(getClass().getClassLoader()));
                    return true;
                }
                case 10000: {
                    data.enforceInterface(DESCRIPTOR);
                    int requestUid = data.readInt();
                    int requestPid = data.readInt();
                    String requestPackageName = data.readString();
                    int requestCode = data.readInt();
                    showPermissionConfirmation(requestUid, requestPid, requestPackageName,
                            requestCode);
                    reply.writeNoException();
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static final class Proxy implements IShizukuApplication {
            private final IBinder remote;

            Proxy(IBinder remote) {
                this.remote = remote;
            }

            @Override public IBinder asBinder() {
                return remote;
            }

            @Override public void bindApplication(Bundle data) throws RemoteException {
                Parcel out = Parcel.obtain();
                try {
                    out.writeInterfaceToken(DESCRIPTOR);
                    if (data != null) {
                        out.writeInt(1);
                        data.writeToParcel(out, 0);
                    } else {
                        out.writeInt(0);
                    }
                    remote.transact(1, out, null, IBinder.FLAG_ONEWAY);
                } finally {
                    out.recycle();
                }
            }

            @Override public void dispatchRequestPermissionResult(int requestCode, Bundle data)
                    throws RemoteException {
                Parcel out = Parcel.obtain();
                try {
                    out.writeInterfaceToken(DESCRIPTOR);
                    out.writeInt(requestCode);
                    if (data != null) {
                        out.writeInt(1);
                        data.writeToParcel(out, 0);
                    } else {
                        out.writeInt(0);
                    }
                    remote.transact(2, out, null, IBinder.FLAG_ONEWAY);
                } finally {
                    out.recycle();
                }
            }

            @Override public void showPermissionConfirmation(int requestUid, int requestPid,
                                                             String requestPackageName,
                                                             int requestCode)
                    throws RemoteException {
                Parcel out = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    out.writeInterfaceToken(DESCRIPTOR);
                    out.writeInt(requestUid);
                    out.writeInt(requestPid);
                    out.writeString(requestPackageName);
                    out.writeInt(requestCode);
                    remote.transact(10000, out, reply, 0);
                    reply.readException();
                } finally {
                    out.recycle();
                    reply.recycle();
                }
            }
        }
    }
}
