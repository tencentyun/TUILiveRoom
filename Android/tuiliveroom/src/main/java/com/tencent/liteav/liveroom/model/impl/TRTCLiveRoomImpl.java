package com.tencent.liteav.liveroom.model.impl;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.tencent.liteav.audio.TXAudioEffectManager;
import com.tencent.liteav.basic.UserModel;
import com.tencent.liteav.basic.UserModelManager;
import com.tencent.liteav.beauty.TXBeautyManager;
import com.tencent.liteav.liveroom.model.TRTCLiveRoom;
import com.tencent.liteav.liveroom.model.TRTCLiveRoomCallback;
import com.tencent.liteav.liveroom.model.TRTCLiveRoomDef;
import com.tencent.liteav.liveroom.model.TRTCLiveRoomDelegate;
import com.tencent.liteav.liveroom.model.impl.av.liveplayer.TXLivePlayerRoom;
import com.tencent.liteav.liveroom.model.impl.av.trtc.ITXTRTCLiveRoomDelegate;
import com.tencent.liteav.liveroom.model.impl.av.trtc.TXTRTCLiveRoom;
import com.tencent.liteav.liveroom.model.impl.av.trtc.TXTRTCMixUser;
import com.tencent.liteav.liveroom.model.impl.base.TRTCLogger;
import com.tencent.liteav.liveroom.model.impl.base.TXCallback;
import com.tencent.liteav.liveroom.model.impl.base.TXRoomInfo;
import com.tencent.liteav.liveroom.model.impl.base.TXRoomInfoListCallback;
import com.tencent.liteav.liveroom.model.impl.base.TXUserInfo;
import com.tencent.liteav.liveroom.model.impl.base.TXUserListCallback;
import com.tencent.liteav.liveroom.model.impl.room.ITXRoomServiceDelegate;
import com.tencent.liteav.liveroom.model.impl.room.impl.TXRoomService;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.trtc.TRTCCloud;
import com.tencent.trtc.TRTCCloudDef;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tencent.liteav.liveroom.model.TRTCLiveRoomDef.ROOM_STATUS_NONE;
import static com.tencent.liteav.liveroom.model.TRTCLiveRoomDef.ROOM_STATUS_PK;

public class TRTCLiveRoomImpl extends TRTCLiveRoom implements ITXTRTCLiveRoomDelegate, ITXRoomServiceDelegate {
    private static final int CODE_SUCCESS   = 0;
    private static final int CODE_ERROR     = -1;
    public static final  int PK_ANCHOR_NUMS = 2;


    private static final class Role {
        static final int UNKNOWN       = 0;
        static final int TRTC_ANCHOR   = 1;
        static final int TRTC_AUDIENCE = 2;
        static final int CDN_AUDIENCE  = 3;
    }


    private static final class TXCallbackHolder implements TXCallback {
        private WeakReference<TRTCLiveRoomImpl> wefImpl;

        private TRTCLiveRoomCallback.ActionCallback realCallback;

        TXCallbackHolder(TRTCLiveRoomImpl impl) {
            wefImpl = new WeakReference<>(impl);
        }

        public void setRealCallback(TRTCLiveRoomCallback.ActionCallback callback) {
            realCallback = callback;
        }

        @Override
        public void onCallback(final int code, final String msg) {
            TRTCLiveRoomImpl impl = wefImpl.get();
            if (impl != null) {
                impl.runOnDelegateThread(new Runnable() {
                    @Override
                    public void run() {
                        if (realCallback != null) {
                            realCallback.onCallback(code, msg);
                        }
                    }
                });
            }
        }
    }


    private static final String           TAG = "TRTCLiveRoom";
    private static       TRTCLiveRoomImpl sInstance;

    private TRTCLiveRoomDelegate mDelegate;
    private Handler              mMainHandler;
    private Handler              mDelegateHandler;

    private int                                mSDKAppId;
    private String                             mRoomId;
    private String                             mUserId;
    private String                             mUserSign;
    private TRTCLiveRoomDef.TRTCLiveRoomConfig mRoomConfig;
    private int                                mRoomLiveStatus = ROOM_STATUS_NONE;
    private TRTCLiveRoomDef.TRTCLiveRoomInfo   mLiveRoomInfo;
    private Set<String>                        mAnchorList;
    private Set<String>                        mAudienceList;

    private Map<String, TXCloudVideoView> mPlayViewMap;

    private int mCurrentRole;
    private int mTargetRole;
    private int mOriginalRole;

    private TXCallbackHolder mJoinAnchorCallbackHolder;
    private TXCallbackHolder mRequestPKHolder;

    private Map<String, String> mJoinAnchorMap = new HashMap<>();
    private Map<String, String> mRoomPkMap     = new HashMap<>();

