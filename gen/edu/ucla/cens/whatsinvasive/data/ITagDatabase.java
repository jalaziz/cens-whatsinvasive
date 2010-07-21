/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: D:\\Users\\Khashim\\EclipseWorkspaces\\CENS\\whatsinvasive\\src\\edu\\ucla\\cens\\whatsinvasive\\data\\ITagDatabase.aidl
 */
package edu.ucla.cens.whatsinvasive.data;
import java.lang.String;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Binder;
import android.os.Parcel;
public interface ITagDatabase extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements edu.ucla.cens.whatsinvasive.data.ITagDatabase
{
private static final java.lang.String DESCRIPTOR = "edu.ucla.cens.whatsinvasive.data.ITagDatabase";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an ITagDatabase interface,
 * generating a proxy if needed.
 */
public static edu.ucla.cens.whatsinvasive.data.ITagDatabase asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = (android.os.IInterface)obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof edu.ucla.cens.whatsinvasive.data.ITagDatabase))) {
return ((edu.ucla.cens.whatsinvasive.data.ITagDatabase)iin);
}
return new edu.ucla.cens.whatsinvasive.data.ITagDatabase.Stub.Proxy(obj);
}
public android.os.IBinder asBinder()
{
return this;
}
public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_registerCallback:
{
data.enforceInterface(DESCRIPTOR);
edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback _arg0;
_arg0 = edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback.Stub.asInterface(data.readStrongBinder());
this.registerCallback(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_unregisterCallback:
{
data.enforceInterface(DESCRIPTOR);
edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback _arg0;
_arg0 = edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback.Stub.asInterface(data.readStrongBinder());
this.unregisterCallback(_arg0);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements edu.ucla.cens.whatsinvasive.data.ITagDatabase
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
public void registerCallback(edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback cb) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((cb!=null))?(cb.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_registerCallback, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void unregisterCallback(edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback cb) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((cb!=null))?(cb.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_unregisterCallback, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_registerCallback = (IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_unregisterCallback = (IBinder.FIRST_CALL_TRANSACTION + 1);
}
public void registerCallback(edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback cb) throws android.os.RemoteException;
public void unregisterCallback(edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback cb) throws android.os.RemoteException;
}
