package com.example.mymediacodectest.bean;

import android.content.Context;
import android.util.Log;

import com.xuhao.didi.core.iocore.interfaces.IPulseSendable;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

public class  VideoBean implements IPulseSendable {
    private String str = "";

    public VideoBean(Context context,String info) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("cmd", 15);
            jsonObject.put("info", info);
            str = jsonObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public byte[] parse() {
        byte[] body = str.getBytes(Charset.defaultCharset());
        ByteBuffer bb = ByteBuffer.allocate(4 + body.length);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.putInt(body.length);
        bb.put(body);
        return bb.array();
    }
}
