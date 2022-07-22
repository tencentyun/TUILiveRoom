package com.tencent.liteav.liveroom;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import com.blankj.utilcode.util.ToastUtils;
import com.tencent.imsdk.v2.V2TIMGroupInfoResult;
import com.tencent.liteav.basic.UserModel;
import com.tencent.liteav.basic.UserModelManager;
import com.tencent.liteav.liveroom.model.LiveRoomManager;
import com.tencent.liteav.liveroom.model.TRTCLiveRoom;
import com.tencent.liteav.liveroom.model.TRTCLiveRoomCallback;
import com.tencent.liteav.liveroom.model.TRTCLiveRoomDef;
import com.tencent.liteav.liveroom.model.impl.base.TRTCLogger;
import com.tencent.liteav.liveroom.ui.anchor.TCCameraAnchorActivity;
import com.tencent.liteav.liveroom.ui.audience.TCAudienceActivity;
import com.tencent.liteav.liveroom.ui.common.utils.TCConstants;
import com.tencent.qcloud.tuicore.TUILogin;

import java.util.Collections;
import java.util.List;

public class TUILiveRoomImpl extends TUILiveRoom {
    private static final String TAG = "TUILiveRoomImpl";

    private static TUILiveRoomImpl sInstance;

    private Context mContext;

    private TUILiveRoomListener mListener;

    private TRTCLiveRoom mLiveRoom;

    public static TUILiveRoomImpl sharedInstance(Context context) {
        if (sInstance == null) {
            synchronized (TUILiveRoomImpl.class) {
                if (sInstance == null) {
                    sInstance = new TUILiveRoomImpl(context);
                }
            }
        }
        return sInstance;
    }

    private TUILiveRoomImpl(Context context) {
        mContext = context.getApplicationContext();
        mLiveRoom = TRTCLiveRoom.sharedInstance(mContext);
    }

    @Override
    public void createRoom(final int roomId, final String name, final String coverUrl) {
        if (mContext == null) {
            TRTCLogger.e(TAG, "context is null");
            if (mListener != null) {
                mListener.onRoomCreate(-1, "context is null");
            }
            return;
        }

        if (roomId == 0) {
            TRTCLogger.e(TAG, "roomId is empty");
            if (mListener != null) {
                mListener.onRoomCreate(-1, "roomId is empty");
            }
            return;
        }

        if (TextUtils.isEmpty(name)) {
            TRTCLogger.e(TAG, "roomId is empty");
            if (mListener != null) {
                mListener.onRoomCreate(-1, "roomId is empty");
            }
            return;
        }

        if (!TUILogin.isUserLogined()) {
            TRTCLogger.e(TAG, "user not login");
            if (mListener != null) {
                mListener.onRoomCreate(-1, "user not login");
            }
            return;
        }
        liveVideoLogin(new TRTCLiveRoomCallback.ActionCallback() {
            @Override
            public void onCallback(int code, String msg) {
                if (code == 0) {
                    UserModel userModel = new UserModel();
                    userModel.userId = roomId + "";
                    userModel.userName = name;
                    userModel.userAvatar = coverUrl;
                    final UserModelManager manager = UserModelManager.getInstance();
                    manager.setUserModel(userModel);
                    Intent intent = new Intent(mContext, TCCameraAnchorActivity.class);
                    intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent);
                } else {
                    TRTCLogger.e(TAG, msg);
                    if (mListener != null) {
                        mListener.onRoomCreate(code, msg);
                    }
                }
            }
        });

    }

    @Override
    public void enterRoom(final String roomId) {
        if (mContext == null) {
            TRTCLogger.e(TAG, "context is null");
            if (mListener != null) {
                mListener.onRoomEnter(-1, "context is null");
            }
            return;
        }

        if (TextUtils.isEmpty(roomId)) {
            TRTCLogger.e(TAG, "roomId is empty");
            if (mListener != null) {
                mListener.onRoomEnter(-1, "roomId is empty");
            }
            return;
        }

        if (!TUILogin.isUserLogined()) {
            TRTCLogger.e(TAG, "user not login");
            if (mListener != null) {
                mListener.onRoomEnter(-1, "user not login");
            }
            return;
        }
        liveVideoLogin(new TRTCLiveRoomCallback.ActionCallback() {
            @Override
            public void onCallback(int code, String msg) {
                if (code == 0) {
                    LiveRoomManager.getInstance().getGroupInfo(roomId + "", new LiveRoomManager.GetGroupInfoCallback() {
                        @Override
                        public void onSuccess(V2TIMGroupInfoResult result) {
                            if (isRoomExist(result)) {
                                realEnterRoom(roomId);
                            } else {
                                ToastUtils.showLong(R.string.trtcliveroom_room_not_exist);
                            }
                        }

                        @Override
                        public void onFailed(int code, String msg) {
                            ToastUtils.showLong(msg);
                        }
                    });
                } else {
                    TRTCLogger.e(TAG, msg);
                    if (mListener != null) {
                        mListener.onRoomEnter(code, msg);
                    }
                }
            }
        });
    }

    @Override
    public void setListener(TUILiveRoomListener listener) {
        mListener = listener;
    }

    private boolean isRoomExist(V2TIMGroupInfoResult result) {
        if (result == null) {
            Log.e(TAG, "room not exist result is null");
            return false;
        }
        return result.getResultCode() == 0;
    }

    private void realEnterRoom(final String roomId) {
        mLiveRoom.getRoomInfos(Collections.singletonList(roomId), new TRTCLiveRoomCallback.RoomInfoCallback() {
            @Override
            public void onCallback(int code, String msg, List<TRTCLiveRoomDef.TRTCLiveRoomInfo> list) {
                if (0 == code && null != list && !list.isEmpty()) {
                    TRTCLiveRoomDef.TRTCLiveRoomInfo info = list.get(0);
                    gotoAudience(roomId, info.ownerId, info.roomName);
                }
            }
        });
    }

    private void gotoAudience(String roomId, String anchorId, String anchorName) {
        Intent intent = new Intent(mContext, TCAudienceActivity.class);
        intent.putExtra(TCConstants.GROUP_ID, roomId);
        intent.putExtra(TCConstants.PUSHER_ID, anchorId);
        intent.putExtra(TCConstants.PUSHER_NAME, anchorName);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void liveVideoLogin(final TRTCLiveRoomCallback.ActionCallback callback) {
        TRTCLiveRoomDef.TRTCLiveRoomConfig config = new TRTCLiveRoomDef.TRTCLiveRoomConfig(false,
                "");
        mLiveRoom.login(TUILogin.getSdkAppId(), TUILogin.getUserId(),
                TUILogin.getUserSig(), config, new TRTCLiveRoomCallback.ActionCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                        if (code == 0) {
                            mLiveRoom.setSelfProfile(TUILogin.getNickName(),
                                    TUILogin.getFaceUrl(), new TRTCLiveRoomCallback.ActionCallback() {
                                        @Override
                                        public void onCallback(int code, String msg) {
                                            callback.onCallback(code, msg);
                                        }
                                    });
                        } else {
                            callback.onCallback(code, msg);
                        }
                    }
                });
    }
}
