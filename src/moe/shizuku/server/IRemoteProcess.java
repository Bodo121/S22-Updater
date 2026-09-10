// Binder client for Shizuku's IRemoteProcess.
//
// Derived from RikkaApps/Shizuku-API
// (aidl/src/main/aidl/moe/shizuku/server/IRemoteProcess.aidl, Apache-2.0).
// Transaction codes follow declaration order (FIRST_CALL_TRANSACTION + index),
// exactly as the AIDL compiler generates them.
package moe.shizuku.server;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

public interface IRemoteProcess extends IInterface {
    String DESCRIPTOR = "moe.shizuku.server.IRemoteProcess";

    ParcelFileDescriptor getOutputStream() throws RemoteException;

    ParcelFileDescriptor getInputStream() throws RemoteException;

    ParcelFileDescriptor getErrorStream() throws RemoteException;

    int waitFor() throws RemoteException;

    int exitValue() throws RemoteException;

    void destroy() throws RemoteException;

    boolean alive() throws RemoteException;

    boolean waitForTimeout(long timeout, String unit) throws RemoteException;

    final class Proxy implements IRemoteProcess {
        private final IBinder remote;

        public Proxy(IBinder remote) {
            this.remote = remote;
        }

        @Override public IBinder asBinder() {
            return remote;
        }

        private Parcel transact(int code, Parcel data) throws RemoteException {
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

        @Override public ParcelFileDescriptor getOutputStream() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                reply = transact(IBinder.FIRST_CALL_TRANSACTION + 0, data);
                return reply.readFileDescriptor();
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public ParcelFileDescriptor getInputStream() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                reply = transact(IBinder.FIRST_CALL_TRANSACTION + 1, data);
                return reply.readFileDescriptor();
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public ParcelFileDescriptor getErrorStream() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                reply = transact(IBinder.FIRST_CALL_TRANSACTION + 2, data);
                return reply.readFileDescriptor();
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public int waitFor() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                reply = transact(IBinder.FIRST_CALL_TRANSACTION + 3, data);
                return reply.readInt();
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public int exitValue() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                reply = transact(IBinder.FIRST_CALL_TRANSACTION + 4, data);
                return reply.readInt();
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public void destroy() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                reply = transact(IBinder.FIRST_CALL_TRANSACTION + 5, data);
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public boolean alive() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                reply = transact(IBinder.FIRST_CALL_TRANSACTION + 6, data);
                return reply.readInt() != 0;
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }

        @Override public boolean waitForTimeout(long timeout, String unit) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = null;
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeLong(timeout);
                data.writeString(unit);
                reply = transact(IBinder.FIRST_CALL_TRANSACTION + 7, data);
                return reply.readInt() != 0;
            } finally {
                data.recycle();
                if (reply != null) reply.recycle();
            }
        }
    }

    static IRemoteProcess asInterface(IBinder binder) {
        if (binder == null) return null;
        android.os.IInterface local = binder.queryLocalInterface(DESCRIPTOR);
        if (local instanceof IRemoteProcess) return (IRemoteProcess) local;
        return new Proxy(binder);
    }
}
