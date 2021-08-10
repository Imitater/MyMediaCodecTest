package com.example.mymediacodectest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
 import com.example.mymediacodectest.bean.VideoBean;
import com.xuhao.didi.socket.client.impl.client.action.ActionDispatcher;
import com.xuhao.didi.socket.client.sdk.OkSocket;
import com.xuhao.didi.socket.client.sdk.client.ConnectionInfo;
import com.xuhao.didi.socket.client.sdk.client.OkSocketOptions;
import com.xuhao.didi.socket.client.sdk.client.connection.IConnectionManager;
import com.xuhao.didi.socket.client.sdk.client.connection.NoneReconnect;


public class SocketService extends Service {
    private SocketBinder sockerBinder = new SocketBinder();
    private OkSocketOptions mOkOptions;
    private IConnectionManager mManager;
    public static final String CHANNEL_ID_STRING = "service_01";

    @Override
    public IBinder onBind(Intent intent) {
        return sockerBinder;
    }


    public class SocketBinder extends Binder {
        /*返回SocketService 在需要的地方可以通过ServiceConnection获取到SocketService  */
        public SocketService getService() {
            return SocketService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //适配8.0service
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel mChannel = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            mChannel = new NotificationChannel(CHANNEL_ID_STRING, getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(mChannel);
            Notification notification = new Notification.Builder(getApplicationContext(), CHANNEL_ID_STRING).build();
            startForeground(1, notification);
        }
     }

    public void sendMsg(VideoBean bean){
         mManager.send(bean);
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ConnectionInfo mInfo = new ConnectionInfo("172.16.10.5", 8080);
        final Handler handler = new Handler(Looper.getMainLooper());
        OkSocketOptions.Builder builder = new OkSocketOptions.Builder();
        builder.setPulseFrequency(5000);
        builder.setReconnectionManager(new NoneReconnect());
        builder.setCallbackThreadModeToken(new OkSocketOptions.ThreadModeToken() {
            @Override
            public void handleCallbackEvent(ActionDispatcher.ActionRunnable runnable) {
                handler.post(runnable);
            }
        });
        mManager = OkSocket.open(mInfo).option(builder.build());
        //开启重连
        mManager.option(new OkSocketOptions.Builder(mManager.getOption()).setReconnectionManager(OkSocketOptions.getDefault().getReconnectionManager()).build());
        SocketAdapter adapter = new SocketAdapter(mManager, getApplicationContext(), mInfo, this);
        mManager.registerReceiver(adapter);
        //开始连接
        mManager.connect();
        return super.onStartCommand(intent, flags, startId);
    }
}
