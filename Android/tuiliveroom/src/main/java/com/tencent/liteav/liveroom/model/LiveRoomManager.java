package com.tencent.liteav.liveroom.model;


import android.util.Log;

import com.tencent.imsdk.v2.V2TIMGroupInfoResult;
import com.tencent.imsdk.v2.V2TIMManager;
import com.tencent.imsdk.v2.V2TIMValueCallback;

import java.util.ArrayList;
import java.util.List;

public class LiveRoomManager {
    private static final String TAG = "LiveRoomManager";

    private static LiveRoomManager sInstance;
    private        RoomCallback    mRoomCallback;

    public static LiveRoomManager getInstance() {
        if (sInstance == null) {
            synchronized (LiveRoomManager.class) {
                if (sInstance == null) {
                    sInstance = new LiveRoomManager();
                }
            }
        }
        return sInstance;
    }


    public void addCallback(RoomCallback callback) {
        mRoomCallback = callback;
    }

    public void removeCallback() {
        mRoomCallback = null;
    }

    public void createRoom(String roomId, ActionCallback callback) {
        if (mRoomCallback != null) {
            mRoomCallback.onRoomCreate(roomId, callback);
        }
    }

    public void destroyRoom(String roomId, ActionCallback callback) {
        if (mRoomCallback != null) {
            mRoomCallback.onRoomDestroy(roomId, callback);
        }
    }

    public void getRoomIdList(GetCallback callback) {
        if (mRoomCallback != null) {
            mRoomCallback.onGetRoomIdList(callback);
        }
    }

    public interface RoomCallback {
        void onRoomCreate(String roomId, ActionCallback callback);

        void onRoomDestroy(String roomId, ActionCallback callback);

        void onGetRoomIdList(GetCallback callback);
    }


    public interface ActionCallback {
        void onSuccess();

        void onError(int code, String message);
    }


    public interface GetCallback {
        void onSuccess(List<String> list);

        void onError(int code, String message);
    }

    public boolean isAddCallBack() {
        return mRoomCallback != null;
    }

    public void getGroupInfo(final String roomId, final GetGroupInfoCallback callback) {
        List<String> roomIdList = new ArrayList<>();
        roomIdList.add(roomId);
        Log.i(TAG, "get room id list " + roomIdList);
        V2TIMManager.getGroupManager().getGroupsInfo(roomIdList, new V2TIMValueCallback<List<V2TIMGroupInfoResult>>() {
            @Override
            public void onError(int i, String s) {
                Log.e(TAG, "get group info list fail, code:" + i + " msg: " + s);
                callback.onFailed(-1, s);
            }

            @Override
            public void onSuccess(List<V2TIMGroupInfoResult> resultList) {
                if (resultList != null && !resultList.isEmpty()) {
                    V2TIMGroupInfoResult result = resultList.get(0);
                    callback.onSuccess(result);
                } else {
                    callback.onFailed(-1, "get groupInfo List is null");
                }
            }
        });
    }

    public interface GetGroupInfoCallback {
        void onSuccess(V2TIMGroupInfoResult result);

        void onFailed(int code, String msg);
    }
}
