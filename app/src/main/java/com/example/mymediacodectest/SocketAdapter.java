package com.example.mymediacodectest;


import android.content.Context;
import android.util.Log;

import com.example.mymediacodectest.bean.HandShakeBean;
import com.example.mymediacodectest.bean.PulseBean;
import com.example.mymediacodectest.utils.EventCode;
import com.example.mymediacodectest.utils.EventMessage;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xuhao.didi.core.iocore.interfaces.IPulseSendable;
import com.xuhao.didi.core.iocore.interfaces.ISendable;
import com.xuhao.didi.core.pojo.OriginalData;
import com.xuhao.didi.socket.client.impl.client.PulseManager;
import com.xuhao.didi.socket.client.sdk.client.ConnectionInfo;
import com.xuhao.didi.socket.client.sdk.client.action.SocketActionAdapter;
import com.xuhao.didi.socket.client.sdk.client.connection.IConnectionManager;


import java.nio.charset.Charset;
import java.util.Arrays;

import static com.example.mymediacodectest.utils.EventBusUtils.post;

class SocketAdapter extends SocketActionAdapter {
    private final IConnectionManager mManager;
    private final Context context;
    private final ConnectionInfo mInfo;
    private final SocketService socketService;
    private int connectionCount;
    private PulseManager pulseManager;

    public SocketAdapter(IConnectionManager mManager, Context context, ConnectionInfo mInfo, SocketService socketService) {
        this.mManager = mManager;
        this.context = context;
        this.mInfo = mInfo;
        this.socketService = socketService;
    }

    @Override
    public void onSocketConnectionSuccess(ConnectionInfo info, String action) {
        Log.e("socket", "连接成功");
        mManager.send(new HandShakeBean(context));
        pulseManager = mManager.getPulseManager();
        pulseManager.setPulseSendable(new PulseBean(context));
        //发送连接成功消息
        EventMessage eventMessage = new EventMessage(EventCode.EVENT_A);
        post(eventMessage);
    }


    @Override
    public void onSocketDisconnection(ConnectionInfo info, String action, Exception e) {
        if (e != null) {
            Log.e("error", "连接断开" + e.toString());
        }
    }

    @Override
    public void onSocketConnectionFailed(ConnectionInfo info, String action, Exception e) {
        Log.e("error", "连接失败" + e.toString());
        connectionCount++;
        if (connectionCount >= 10) {
            //断开tcp 开启udp消息发送
            mManager.disconnect();
            mManager.unRegisterReceiver(this);
         }
    }

    @Override
    public void onSocketReadResponse(ConnectionInfo info, String action, OriginalData data) {
        String str = new String(data.getBodyBytes(), Charset.forName("utf-8"));
        JsonObject jsonObject = new JsonParser().parse(str).getAsJsonObject();
        int cmd = jsonObject.get("cmd").getAsInt();
        checkStauts(cmd, jsonObject);//Cmd状态设置
    }

    //Cmd状态设置
    private void checkStauts(int cmd, JsonObject jsonObject) {
        switch (cmd) {
            case 75://重定向
                String ip = jsonObject.get("data").getAsString().split(":")[0];
                int port = Integer.parseInt(jsonObject.get("data").getAsString().split(":")[1]);
                ConnectionInfo redirectInfo = new ConnectionInfo(ip, port);
                redirectInfo.setBackupInfo(mInfo.getBackupInfo());
                break;
            case 14:
                mManager.getPulseManager().feed();//心跳喂养
                break;
            default:
                Log.e("info",jsonObject.toString());
                break;
        }
    }

    @Override
    public void onSocketWriteResponse(ConnectionInfo info, String action, ISendable data) {
        byte[] bytes = data.parse();
        bytes = Arrays.copyOfRange(bytes, 4, bytes.length);
        String str = new String(bytes, Charset.forName("utf-8"));
        JsonObject jsonObject = new JsonParser().parse(str).getAsJsonObject();
        int cmd = jsonObject.get("cmd").getAsInt();
        switch (cmd) {
            case 54: {
                mManager.getPulseManager().pulse();
                break;
            }
        }
    }

    @Override
    public void onPulseSend(ConnectionInfo info, IPulseSendable data) {
        byte[] bytes = data.parse();
        bytes = Arrays.copyOfRange(bytes, 4, bytes.length);
        String str = new String(bytes, Charset.forName("utf-8"));
        JsonObject jsonObject = new JsonParser().parse(str).getAsJsonObject();
        int cmd = jsonObject.get("cmd").getAsInt();
        if (cmd == 14) {
            Log.e("心跳", str);
            pulseManager.setPulseSendable(new PulseBean(context));
        }else if (cmd==15){
            Log.e("流", str);
        }
    }
}

