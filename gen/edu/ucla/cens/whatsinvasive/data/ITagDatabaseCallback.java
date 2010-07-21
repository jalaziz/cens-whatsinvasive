/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: D:\\Users\\Khashim\\EclipseWorkspaces\\CENS\\whatsinvasive\\src\\edu\\ucla\\cens\\whatsinvasive\\data\\ITagDatabaseCallback.aidl
 */
package edu.ucla.cens.whatsinvasive.data;
import java.lang.String;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Binder;
import android.os.Parcel;
public interface ITagDatabaseCallback extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback
{
private static final java.lang.String DESCRIPTOR = "edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an ITagDatabaseCallback interface,
 * generating a proxy if needed.
 */
public static edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = (android.os.IInterface)obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback))) {
return ((edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback)iin);
}
return new edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback.Stub.Proxy(obj);
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
case TRANSACTION_parkTitleUpdated:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
this.parkTitleUpdated(_arg0);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback
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
public void parkTitleUpdated(java.lang.String title) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(title);
mRemote.transact(Stub.TRANSACTION_parkTitleUpdated, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_parkTitleUpdated = (IBinder.FIRST_CALL_TRANSACTION + 0);
}
public void parkTitleUpdated(java.lang.String title) throws android.os.RemoteException;
}
