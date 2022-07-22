package com.tencent.liteav.liveroom.model.impl.room.impl;

import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.tencent.liteav.liveroom.model.impl.base.TRTCLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.tencent.liteav.liveroom.model.impl.room.impl.IMProtocol.Define.VALUE_PROTOCOL_VERSION;

public class IMProtocol {
    private static final String TAG = "IMProtocol";


    public static class Define {
        public static final String KEY_VERSION            = "version";
        public static final String KEY_ACTION             = "action";
        public static final String VALUE_PROTOCOL_VERSION = "1.0.0";


        public static final int CODE_UNKNOWN                   = 0;
        public static final int CODE_REQUEST_JOIN_ANCHOR       = 100;
        public static final int CODE_RESPONSE_JOIN_ANCHOR      = 101;
        public static final int CODE_KICK_OUT_JOIN_ANCHOR      = 102;
        public static final int CODE_NOTIFY_JOIN_ANCHOR_STREAM = 103;

        public static final int CODE_REQUEST_ROOM_PK = 200;
        public static final int CODE_RESPONSE_PK     = 201;
        public static final int CODE_QUIT_ROOM_PK    = 202;

        public static final int CODE_ROOM_TEXT_MSG   = 300;
        public static final int CODE_ROOM_CUSTOM_MSG = 301;

        public static final int CODE_UPDATE_GROUP_INFO = 400;
    }


    public static class SignallingDefine {
        public static String KEY_VERSION     = "version";
        public static String KEY_BUSINESS_ID = "businessID";
        public static String KEY_DATA        = "data";
        public static String KEY_ROOM_ID     = "roomId";
        public static String KEY_CMD         = "cmd";

        public static final int    VALUE_VERSION     = 1;
        public static final String VALUE_BUSINESS_ID = "Live";
        public static final String VALUE_PLATFORM    = "Android";

        /**
         * Signaling CMD type
         */
        public static final String CMD_REQUESTJOINANCHOR = "requestJoinAnchor";
        public static final String CMD_KICKOUTJOINANCHOR = "kickoutJoinAnchor";
        public static final String CMD_REQUESTROOMPK     = "requestRoomPK";
        public static final String CMD_QUITROOMPK        = "quitRoomPK";
    }

    public static Pair<Boolean, Pair<String, String>> parsePKRsp(JSONObject jsonObject) {
        try {
            boolean agree = (jsonObject.getInt("accept") == 1);
            String reason = jsonObject.optString("reason");
            String streamId = jsonObject.optString("stream_id");
            return new Pair<>(agree, new Pair<String, String>(reason, streamId));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getRoomTextMsgHeadJsonStr() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(Define.KEY_VERSION, VALUE_PROTOCOL_VERSION);
            jsonObject.put(Define.KEY_ACTION, Define.CODE_ROOM_TEXT_MSG);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();
    }

    public static String getCusMsgJsonStr(String cmd, String msg) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(Define.KEY_VERSION, VALUE_PROTOCOL_VERSION);
            jsonObject.put(Define.KEY_ACTION, Define.CODE_ROOM_CUSTOM_MSG);
            jsonObject.put("command", cmd);
            jsonObject.put("message", msg);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();
    }

    public static Pair<String, String> parseCusMsg(JSONObject jsonObject) {
        String cmd = jsonObject.optString("command");
        String message = jsonObject.optString("message");
        return new Pair<>(cmd, message);
    }

    public static String getUpdateGroupInfoJsonStr(int type, List<IMAnchorInfo> list) {
        try {
            JSONObject jsonObject = new JSONObject(getGroupInfoJsonStr(type, list));
            jsonObject.put(Define.KEY_VERSION, VALUE_PROTOCOL_VERSION);
            jsonObject.put(Define.KEY_ACTION, Define.CODE_UPDATE_GROUP_INFO);
            return jsonObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String getGroupInfoJsonStr(int type, List<IMAnchorInfo> list) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("type", type);
            JSONArray jsonArray = new JSONArray();
            for (IMAnchorInfo info : list) {
                JSONObject jInfo = new JSONObject();
                jInfo.put("userId", info.userId);
                jInfo.put("streamId", info.streamId);
                jInfo.put("name", info.name);
                jsonArray.put(jInfo);
            }
            jsonObject.put("list", jsonArray);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();
    }

    public static Pair<Integer, List<IMAnchorInfo>> parseGroupInfo(String jsonStr) {
        if (TextUtils.isEmpty(jsonStr)) {
            return null;
        }
        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            int type = jsonObject.getInt("type");

            List<IMAnchorInfo> list = new ArrayList<>();
            JSONArray jsonArray = jsonObject.getJSONArray("list");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jObj = jsonArray.getJSONObject(i);
                String uid = jObj.optString("userId");
                String sid = jObj.optString("streamId");
                String name = jObj.optString("name");

                IMAnchorInfo info = new IMAnchorInfo();
                info.userId = uid;
                info.streamId = sid;
                info.name = name;

                list.add(info);
            }
            Pair<Integer, List<IMAnchorInfo>> pair = new Pair<>(type, list);
            return pair;
        } catch (JSONException e) {
            TRTCLogger.d(TAG, "parseGroupInfo fail, group info：" + jsonStr);
        }
        return null;
    }

    public static SignallingData convert2SignallingData(String json) {
        SignallingData signallingData = new SignallingData();
        Map<String, Object> extraMap;
        try {
            extraMap = new Gson().fromJson(json, Map.class);
            if (extraMap == null) {
                return signallingData;
            }
            if (extraMap.containsKey(SignallingDefine.KEY_VERSION)) {
                Object version = extraMap.get(SignallingDefine.KEY_VERSION);
                if (version instanceof Double) {
                    signallingData.setVersion(((Double) version).intValue());
                }
            }

            if (extraMap.containsKey(SignallingDefine.KEY_BUSINESS_ID)) {
                Object businessId = extraMap.get(SignallingDefine.KEY_BUSINESS_ID);
                if (businessId instanceof String) {
                    signallingData.setBusinessID((String) businessId);
                }
            }

            if (extraMap.containsKey(SignallingDefine.KEY_DATA)) {
                Object dataMapObj = extraMap.get(SignallingDefine.KEY_DATA);
                if (dataMapObj != null && dataMapObj instanceof Map) {
                    Map<String, Object> dataMap = (Map<String, Object>) dataMapObj;
                    SignallingData.DataInfo dataInfo = convert2DataInfo(dataMap);
                    signallingData.setData(dataInfo);
                }
            }
        } catch (JsonSyntaxException e) {
            Log.i(TAG, e.getMessage());
        }
        return signallingData;
    }

    private static SignallingData.DataInfo convert2DataInfo(Map<String, Object> dataMap) {
        SignallingData.DataInfo dataInfo = new SignallingData.DataInfo();
        try {
            if (dataMap.containsKey(SignallingDefine.KEY_CMD)) {
                Object cmd = dataMap.get(SignallingDefine.KEY_CMD);
                if (cmd instanceof String) {
                    dataInfo.setCmd((String) cmd);
                }
            }
            if (dataMap.containsKey(SignallingDefine.KEY_ROOM_ID)) {
                Object roomId = dataMap.get(SignallingDefine.KEY_ROOM_ID);
                if (roomId instanceof String) {
                    dataInfo.setRoomID((String) roomId);
                }
            }
        } catch (JsonSyntaxException e) {
            Log.i(TAG, e.getMessage());
        }
        return dataInfo;
    }

}