    public static synchronized TRTCLiveRoom sharedInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TRTCLiveRoomImpl(context.getApplicationContext());
        }
        return sInstance;
    }

    public static synchronized void destroySharedInstance() {
        if (sInstance != null) {
            sInstance.destroy();
            sInstance = null;
        }
    }

    private TRTCLiveRoomImpl(Context context) {
        mCurrentRole = Role.CDN_AUDIENCE;
        mOriginalRole = Role.CDN_AUDIENCE;
        mTargetRole = Role.CDN_AUDIENCE;
        mMainHandler = new Handler(Looper.getMainLooper());
        mDelegateHandler = new Handler(Looper.getMainLooper());
        mLiveRoomInfo = new TRTCLiveRoomDef.TRTCLiveRoomInfo();
        mSDKAppId = 0;
        mUserId = "";
        mUserSign = "";
        TXLivePlayerRoom.getInstance().init(context);
        TXTRTCLiveRoom.getInstance().init(context);
        TXTRTCLiveRoom.getInstance().setDelegate(this);
        TXRoomService.getInstance().init(context);
        TXRoomService.getInstance().setDelegate(this);
        mPlayViewMap = new HashMap<>();
        mAnchorList = new HashSet<>();
        mAudienceList = new HashSet<>();
        mJoinAnchorCallbackHolder = new TXCallbackHolder(this);
        mRequestPKHolder = new TXCallbackHolder(this);
    }

    private void destroy() {
        TXRoomService.getInstance().destroy();
        TRTCCloud.destroySharedInstance();
    }

    private void runOnMainThread(Runnable runnable) {
        Handler handler = mMainHandler;
        if (handler != null) {
            if (handler.getLooper() == Looper.myLooper()) {
                runnable.run();
            } else {
                handler.post(runnable);
            }
        } else {
            runnable.run();
        }
    }

    private void runOnDelegateThread(Runnable runnable) {
        Handler handler = mDelegateHandler;
        if (handler != null) {
            if (handler.getLooper() == Looper.myLooper()) {
                runnable.run();
            } else {
                handler.post(runnable);
            }
        } else {
            runnable.run();
        }
    }

    @Override
    public void setDelegate(final TRTCLiveRoomDelegate delegate) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.setDelegate(delegate);
                mDelegate = delegate;
            }
        });
    }

    @Override
    public void setDelegateHandler(final Handler handler) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                if (handler != null) {
                    mDelegateHandler = handler;
                }
            }
        });
    }

    @Override
    public void login(final int sdkAppId, final String userId, final String userSig,
                      final TRTCLiveRoomDef.TRTCLiveRoomConfig config,
                      final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.i(TAG, "start login, sdkAppId:" + sdkAppId + " userId:"
                        + userId + " config:" + config + " sign is empty:" + TextUtils.isEmpty(userSig));
                if (sdkAppId == 0 || TextUtils.isEmpty(userId) || TextUtils.isEmpty(userSig) || config == null) {
                    TRTCLogger.e(TAG, "start login fail. params invalid.");
                    if (callback != null) {
                        callback.onCallback(-1, "登录失败，参数有误");
                    }
                    return;
                }
                mSDKAppId = sdkAppId;
                mUserId = userId;
                mUserSign = userSig;
                mRoomConfig = config;
                TRTCLogger.i(TAG, "start login room service");
                TXRoomService.getInstance().login(sdkAppId, userId, userSig, new TXCallback() {
                    @Override
                    public void onCallback(final int code, final String msg) {
                        TRTCLogger.i(TAG, "login room service finish, code:" + code + " msg:" + msg);
                        runOnDelegateThread(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onCallback(code, msg);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public void logout(final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.i(TAG, "start logout");
                mSDKAppId = 0;
                mUserId = "";
                mUserSign = "";
                TRTCLogger.i(TAG, "start logout room service");
                TXRoomService.getInstance().logout(new TXCallback() {
                    @Override
                    public void onCallback(final int code, final String msg) {
                        TRTCLogger.i(TAG, "logout room service finish, code:" + code + " msg:" + msg);
                        runOnDelegateThread(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onCallback(code, msg);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public void setSelfProfile(final String userName, final String avatarURL,
                               final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.i(TAG, "set profile, user name:" + userName + " avatar url:" + avatarURL);
                TXRoomService.getInstance().setSelfProfile(userName, avatarURL, new TXCallback() {
                    @Override
                    public void onCallback(final int code, final String msg) {
                        TRTCLogger.i(TAG, "set profile finish, code:" + code + " msg:" + msg);
                        runOnDelegateThread(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onCallback(code, msg);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public void createRoom(final String strRoomId, final TRTCLiveRoomDef.TRTCCreateRoomParam roomParam,
                           final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.i(TAG, "create room, room id:" + strRoomId + " info:" + roomParam);
                if (TextUtils.isEmpty(strRoomId)) {
                    TRTCLogger.e(TAG, "create room fail. params invalid");
                    return;
                }
                mRoomId = strRoomId;
                mCurrentRole = Role.UNKNOWN;
                mTargetRole = Role.UNKNOWN;
                mOriginalRole = Role.TRTC_ANCHOR;
                mRoomLiveStatus = ROOM_STATUS_NONE;

                mAnchorList.clear();
                mAudienceList.clear();

                mTargetRole = Role.TRTC_ANCHOR;
                final String roomName = (roomParam == null ? "" : roomParam.roomName);
                final String roomCover = (roomParam == null ? "" : roomParam.coverUrl);
                TXRoomService.getInstance().createRoom(strRoomId, roomName, roomCover, new TXCallback() {
                    @Override
                    public void onCallback(final int code, final String msg) {
                        TRTCLogger.i(TAG, "create room in service, code:" + code + " msg:" + msg);
                        if (code == 0) {
                            enterTRTCRoomInner(strRoomId, mUserId, mUserSign,
                                    TRTCCloudDef.TRTCRoleAnchor, new TRTCLiveRoomCallback.ActionCallback() {
                                        @Override
                                        public void onCallback(final int code, final String msg) {
                                            if (code == 0) {
                                                runOnMainThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        mAnchorList.add(mUserId);
                                                        mCurrentRole = Role.TRTC_ANCHOR;
                                                    }
                                                });
                                            }
                                            runOnDelegateThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (callback != null) {
                                                        callback.onCallback(code, msg);
                                                    }
                                                }
                                            });
                                        }
                                    });
                            return;
                        } else {
                            runOnDelegateThread(new Runnable() {
                                @Override
                                public void run() {
                                    TRTCLiveRoomDelegate delegate = mDelegate;
                                    if (delegate != null) {
                                        delegate.onError(code, msg);
                                    }
                                }
                            });
                        }
                        runOnDelegateThread(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onCallback(code, msg);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public void destroyRoom(final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.i(TAG, "start destroy room.");
                quitRoomPK(null);

                TRTCLogger.i(TAG, "start exit trtc room.");
                TXTRTCLiveRoom.getInstance().exitRoom(new TXCallback() {
                    @Override
                    public void onCallback(final int code, final String msg) {
                        TRTCLogger.i(TAG, "exit trtc room finish, code:" + code + " msg:" + msg);
                        if (code != 0) {
                            runOnDelegateThread(new Runnable() {
                                @Override
                                public void run() {
                                    TRTCLiveRoomDelegate delegate = mDelegate;
                                    if (delegate != null) {
                                        delegate.onError(code, msg);
                                    }
                                }
                            });
                        }
                    }
                });

                TRTCLogger.i(TAG, "start destroy room service.");
                setLiveRoomType(false);
                TXRoomService.getInstance().destroyRoom(new TXCallback() {
                    @Override
                    public void onCallback(final int code, final String msg) {
                        TRTCLogger.i(TAG, "destroy room finish, code:" + code + " msg:" + msg);
                        runOnDelegateThread(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onCallback(code, msg);
                                }
                            }
                        });
                    }
                });
                mPlayViewMap.clear();

                mCurrentRole = Role.UNKNOWN;
                mTargetRole = Role.UNKNOWN;
                mOriginalRole = Role.UNKNOWN;
                mRoomLiveStatus = ROOM_STATUS_NONE;

                mAnchorList.clear();
                mRoomId = "";
                mJoinAnchorCallbackHolder.setRealCallback(null);
                mRequestPKHolder.setRealCallback(null);
            }
        });
    }

    @Override
    public void enterRoom(final String roomId, final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                mCurrentRole = Role.UNKNOWN;
                mTargetRole = Role.UNKNOWN;
                mOriginalRole = Role.UNKNOWN;
                mRoomLiveStatus = ROOM_STATUS_NONE;
                mAnchorList.clear();
                mRoomId = roomId;
                boolean useCDNFirst = false;
                TRTCLiveRoomDef.TRTCLiveRoomConfig config = mRoomConfig;
                if (config != null) {
                    useCDNFirst = config.useCDNFirst;
                }
                if (useCDNFirst) {
                    mOriginalRole = Role.CDN_AUDIENCE;
                } else {
                    mOriginalRole = Role.TRTC_AUDIENCE;
                }

                TRTCLogger.i(TAG, "start enter room, room id:" + roomId + " use cdn:" + useCDNFirst);
                final boolean finalUseCDNFirst = useCDNFirst;

                TXRoomService.getInstance().enterRoom(String.valueOf(roomId), new TXCallback() {
                    @Override
                    public void onCallback(final int code, final String msg) {
                        TRTCLogger.i(TAG, "enter room service finish, room id:"
                                + roomId + " code:" + code + " msg:" + msg);
                        runOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                if (finalUseCDNFirst) {
                                    runOnDelegateThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (callback != null) {
                                                callback.onCallback(code, msg);
                                            }
                                        }
                                    });
                                } else {
                                    TRTCLogger.i(TAG, "start enter trtc room.");
                                    mTargetRole = Role.TRTC_AUDIENCE;
                                    if (code != 0) {
                                        runOnDelegateThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                TRTCLiveRoomDelegate delegate = mDelegate;
                                                if (delegate != null) {
                                                    delegate.onError(code, msg);
                                                }
                                                if (callback != null) {
                                                    callback.onCallback(code, msg);
                                                }
                                            }
                                        });
                                    } else {
                                        enterTRTCRoomInner(roomId, mUserId, mUserSign,
                                                TRTCCloudDef.TRTCRoleAudience,
                                                new TRTCLiveRoomCallback.ActionCallback() {
                                                    @Override
                                                    public void onCallback(final int code, final String msg) {
                                                        TRTCLogger.i(TAG, "trtc enter room finish, room id:"
                                                                + roomId + " code:" + code + " msg:" + msg);
                                                        runOnMainThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                if (code == 0) {
                                                                    mCurrentRole = Role.TRTC_AUDIENCE;
                                                                }
                                                            }
                                                        });
                                                        runOnDelegateThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                if (callback != null) {
                                                                    callback.onCallback(code, msg);
                                                                }
                                                            }
                                                        });
                                                    }
                                                });
                                    }
                                }
                            }
                        });
                    }
                });
            }
        });
    }


    @Override
    public void exitRoom(final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.i(TAG, "start exit room.");
                if (mCurrentRole == Role.TRTC_ANCHOR) {
                    stopPublish(null);
                }

                TXTRTCLiveRoom.getInstance().exitRoom(new TXCallback() {
                    @Override
                    public void onCallback(final int code, final String msg) {
                        if (code != 0) {
                            runOnDelegateThread(new Runnable() {
                                @Override
                                public void run() {
                                    TRTCLiveRoomDelegate delegate = mDelegate;
                                    if (delegate != null) {
                                        delegate.onError(code, msg);
                                    }
                                }
                            });
                        }
                    }
                });
                TRTCLogger.i(TAG, "start stop all live player.");
                TXLivePlayerRoom.getInstance().stopAllPlay();
                TRTCLogger.i(TAG, "start exit room service.");
                setLiveRoomType(false);
                TXRoomService.getInstance().exitRoom(new TXCallback() {
                    @Override
                    public void onCallback(final int code, final String msg) {
                        TRTCLogger.i(TAG, "exit room finish, code:" + code + " msg:" + msg);
                        runOnDelegateThread(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onCallback(code, msg);
                                }
                            }
                        });
                    }
                });
                mPlayViewMap.clear();
                mAnchorList.clear();
                mRoomId = "";
                mTargetRole = Role.UNKNOWN;
                mOriginalRole = Role.UNKNOWN;
                mCurrentRole = Role.UNKNOWN;
                mJoinAnchorCallbackHolder.setRealCallback(null);
                mRequestPKHolder.setRealCallback(null);
            }
        });
    }

    @Override
    public void getRoomInfos(final List<String> roomIdList, final TRTCLiveRoomCallback.RoomInfoCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                final List<TRTCLiveRoomDef.TRTCLiveRoomInfo> trtcLiveRoomInfoList = new ArrayList<>();
                TRTCLogger.i(TAG, "start getRoomInfos: " + roomIdList);
                List<String> strings = new ArrayList<>();
                for (String id : roomIdList) {
                    strings.add(id);
                }
                TXRoomService.getInstance().getRoomInfos(strings, new TXRoomInfoListCallback() {
                    @Override
                    public void onCallback(int code, String msg, List<TXRoomInfo> list) {
                        if (code == 0) {
                            for (TXRoomInfo info : list) {
                                TRTCLogger.i(TAG, info.toString());
                                if (TextUtils.isEmpty(info.ownerId)) {
                                    continue;
                                }
                                TRTCLiveRoomDef.TRTCLiveRoomInfo liveRoomInfo = new TRTCLiveRoomDef.TRTCLiveRoomInfo();
                                int translateRoomId;
                                try {
                                    translateRoomId = Integer.valueOf(info.roomId);
                                } catch (NumberFormatException e) {
                                    continue;
                                }
                                liveRoomInfo.roomId = translateRoomId;
                                liveRoomInfo.memberCount = info.memberCount;
                                liveRoomInfo.roomName = info.roomName;
                                liveRoomInfo.ownerId = info.ownerId;
                                liveRoomInfo.coverUrl = info.coverUrl;
                                liveRoomInfo.streamUrl = info.streamUrl;
                                liveRoomInfo.ownerName = info.ownerName;
                                trtcLiveRoomInfoList.add(liveRoomInfo);
                            }
                        }
                        if (callback != null) {
                            callback.onCallback(code, msg, trtcLiveRoomInfoList);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void getAnchorList(final TRTCLiveRoomCallback.UserListCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                List<String> anchorList = new ArrayList<>(mAnchorList);
                if (anchorList.size() > 0) {
                    TRTCLogger.i(TAG, "start getAnchorList");
                    TXRoomService.getInstance().getUserInfo(anchorList, new TXUserListCallback() {
                        @Override
                        public void onCallback(int code, String msg, List<TXUserInfo> list) {
                            if (code == 0) {
                                List<TRTCLiveRoomDef.TRTCLiveUserInfo> trtcLiveUserInfoList = new ArrayList<>();
                                for (TXUserInfo info : list) {
                                    TRTCLogger.i(TAG, info.toString());
                                    TRTCLiveRoomDef.TRTCLiveUserInfo userInfo = new TRTCLiveRoomDef.TRTCLiveUserInfo();
                                    userInfo.userId = info.userId;
                                    userInfo.userName = info.userName;
                                    userInfo.userAvatar = info.avatarURL;
                                    trtcLiveUserInfoList.add(userInfo);
                                }
                                callback.onCallback(code, msg, trtcLiveUserInfoList);
                            } else {
                                callback.onCallback(code, msg, new ArrayList<TRTCLiveRoomDef.TRTCLiveUserInfo>());
                            }
                            TRTCLogger.i(TAG, "onCallback: " + code + " " + msg);
                        }
                    });
                } else {
                    if (callback != null) {
                        callback.onCallback(0, "用户列表为空", new ArrayList<TRTCLiveRoomDef.TRTCLiveUserInfo>());
                    }
                }
            }
        });
    }

    @Override
    public void getAudienceList(final TRTCLiveRoomCallback.UserListCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXRoomService.getInstance().getAudienceList(new TXUserListCallback() {
                    @Override
                    public void onCallback(final int code, final String msg, final List<TXUserInfo> list) {
                        TRTCLogger.i(TAG, "get audience list finish, code:"
                                + code + " msg:" + msg + " list:" + (list != null ? list.size() : 0));
                        runOnDelegateThread(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    List<TRTCLiveRoomDef.TRTCLiveUserInfo> userList = new ArrayList<>();
                                    if (list != null) {
                                        for (TXUserInfo info : list) {
                                            if (mAnchorList.contains(info.userId)) {
                                                continue;
                                            }
                                            TRTCLiveRoomDef.TRTCLiveUserInfo trtcUserInfo =
                                                    new TRTCLiveRoomDef.TRTCLiveUserInfo();
                                            trtcUserInfo.userId = info.userId;
                                            trtcUserInfo.userAvatar = info.avatarURL;
                                            trtcUserInfo.userName = info.userName;
                                            userList.add(trtcUserInfo);
                                            TRTCLogger.i(TAG, "info:" + info);
                                        }
                                    }
                                    callback.onCallback(code, msg, userList);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public void startCameraPreview(final boolean isFront,
                                   final TXCloudVideoView view, final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.i(TAG, "start camera preview。");
                TXTRTCLiveRoom.getInstance().startCameraPreview(isFront, view, new TXCallback() {
                    @Override
                    public void onCallback(final int code, final String msg) {
                        TRTCLogger.i(TAG, "start camera preview finish, code:" + code + " msg:" + msg);
                        runOnDelegateThread(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onCallback(code, msg);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public void stopCameraPreview() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.i(TAG, "stop camera preview.");
                TXTRTCLiveRoom.getInstance().stopCameraPreview();
            }
        });
    }

    @Override
    public void startPublish(final String streamId, final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                String tempStreamId = streamId;
                if (TextUtils.isEmpty(tempStreamId)) {
                    tempStreamId = mSDKAppId + "_" + mRoomId + "_" + mUserId + "";
                }
                final String finalStreamId = tempStreamId;

                if (!isTRTCMode()) {
                    if (TextUtils.isEmpty(mRoomId)) {
                        TRTCLogger.e(TAG, "start publish error, room id is empty.");
                        if (callback != null) {
                            runOnDelegateThread(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onCallback(-1, "推流失败, room id 为空");
                                }
                            });
                        }
                        return;
                    }
                    mTargetRole = Role.TRTC_ANCHOR;
                    TRTCLogger.i(TAG, "enter trtc room before start publish.");
                    enterTRTCRoomInner(mRoomId, mUserId, mUserSign,
                            TRTCCloudDef.TRTCRoleAudience, new TRTCLiveRoomCallback.ActionCallback() {
                                @Override
                                public void onCallback(final int code, final String msg) {
                                    TRTCLogger.i(TAG, "enter trtc room finish, code:" + code + " msg:" + msg);
                                    mCurrentRole = Role.TRTC_ANCHOR;
                                    if (code == 0) {
                                        startPublishInner(finalStreamId, callback);
                                        if (mOriginalRole == Role.CDN_AUDIENCE) {
                                            changeToTRTCPlay();
                                        }
                                    } else {
                                        runOnDelegateThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                callback.onCallback(code, msg);
                                            }
                                        });
                                    }
                                }
                            });
                } else {
                    startPublishInner(finalStreamId, new TRTCLiveRoomCallback.ActionCallback() {
                        @Override
                        public void onCallback(final int code, final String msg) {
                            runOnDelegateThread(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onCallback(code, msg);
                                }
                            });
                        }
                    });
                }
                TRTCLogger.i(TAG, "update room service stream id:" + finalStreamId);
                TXRoomService.getInstance().updateStreamId(finalStreamId, new TXCallback() {
                    @Override
                    public void onCallback(final int code, final String msg) {
                        TRTCLogger.i(TAG, "room service start publish, code:" + code + " msg:" + msg);
                        if (code != 0) {
                            runOnDelegateThread(new Runnable() {
                                @Override
                                public void run() {
                                    TRTCLiveRoomDelegate delegate = mDelegate;
                                    if (delegate != null) {
                                        delegate.onError(code, msg);
                                    }
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    @Override
    public void stopPublish(final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.i(TAG, "stop publish");
                TXTRTCLiveRoom.getInstance().stopPublish(new TXCallback() {
                    @Override
                    public void onCallback(final int code, final String msg) {
                        TRTCLogger.i(TAG, "stop publish finish, code:" + code + " msg:" + msg);
                        if (mOriginalRole == Role.CDN_AUDIENCE) {
                            mTargetRole = Role.CDN_AUDIENCE;

                            TRTCLogger.i(TAG, "start exit trtc room.");
                            TXTRTCLiveRoom.getInstance().exitRoom(new TXCallback() {
                                @Override
                                public void onCallback(int code, String msg) {
                                    TRTCLogger.i(TAG, "exit trtc room finish, code:" + code + " msg:" + msg);
                                    runOnMainThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mCurrentRole = Role.CDN_AUDIENCE;
                                            changeToCDNPlay();
                                        }
                                    });
                                }
                            });
                        } else {
                            runOnDelegateThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (callback != null) {
                                        callback.onCallback(code, msg);
                                    }
                                }
                            });
                        }
                    }
                });

                TRTCLogger.i(TAG, "start update stream id");
                TXRoomService.getInstance().updateStreamId("", new TXCallback() {
                    @Override
                    public void onCallback(final int code, final String msg) {
                        TRTCLogger.i(TAG, "room service update stream id finish, code:" + code + " msg:" + msg);
                        if (code != 0) {
                            runOnDelegateThread(new Runnable() {
                                @Override
                                public void run() {
                                    TRTCLiveRoomDelegate delegate = mDelegate;
                                    if (delegate != null) {
                                        delegate.onError(code, msg);
                                    }
                                }
                            });
                        }
                    }
                });
                if (mOriginalRole == Role.TRTC_AUDIENCE || mOriginalRole == Role.CDN_AUDIENCE) {
                    TXRoomService.getInstance().quitLinkMic();
                }
            }
        });
    }

    private void changeToCDNPlay() {
        TRTCLogger.i(TAG, "switch trtc to cdn play");
        TXTRTCLiveRoom.getInstance().stopAllPlay();
        final String ownerId = TXRoomService.getInstance().getOwnerUserId();
        if (!TextUtils.isEmpty(ownerId)) {
            Set<String> leaveAnchorSet = new HashSet<>();

            Iterator<String> anchorIterator = mAnchorList.iterator();
            while (anchorIterator.hasNext()) {
                String userId = anchorIterator.next();
                if (!TextUtils.isEmpty(userId) && !userId.equals(ownerId)) {
                    leaveAnchorSet.add(userId);
                    anchorIterator.remove();
                    mPlayViewMap.remove(userId);
                }
            }

            TRTCLiveRoomDelegate delegate = mDelegate;
            if (delegate != null) {
                for (String userId : leaveAnchorSet) {
                    delegate.onAnchorExit(userId);
                }
            }

            final String ownerPlayURL = getPlayURL(ownerId);
            if (!TextUtils.isEmpty(ownerPlayURL)) {
                TXCloudVideoView view = mPlayViewMap.get(ownerId);
                TXLivePlayerRoom.getInstance().startPlay(ownerPlayURL, view, null);
            } else {
                TRTCLogger.e(TAG, "change to play cdn fail, can't get owner play url, owner id:" + ownerId);
            }
        } else {
            TRTCLogger.e(TAG, "change to play cdn fail, can't get owner user id.");
        }
    }


    private void changeToTRTCPlay() {
        TRTCLogger.i(TAG, "switch cdn to trtc play");
        TXLivePlayerRoom.getInstance().stopAllPlay();
        for (String userId : mAnchorList) {
            TXTRTCLiveRoom.getInstance().startPlay(userId, mPlayViewMap.get(userId), null);
        }
    }

    @Override
    public void startPlay(final String userId, final TXCloudVideoView view,
                          final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                mPlayViewMap.put(userId, view);
                if (isTRTCMode()) {
                    TXTRTCLiveRoom.getInstance().startPlay(userId, view, new TXCallback() {
                        @Override
                        public void onCallback(final int code, final String msg) {
                            TRTCLogger.i(TAG, "start trtc play finish, code:" + code + " msg:" + msg);
                            runOnDelegateThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (callback != null) {
                                        callback.onCallback(code, msg);
                                    }
                                }
                            });
                        }
                    });
                } else {
                    String playURL = getPlayURL(userId);
                    if (!TextUtils.isEmpty(playURL)) {
                        TRTCLogger.i(TAG, "start cdn play, url:" + playURL);
                        TXLivePlayerRoom.getInstance().startPlay(playURL, view, new TXCallback() {
                            @Override
                            public void onCallback(final int code, final String msg) {
                                TRTCLogger.i(TAG, "start cdn play finish, code:" + code + " msg:" + msg);
                                runOnDelegateThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (callback != null) {
                                            callback.onCallback(code, msg);
                                        }
                                    }
                                });
                            }
                        });
                    } else {
                        TRTCLogger.e(TAG, "start cdn play error, can't find stream id by user id:" + userId);
                        runOnDelegateThread(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onCallback(-1, "启动CDN播放失败，找不到对应的流ID");
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    @Override
    public void stopPlay(final String userId, final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                mPlayViewMap.remove(userId);
                if (isTRTCMode()) {
                    TXTRTCLiveRoom.getInstance().stopPlay(userId, new TXCallback() {
                        @Override
                        public void onCallback(final int code, final String msg) {
                            TRTCLogger.i(TAG, "stop trtc play finish, code:" + code + " msg:" + msg);
                            runOnDelegateThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (callback != null) {
                                        callback.onCallback(code, msg);
                                    }
                                }
                            });
                        }
                    });
                } else {
                    String playURL = getPlayURL(userId);
                    if (!TextUtils.isEmpty(playURL)) {
                        TRTCLogger.i(TAG, "stop play, url:" + playURL);
                        TXLivePlayerRoom.getInstance().stopPlay(playURL, new TXCallback() {
                            @Override
                            public void onCallback(final int code, final String msg) {
                                TRTCLogger.i(TAG, "stop cdn play finish, code:" + code + " msg:" + msg);
                                runOnDelegateThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (callback != null) {
                                            callback.onCallback(code, msg);
                                        }
                                    }
                                });
                            }
                        });
                    } else {
                        TRTCLogger.e(TAG, "stop cdn play error, can't find stream id by user id:" + userId);
                        runOnDelegateThread(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onCallback(-1, "停止播放失败，找不到对应的流ID");
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    @Override
    public void requestJoinAnchor(final String reason, final int timeout,
                                  final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (mRoomLiveStatus == ROOM_STATUS_PK) {
                    //正在PK中
                    runOnDelegateThread(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onCallback(CODE_ERROR, "正在PK中");
                            }
                        }
                    });
                    return;
                }
                if (TXRoomService.getInstance().isLogin()) {
                    TRTCLogger.i(TAG, "start join anchor.");
                    mJoinAnchorCallbackHolder.setRealCallback(callback);
                    TXRoomService.getInstance().requestJoinAnchor(reason, timeout, mJoinAnchorCallbackHolder);
                } else {
                    TRTCLogger.e(TAG, "request join anchor fail, not login yet.");
                    runOnDelegateThread(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onCallback(CODE_ERROR, "请求上麦失败，IM未登录");
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public void responseJoinAnchor(final String userId, final boolean agree, final String reason) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (TXRoomService.getInstance().isLogin()) {
                    TRTCLogger.i(TAG, "response join anchor.");
                    TXRoomService.getInstance().responseJoinAnchor(userId, agree, reason);
                } else {
                    TRTCLogger.e(TAG, "response join anchor fail. not login yet.");
                }
            }
        });
    }

    @Override
    public void kickoutJoinAnchor(final String userId, final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (TXRoomService.getInstance().isLogin()) {
                    TRTCLogger.i(TAG, "kick out join anchor.");
                    TXRoomService.getInstance().kickoutJoinAnchor(userId, new TXCallback() {
                        @Override
                        public void onCallback(final int code, final String msg) {
                            TRTCLogger.i(TAG, "kick out finish, code:" + code + " msg:" + msg);
                            runOnDelegateThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (callback != null) {
                                        callback.onCallback(code, msg);
                                    }
                                }
                            });
                        }
                    });
                } else {
                    TRTCLogger.e(TAG, "kick out fail. not login yet.");
                    runOnDelegateThread(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onCallback(CODE_ERROR, "踢人失败，IM未登录");
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public void cancelRequestJoinAnchor(final String userId, final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (mJoinAnchorMap.containsKey(userId)) {
                    String inviteId = mJoinAnchorMap.get(userId);
                    mJoinAnchorCallbackHolder.setRealCallback(callback);
                    TXRoomService.getInstance().cancelRequestJoinAnchor(inviteId, "", mJoinAnchorCallbackHolder);
                }
            }
        });
    }

    @Override
    public void requestRoomPK(int roomId, String userId, final int timeout,
                              final TRTCLiveRoomCallback.ActionCallback callback) {
        if (TXRoomService.getInstance().isLogin()) {
            TRTCLogger.i(TAG, "request room pk.");
            mRequestPKHolder.setRealCallback(callback);
            String inviteId = TXRoomService.getInstance().requestRoomPK(String.valueOf(roomId),
                    userId, timeout, mRequestPKHolder);
            mRoomPkMap.put(mUserId, inviteId);
        } else {
            TRTCLogger.e(TAG, "request room pk fail. not login yet.");
            runOnDelegateThread(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onCallback(CODE_ERROR, "请求PK失败，IM未登录");
                    }
                }
            });
        }
    }

    @Override
    public void responseRoomPK(final String userId, final boolean agree, final String reason) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (TXRoomService.getInstance().isLogin()) {
                    TRTCLogger.i(TAG, "response pk.");
                    TXRoomService.getInstance().responseRoomPK(userId, agree, reason);
                } else {
                    TRTCLogger.e(TAG, "response pk fail. not login yet.");
                }
            }
        });
    }

    @Override
    public void cancelRequestRoomPK(final String userId, final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (mRoomPkMap.containsKey(mUserId)) {
                    String inviteId = mRoomPkMap.get(userId);
                    mRequestPKHolder.setRealCallback(callback);
                    TXRoomService.getInstance().cancelRequestRoomPK(inviteId, "", mRequestPKHolder);
                    mRoomPkMap.remove(mUserId);
                }
            }
        });
    }

    @Override
    public void quitRoomPK(final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (TXRoomService.getInstance().isLogin()) {
                    TRTCLogger.i(TAG, "quit pk.");
                    TXRoomService.getInstance().quitRoomPK(new TXCallback() {
                        @Override
                        public void onCallback(final int code, final String msg) {
                            TRTCLogger.i(TAG, "quit pk finish, code:" + code + " msg:" + msg);
                            runOnDelegateThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (callback != null) {
                                        callback.onCallback(code, msg);
                                    }
                                }
                            });
                        }
                    });
                } else {
                    TRTCLogger.i(TAG, "quit pk fail.not login yet.");
                    runOnDelegateThread(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onCallback(CODE_ERROR, "退出PK失败，IM未登录");
                            }
                        }
                    });
                }
                TXTRTCLiveRoom.getInstance().stopPK();
            }
        });
    }

    @Override
    public void switchCamera() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.i(TAG, "switch camera.");
                TXTRTCLiveRoom.getInstance().switchCamera();
            }
        });
    }

    @Override
    public void setMirror(final boolean isMirror) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.i(TAG, "set mirror.");
                TXTRTCLiveRoom.getInstance().setMirror(isMirror);
            }
        });
    }

    @Override
    public void muteLocalAudio(final boolean mute) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.i(TAG, "mute local audio, mute:" + mute);
                TXTRTCLiveRoom.getInstance().muteLocalAudio(mute);
            }
        });
    }

    @Override
    public void muteRemoteAudio(final String userId, final boolean mute) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (isTRTCMode()) {
                    TRTCLogger.i(TAG, "mute trtc audio, user id:" + userId);
                    TXTRTCLiveRoom.getInstance().muteRemoteAudio(userId, mute);
                } else {
                    TRTCLogger.i(TAG, "mute cnd audio, user id:" + userId);
                    String playURL = getPlayURL(userId);
                    if (!TextUtils.isEmpty(playURL)) {
                        // 走 CDN
                        TRTCLogger.i(TAG, "mute cdn audio success, url:" + playURL);
                        TXLivePlayerRoom.getInstance().muteRemoteAudio(playURL, mute);
                    } else {
                        TRTCLogger.e(TAG, "mute cdn remote audio fail, exchange stream id fail. user id:" + userId);
                    }
                }
            }
        });
    }

    @Override
    public void muteAllRemoteAudio(final boolean mute) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (isTRTCMode()) {
                    TRTCLogger.i(TAG, "mute all trtc remote audio success, mute:" + mute);
                    TXTRTCLiveRoom.getInstance().muteAllRemoteAudio(mute);
                } else {
                    TRTCLogger.i(TAG, "mute all cdn audio success, mute:" + mute);
                    TXLivePlayerRoom.getInstance().muteAllRemoteAudio(mute);
                }
            }
        });
    }

    @Override
    public void sendRoomTextMsg(final String msg, final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXRoomService.getInstance().sendRoomTextMsg(msg, new TXCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                        if (callback != null) {
                            callback.onCallback(code, msg);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void sendRoomCustomMsg(final String cmd, final String message,
                                  final TRTCLiveRoomCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXRoomService.getInstance().sendRoomCustomMsg(cmd, message, new TXCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                        if (callback != null) {
                            callback.onCallback(code, msg);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void showVideoDebugLog(final boolean isShow) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXLivePlayerRoom.getInstance().showVideoDebugLog(isShow);
                TXTRTCLiveRoom.getInstance().showVideoDebugLog(isShow);
            }
        });
    }

    @Override
    public TXAudioEffectManager getAudioEffectManager() {
        return TXTRTCLiveRoom.getInstance().getAudioEffectManager();
    }

    @Override
    public void setAudioQuality(final int quality) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCLiveRoom.getInstance().setAudioQuality(quality);
            }
        });
    }

    @Override
    public TXBeautyManager getBeautyManager() {
        return TXTRTCLiveRoom.getInstance().getTXBeautyManager();
    }

    @Override
    public void setVideoResolution(final int resolution) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCLiveRoom.getInstance().setVideoResolution(resolution);
            }
        });
    }

    @Override
    public void setVideoFps(final int fps) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCLiveRoom.getInstance().setVideoFps(fps);
            }
        });
    }

    @Override
    public void setVideoBitrate(final int bitrate) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCLiveRoom.getInstance().setVideoBitrate(bitrate);
            }
        });
    }

    private void setLiveRoomType(boolean updateType) {
        if (updateType) {
            UserModelManager.getInstance().getUserModel().userType = UserModel.UserType.LIVE_ROOM;
        } else {
            UserModelManager.getInstance().getUserModel().userType = UserModel.UserType.NONE;
        }
    }

    private void enterTRTCRoomInner(final String strRoomId, final String userId,
                                    final String userSign, final int role,
                                    final TRTCLiveRoomCallback.ActionCallback callback) {
        TRTCLogger.i(TAG, "enter trtc room.");
        setLiveRoomType(true);
        mTargetRole = Role.TRTC_ANCHOR;
        TXTRTCLiveRoom.getInstance().enterRoom(mSDKAppId, strRoomId, userId, userSign, role, new TXCallback() {
            @Override
            public void onCallback(final int code, final String msg) {
                TRTCLogger.i(TAG, "enter trtc room finish, code:" + code + " msg:" + msg);
                runOnDelegateThread(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onCallback(code, msg);
                        }
                    }
                });
            }
        });
    }

    private void startPublishInner(String streamId, final TRTCLiveRoomCallback.ActionCallback callback) {
        TRTCLogger.i(TAG, "start publish stream id:" + streamId);
        TXTRTCLiveRoom.getInstance().startPublish(streamId, new TXCallback() {
            @Override
            public void onCallback(final int code, final String msg) {
                TRTCLogger.i(TAG, "start publish stream finish, code:" + code + " msg:" + msg);
                runOnDelegateThread(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onCallback(code, msg);
                        }
                    }
                });
            }
        });
    }

    private boolean isTRTCMode() {
        TRTCLogger.i(TAG, "isTRTCMode mCurrentRole：" + mCurrentRole + " mTargetRole: " + mTargetRole);
        return mCurrentRole == Role.TRTC_ANCHOR || mCurrentRole == Role.TRTC_AUDIENCE
                || mTargetRole == Role.TRTC_ANCHOR || mTargetRole == Role.TRTC_AUDIENCE;
    }

    private void updateMixConfig() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.i(TAG, "start mix stream:" + mAnchorList.size() + " status:" + mRoomLiveStatus);
                if (TXRoomService.getInstance().isOwner()) {
                    if (mAnchorList.size() > 0) {
                        List<TXTRTCMixUser> needToMixUserList = new ArrayList<>();
                        boolean isPKing = TXRoomService.getInstance().isPKing();
                        if (isPKing) {
                            if (mAnchorList.size() == PK_ANCHOR_NUMS) {
                                String userId = TXRoomService.getInstance().getPKUserId();
                                String roomId = TXRoomService.getInstance().getPKRoomId();
                                if (!TextUtils.isEmpty(userId) && !TextUtils.isEmpty(userId)) {
                                    TXTRTCMixUser user = new TXTRTCMixUser();
                                    user.userId = userId;
                                    user.roomId = roomId;
                                    needToMixUserList.add(user);
                                } else {
                                    TRTCLogger.e(TAG, "set pk mix config fail, pk user id:"
                                            + userId + " pk room id:" + roomId);
                                }
                            } else {
                                TRTCLogger.e(TAG, "set pk mix config fail, available uer size:s" + mAnchorList.size());
                            }
                        } else {
                            for (String userId : mAnchorList) {
                                if (userId.equals(mUserId)) {
                                    continue;
                                }
                                TXTRTCMixUser user = new TXTRTCMixUser();
                                user.roomId = null;
                                user.userId = userId;
                                needToMixUserList.add(user);
                            }
                        }
                        if (needToMixUserList.size() > 0) {
                            TXTRTCLiveRoom.getInstance().setMixConfig(needToMixUserList, isPKing);
                        } else {
                            TXTRTCLiveRoom.getInstance().setMixConfig(null, false);
                        }
                    } else {
                        TXTRTCLiveRoom.getInstance().setMixConfig(null, false);
                    }
                }
            }
        });
    }

    private String getPlayURL(String userId) {
        String streamId = TXRoomService.getInstance().exchangeStreamId(userId);
        if (TextUtils.isEmpty(streamId)) {
            TRTCLogger.e(TAG, "user id:" + userId + " exchange stream id fail.");
            return null;
        }
        TRTCLiveRoomDef.TRTCLiveRoomConfig config = mRoomConfig;
        if (config == null || TextUtils.isEmpty(config.cdnPlayDomain)) {
            TRTCLogger.e(TAG, "get play domain in config fail, config:" + mRoomConfig);
            return null;
        }
        return config.cdnPlayDomain + (config.cdnPlayDomain.endsWith("/") ? "" : "/") + streamId + ".flv";
    }

    @Override
    public void onRoomInfoChange(final TXRoomInfo roomInfo) {
        TRTCLogger.i(TAG, "onRoomInfoChange:" + roomInfo);
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                mLiveRoomInfo.ownerId = roomInfo.ownerId;
                mLiveRoomInfo.coverUrl = roomInfo.coverUrl;
                mLiveRoomInfo.roomId = Integer.valueOf(roomInfo.roomId);
                mLiveRoomInfo.roomName = roomInfo.roomName;
                mLiveRoomInfo.ownerName = roomInfo.ownerName;
                mLiveRoomInfo.streamUrl = roomInfo.streamUrl;
                mLiveRoomInfo.roomStatus = roomInfo.roomStatus;
                mLiveRoomInfo.memberCount = roomInfo.memberCount;

                mRoomLiveStatus = roomInfo.roomStatus;
                updateMixConfig();
                runOnDelegateThread(new Runnable() {
                    @Override
                    public void run() {
                        TRTCLiveRoomDelegate delegate = mDelegate;
                        if (delegate != null) {
                            delegate.onRoomInfoChange(mLiveRoomInfo);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onRoomDestroy(final String roomId) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                TRTCLiveRoomDelegate delegate = mDelegate;
                if (delegate != null) {
                    delegate.onRoomDestroy(roomId);
                }
            }
        });
    }

    @Override
    public void onTRTCAnchorEnter(final String userId) {
        TRTCLogger.i(TAG, "onTRTCAnchorEnter:" + userId);
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXRoomService.getInstance().handleAnchorEnter(userId);
                if (mAnchorList.add(userId)) {
                    handleAnchorEnter(userId);
                } else {
                    TRTCLogger.e(TAG, "trtc anchor enter, but already exit:" + userId);
                }
            }
        });
    }

    private void handleAnchorEnter(final String userId) {
        updateMixConfig();
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                TRTCLiveRoomDelegate delegate = mDelegate;
                if (delegate != null) {
                    delegate.onAnchorEnter(userId);
                }
            }
        });
    }

    @Override
    public void onTRTCAnchorExit(final String userId) {
        TRTCLogger.i(TAG, "onTRTCAnchorExit:" + userId);
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXRoomService.getInstance().handleAnchorExit(userId);
                if (mAnchorList.contains(userId)) {
                    mAnchorList.remove(userId);
                    updateMixConfig();
                    if (TXRoomService.getInstance().isOwner()
                            && mAnchorList.size() == 1) {
                        TXRoomService.getInstance().resetRoomStatus();
                    }
                    runOnDelegateThread(new Runnable() {
                        @Override
                        public void run() {
                            TRTCLiveRoomDelegate delegate = mDelegate;
                            if (delegate != null) {
                                delegate.onAnchorExit(userId);
                            }
                        }
                    });
                } else {
                    TRTCLogger.e(TAG, "trtc anchor exit, but never throw yet, maybe something error.");
                }
            }
        });
    }

    @Override
    public void onTRTCStreamAvailable(final String userId) {
    }

    @Override
    public void onTRTCStreamUnavailable(final String userId) {
    }

    @Override
    public void onRoomAnchorEnter(final String userId) {
    }

    @Override
    public void onRoomAnchorExit(final String userId) {
    }

    @Override
    public void onRoomStreamAvailable(final String userId) {
        TRTCLogger.i(TAG, "onRoomStreamAvailable:" + userId);
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (isTRTCMode()) {
                    return;
                }
                if (!mAnchorList.contains(userId)) {
                    mAnchorList.add(userId);
                    runOnDelegateThread(new Runnable() {
                        @Override
                        public void run() {
                            TRTCLiveRoomDelegate delegate = mDelegate;
                            if (delegate != null) {
                                delegate.onAnchorEnter(userId);
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onRoomStreamUnavailable(final String userId) {
        TRTCLogger.i(TAG, "onRoomStreamUnavailable:" + userId);
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (isTRTCMode()) {
                    return;
                }
                if (mAnchorList.contains(userId)) {
                    mAnchorList.remove(userId);
                    runOnDelegateThread(new Runnable() {
                        @Override
                        public void run() {
                            TRTCLiveRoomDelegate delegate = mDelegate;
                            if (delegate != null) {
                                delegate.onAnchorExit(userId);
                            }
                        }
                    });
                } else {
                    TRTCLogger.e(TAG, "room anchor exit, but never throw yet, maybe something error.");
                }
            }
        });
    }

    @Override
    public void onRoomAudienceEnter(final TXUserInfo userInfo) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                if (mAudienceList.contains(userInfo.userId)) {
                    return;
                }
                TRTCLiveRoomDelegate delegate = mDelegate;
                if (delegate != null) {
                    mAudienceList.add(userInfo.userId);
                    TRTCLiveRoomDef.TRTCLiveUserInfo info = new TRTCLiveRoomDef.TRTCLiveUserInfo();
                    info.userId = userInfo.userId;
                    info.userAvatar = userInfo.avatarURL;
                    info.userName = userInfo.userName;
                    delegate.onAudienceEnter(info);
                }
            }
        });
    }

    @Override
    public void onRoomAudienceExit(final TXUserInfo userInfo) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                TRTCLiveRoomDelegate delegate = mDelegate;
                if (delegate != null) {
                    mAudienceList.remove(userInfo.userId);
                    TRTCLiveRoomDef.TRTCLiveUserInfo info = new TRTCLiveRoomDef.TRTCLiveUserInfo();
                    info.userId = userInfo.userId;
                    info.userAvatar = userInfo.avatarURL;
                    info.userName = userInfo.userName;
                    delegate.onAudienceExit(info);
                }
            }
        });
    }

    @Override
    public void onRoomRequestJoinAnchor(final TXUserInfo userInfo, final String reason, final int handleMsgTimeout) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                TRTCLiveRoomDelegate delegate = mDelegate;
                if (delegate != null) {
                    TRTCLiveRoomDef.TRTCLiveUserInfo info = new TRTCLiveRoomDef.TRTCLiveUserInfo();
                    info.userId = userInfo.userId;
                    info.userName = userInfo.userName;
                    info.userAvatar = userInfo.avatarURL;
                    delegate.onRequestJoinAnchor(info, reason, handleMsgTimeout);
                }
            }
        });
    }

    @Override
    public void onRoomKickoutJoinAnchor() {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                stopPublish(null);
                TRTCLiveRoomDelegate delegate = mDelegate;
                if (delegate != null) {
                    delegate.onKickoutJoinAnchor();
                }
            }
        });
    }

    @Override
    public void onRoomCancelJoinAnchor() {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                TRTCLiveRoomDelegate delegate = mDelegate;
                if (delegate != null) {
                    delegate.onCancelJoinAnchor();
                }
            }
        });
    }

    @Override
    public void onRoomRequestRoomPK(final TXUserInfo userInfo, final int timeout) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                TRTCLiveRoomDelegate delegate = mDelegate;
                if (delegate != null) {
                    TRTCLiveRoomDef.TRTCLiveUserInfo info = new TRTCLiveRoomDef.TRTCLiveUserInfo();
                    info.userId = userInfo.userId;
                    info.userName = userInfo.userName;
                    info.userAvatar = userInfo.avatarURL;
                    delegate.onRequestRoomPK(info, timeout);
                }
            }
        });
    }

    @Override
    public void onRoomResponseRoomPK(final String roomId, final TXUserInfo userInfo) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                mRequestPKHolder.setRealCallback(null);
                TRTCLogger.i(TAG, "recv pk repsonse, room id:" + roomId + " info:" + userInfo.toString());
                if (mCurrentRole == Role.TRTC_ANCHOR || mTargetRole == Role.TRTC_ANCHOR) {
                    TXTRTCLiveRoom.getInstance().startPK(roomId, userInfo.userId, new TXCallback() {
                        @Override
                        public void onCallback(final int code, final String msg) {
                            TRTCLogger.i(TAG, "start pk, code:" + code + " msg:" + msg);
                            if (code != 0) {
                                runOnDelegateThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        TRTCLiveRoomDelegate delegate = mDelegate;
                                        if (delegate != null) {
                                            delegate.onError(code, msg);
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onRoomCancelRoomPK() {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                TRTCLiveRoomDelegate delegate = mDelegate;
                if (delegate != null) {
                    delegate.onCancelRoomPK();
                }
            }
        });
    }

    @Override
    public void onRoomQuitRoomPk() {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                TRTCLiveRoomDelegate delegate = mDelegate;
                if (delegate != null) {
                    delegate.onQuitRoomPK();
                }
            }
        });
    }

    @Override
    public void onRoomRecvRoomTextMsg(final String roomId, final String message, final TXUserInfo userInfo) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                TRTCLiveRoomDelegate delegate = mDelegate;
                if (delegate != null) {
                    TRTCLiveRoomDef.TRTCLiveUserInfo info = new TRTCLiveRoomDef.TRTCLiveUserInfo();
                    info.userId = userInfo.userId;
                    info.userName = userInfo.userName;
                    info.userAvatar = userInfo.avatarURL;
                    delegate.onRecvRoomTextMsg(message, info);
                }
            }
        });
    }

    @Override
    public void onRoomRecvRoomCustomMsg(final String roomId, final String cmd,
                                        final String message, final TXUserInfo userInfo) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                TRTCLiveRoomDelegate delegate = mDelegate;
                if (delegate != null) {
                    TRTCLiveRoomDef.TRTCLiveUserInfo info = new TRTCLiveRoomDef.TRTCLiveUserInfo();
                    info.userId = userInfo.userId;
                    info.userName = userInfo.userName;
                    info.userAvatar = userInfo.avatarURL;
                    delegate.onRecvRoomCustomMsg(cmd, message, info);
                }
            }
        });
    }

    @Override
    public void onAudienceRequestJoinAnchorTimeout(final String userId) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                TRTCLiveRoomDelegate delegate = mDelegate;
                if (delegate != null) {
                    delegate.onAudienceRequestJoinAnchorTimeout(userId);
                }
            }
        });
    }

    @Override
    public void onAnchorRequestRoomPKTimeout(final String userId) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                TRTCLiveRoomDelegate delegate = mDelegate;
                if (delegate != null) {
                    delegate.onAnchorRequestRoomPKTimeout(userId);
                }
            }
        });
    }
}
