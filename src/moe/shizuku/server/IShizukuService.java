// Client-side stubs for Shizuku's IShizukuService.
//
// Derived from RikkaApps/Shizuku-API
// (aidl/src/main/aidl/moe/shizuku/server/IShizukuService.aidl, Apache-2.0).
// Transaction codes follow the explicit AIDL numbering. The nested Stub exists
// because rikka.shizuku.Shizuku calls IShizukuService$Stub.asInterface().
package moe.shizuku.server;

import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IShizukuService extends IInterface {
    String DESCRIPTOR = "moe.shizuku.server.IShizukuService";

    int getVersion() throws RemoteException;

    int getUid() throws RemoteException;

    int checkPermission(String permission) throws RemoteException;

    IRemoteProcess newProcess(String[] cmd, String[] env, String dir) throws RemoteException;

    String getSELinuxContext() throws RemoteException;

    String getSystemProperty(String name, String defaultValue) throws RemoteException;

    void setSystemProperty(String name, String value) throws RemoteException;

    int addUserService(IShizukuServiceConnection conn, Bundle args) throws RemoteException;

    int removeUserService(IShizukuServiceConnection conn, Bundle args) throws RemoteException;

    void requestPermission(int requestCode) throws RemoteException;

    boolean checkSelfPermission() throws RemoteException;

    boolean shouldShowRequestPermissionRationale() throws RemoteException;

    void attachApplication(IShizukuApplication application, Bundle args) throws RemoteException;

    void exit() throws RemoteException;

    void attachUserService(IBinder binder, Bundle options) throws RemoteException;

    void dispatchPackageChanged(Intent intent) throws RemoteException;

    boolean isHidden(int uid) throws RemoteException;

    void dispatchPermissionConfirmationResult(int requestUid, int requestPid, int requestCode,
                                              Bundle data) throws RemoteException;

    int getFlagsForUid(int uid, int mask) throws RemoteException;

    void updateFlagsForUid(int uid, int mask, int value) throws RemoteException;

    abstract class Stub extends Binder implements IShizukuService {
        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IShizukuService asInterface(IBinder binder) {
            if (binder == null) return null;
            IInterface local = binder.queryLocalInterface(DESCRIPTOR);
            if (local instanceof IShizukuService) return (IShizukuService) local;
            return new Proxy(binder);
        }

        @Override public IBinder asBinder() {
            return this;
        }
    }

    final class Proxy implements IShizukuService {
        private final IBinder remote;

        public Proxy(IBinder remote) {
            this.remote = remote;
        }

        @Override public IBinder asBinder() {
            return remote;
        }

        private Parcel call(int code, Parcel data) throws RemoteException {
            Parcel reply = Parcel.obtain();
            try {
                remote.transact(code, data, reply, 0);
                reply.readException();
                return reply;
            } catch (RemoteException e) {
                reply.recycle();
                throw e;
            } catch (RuntimeException e) {
                reply.recycle();
                throw e;
            }
        }

        private void callVoid(int code, Parcel data) throws RemoteException {
            Parcel reply = Parcel.obtain();
            try {
                remote.transact(code, data, reply, 0);
                reply.readException();
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        private static void writeBundle(Parcel data, Bundle value) {
            if (value != null) {
                data.writeInt(1);
                value.writeToParcel(data, 0);
            } else {
                data.writeInt(0);
            }
        }

        @Override public int getVersion() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                reply = call(2, data);
                return reply.readInt();
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public int getUid() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                reply = call(3, data);
                return reply.readInt();
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public int checkPermission(String permission) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeString(permission);
                reply = call(4, data);
                return reply.readInt();
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
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
                remote.transact(7, data, reply, 0);
                reply.readException();
                return IRemoteProcess.asInterface(reply.readStrongBinder());
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        @Override public String getSELinuxContext() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                reply = call(8, data);
                return reply.readString();
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public String getSystemProperty(String name, String defaultValue)
                throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeString(name);
                data.writeString(defaultValue);
                reply = call(9, data);
                return reply.readString();
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public void setSystemProperty(String name, String value) throws RemoteException {
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeString(name);
                data.writeString(value);
                callVoid(10, data);
            } finally {
                data.recycle();
            }
        }

        @Override public int addUserService(IShizukuServiceConnection conn, Bundle args)
                throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeStrongBinder(conn == null ? null : conn.asBinder());
                writeBundle(data, args);
                reply = call(11, data);
                return reply.readInt();
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public int removeUserService(IShizukuServiceConnection conn, Bundle args)
                throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeStrongBinder(conn == null ? null : conn.asBinder());
                writeBundle(data, args);
                reply = call(12, data);
                return reply.readInt();
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public void requestPermission(int requestCode) throws RemoteException {
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(requestCode);
                callVoid(14, data);
            } finally {
                data.recycle();
            }
        }

        @Override public boolean checkSelfPermission() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                reply = call(15, data);
                return reply.readInt() != 0;
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public boolean shouldShowRequestPermissionRationale() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                reply = call(16, data);
                return reply.readInt() != 0;
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public void attachApplication(IShizukuApplication application, Bundle args)
                throws RemoteException {
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeStrongBinder(application == null ? null : application.asBinder());
                writeBundle(data, args);
                callVoid(17, data);
            } finally {
                data.recycle();
            }
        }

        @Override public void exit() throws RemoteException {
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                callVoid(100, data);
            } finally {
                data.recycle();
            }
        }

        @Override public void attachUserService(IBinder binder, Bundle options)
                throws RemoteException {
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeStrongBinder(binder);
                writeBundle(data, options);
                callVoid(101, data);
            } finally {
                data.recycle();
            }
        }

        @Override public void dispatchPackageChanged(Intent intent) throws RemoteException {
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                if (intent != null) {
                    data.writeInt(1);
                    intent.writeToParcel(data, 0);
                } else {
                    data.writeInt(0);
                }
                remote.transact(102, data, null, IBinder.FLAG_ONEWAY);
            } finally {
                data.recycle();
            }
        }

        @Override public boolean isHidden(int uid) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(uid);
                reply = call(103, data);
                return reply.readInt() != 0;
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public void dispatchPermissionConfirmationResult(int requestUid, int requestPid,
                                                                   int requestCode, Bundle data)
                throws RemoteException {
            Parcel out = Parcel.obtain();
            try {
                out.writeInterfaceToken(DESCRIPTOR);
                out.writeInt(requestUid);
                out.writeInt(requestPid);
                out.writeInt(requestCode);
                writeBundle(out, data);
                remote.transact(104, out, null, IBinder.FLAG_ONEWAY);
            } finally {
                out.recycle();
            }
        }

        @Override public int getFlagsForUid(int uid, int mask) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(uid);
                data.writeInt(mask);
                reply = call(105, data);
                return reply.readInt();
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public void updateFlagsForUid(int uid, int mask, int value)
                throws RemoteException {
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(uid);
                data.writeInt(mask);
                data.writeInt(value);
                callVoid(106, data);
            } finally {
                data.recycle();
            }
        }
    }
}
