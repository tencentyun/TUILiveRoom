package com.tencent.liteav.liveroom.model.impl.av.trtc;

import static com.tencent.liteav.TXLiteAVCode.ERR_TRTC_USER_SIG_CHECK_FAILED;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.tencent.liteav.audio.TXAudioEffectManager;
import com.tencent.liteav.beauty.TXBeautyManager;
import com.tencent.liteav.liveroom.model.impl.base.TRTCLogger;
import com.tencent.liteav.liveroom.model.impl.base.TXCallback;
import com.tencent.qcloud.tuicore.TUIConstants;
import com.tencent.qcloud.tuicore.TUICore;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.trtc.TRTCCloud;
import com.tencent.trtc.TRTCCloudDef;
import com.tencent.trtc.TRTCCloudListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TXTRTCLiveRoom extends TRTCCloudListener implements ITRTCTXLiveRoom {
    private static final String TAG                    = "TXTRTCLiveRoom";
    private static final long   PLAY_TIME_OUT          = 5000;
    private static final int    KTC_COMPONENT_LIVEROOM = 4;

    private static TXTRTCLiveRoom sInstance;

    private TRTCCloud               mTRTCCloud;
    private TXBeautyManager         mTXBeautyManager;
    private int                     mOriginRole;
    private TXCallback              mEnterRoomCallback;
    private TXCallback              mExitRoomCallback;
    private TXCallback              mPKCallback;
    private boolean                 mIsInRoom;
    private ITXTRTCLiveRoomDelegate mDelegate;
    private String                  mUserId;
    private String                  mRoomId;
    private TRTCCloudDef.TRTCParams mTRTCParams;
    private Map<String, TXCallback> mPlayCallbackMap;
    private Map<String, Runnable>   mPlayTimeoutRunnable;
    private Handler                 mMainHandler;

    private final TRTCCloudDef.TRTCVideoEncParam mVideoEncParam = new TRTCCloudDef.TRTCVideoEncParam();

    public static synchronized TXTRTCLiveRoom getInstance() {
        if (sInstance == null) {
            sInstance = new TXTRTCLiveRoom();
        }
        return sInstance;
    }

    @Override
    public void init(Context context) {
        mTRTCCloud = TRTCCloud.sharedInstance(context);
        TRTCLogger.i(TAG, "init context:" + context);
        mTXBeautyManager = mTRTCCloud.getBeautyManager();
        mPlayCallbackMap = new HashMap<>();
        mPlayTimeoutRunnable = new HashMap<>();
        mMainHandler = new Handler(Looper.getMainLooper());
        mVideoEncParam.enableAdjustRes = true;
        mVideoEncParam.videoResolutionMode = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_PORTRAIT;
    }

    @Override
    public void setDelegate(ITXTRTCLiveRoomDelegate delegate) {
        TRTCLogger.i(TAG, "init delegate:" + delegate);
        mDelegate = delegate;
    }

    @Override
    public void enterRoom(int sdkAppId, String strRoomId,  String userId, String userSign, int role,
                          TXCallback callback) {
        if (sdkAppId == 0 || TextUtils.isEmpty(userId) || TextUtils.isEmpty(userSign)) {
            TRTCLogger.e(TAG, "enter trtc room fail. params invalid. room id:"
                    + strRoomId + " user id:" + userId + " sign is empty:" + TextUtils.isEmpty(userSign));
            if (callback != null) {
                callback.onCallback(-1,
                        "enter trtc room fail. params invalid. room id:" + strRoomId + " user id:"
                                + userId + " sign is empty:" + TextUtils.isEmpty(userSign));
            }
            return;
        }
        mUserId = userId;
        mRoomId = strRoomId;
        mOriginRole = role;
        TRTCLogger.i(TAG, "enter room, app id:" + sdkAppId + " room id:"
                + strRoomId + " user id:" + userId + " sign:"
                + TextUtils.isEmpty(userId) + " role:" + role);
        mEnterRoomCallback = callback;
        mTRTCParams = new TRTCCloudDef.TRTCParams();
        mTRTCParams.sdkAppId = sdkAppId;
        mTRTCParams.userId = userId;
        mTRTCParams.userSig = userSign;
        mTRTCParams.role = role;
        mTRTCParams.strRoomId = strRoomId;
        internalEnterRoom();
    }

    private void setFramework() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("api", "setFramework");
            JSONObject params = new JSONObject();
            params.put("framework", 1);
            params.put("component", KTC_COMPONENT_LIVEROOM);
            jsonObject.put("params", params);
            mTRTCCloud.callExperimentalAPI(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void internalEnterRoom() {
        // Set the listener before room entry; otherwise, room entry may be interrupted by other information
        if (mTRTCParams == null) {
            return;
        }
        setFramework();
        mTRTCCloud.setListener(this);
        mTRTCCloud.enterRoom(mTRTCParams, TRTCCloudDef.TRTC_APP_SCENE_LIVE);
    }

    @Override
    public void onFirstVideoFrame(String userId, int streamType, int width, int height) {
        TRTCLogger.i(TAG, "onFirstVideoFrame:" + userId);
        if (userId == null) {
            // `userId` is `null`, indicating that the captured local camera image starts to be rendered
        } else {
            stopTimeoutRunnable(userId);
            TXCallback callback = mPlayCallbackMap.remove(userId);
            if (callback != null) {
                callback.onCallback(0, userId + "播放成功");
            }
        }
    }

    @Override
    public void exitRoom(TXCallback callback) {
        TRTCLogger.i(TAG, "exit room.");
        mUserId = null;
        mTRTCParams = null;
        mExitRoomCallback = callback;
        mPlayCallbackMap.clear();
        mPlayTimeoutRunnable.clear();
        mMainHandler.removeCallbacksAndMessages(null);
        mTRTCCloud.exitRoom();
        mEnterRoomCallback = null;
    }

    @Override
    public void startPublish(String streamId, TXCallback callback) {
        TRTCLogger.i(TAG, "start publish. stream id:" + streamId);
        if (!isEnterRoom()) {
            // The user hasn't entered a room. Enter a room first before pushing a stream
            TRTCLogger.i(TAG, "not enter room yet, enter trtc room.");
            internalEnterRoom();
        }
        // If the user is an audience member, switch to anchor
        if (mOriginRole == TRTCCloudDef.TRTCRoleAudience) {
            mTRTCCloud.switchRole(TRTCCloudDef.TRTCRoleAnchor);
            // An audience member can be switched to a secondary anchor. Set the resolution for the secondary anchor
            TRTCCloudDef.TRTCVideoEncParam param = new TRTCCloudDef.TRTCVideoEncParam();
            param.videoResolution = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_480_270;
            param.videoBitrate = 400;
            param.videoFps = 15;
            param.videoResolutionMode = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_PORTRAIT;
            mTRTCCloud.setVideoEncoderParam(param);
        } else if (mOriginRole == TRTCCloudDef.TRTCRoleAnchor) {
            // An audience member can be switched to a secondary anchor. Set the resolution for the secondary anchor
            TRTCCloudDef.TRTCVideoEncParam param = new TRTCCloudDef.TRTCVideoEncParam();
            param.videoResolution = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_1280_720;
            param.videoBitrate = 1800;
            param.videoFps = 15;
            param.enableAdjustRes = true;
            param.videoResolutionMode = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_PORTRAIT;
            mTRTCCloud.setVideoEncoderParam(param);
        }
        ;
        mTRTCCloud.startPublishing(streamId, TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);
        mTRTCCloud.startLocalAudio();
        if (callback != null) {
            callback.onCallback(0, "start publish success.");
        }
    }

    @Override
    public void stopPublish(TXCallback callback) {
        TRTCLogger.i(TAG, "stop publish.");
        mTRTCCloud.stopPublishing();
        mTRTCCloud.stopLocalAudio();
        if (mOriginRole == TRTCCloudDef.TRTCRoleAudience) {
            mTRTCCloud.switchRole(TRTCCloudDef.TRTCRoleAudience);
        } else if (mOriginRole == TRTCCloudDef.TRTCRoleAnchor) {
            mTRTCCloud.exitRoom();
        }
        if (callback != null) {
            callback.onCallback(0, "stop publish success.");
        }
    }

    @Override
    public void startCameraPreview(boolean isFront, TXCloudVideoView view, TXCallback callback) {
        TRTCLogger.i(TAG, "start camera preview.");
        mTRTCCloud.startLocalPreview(isFront, view);
        mTRTCCloud.setLocalVideoProcessListener(TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_Texture_2D,
                TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_TEXTURE,
                new TRTCVideoFrameListener() {
                    @Override
                    public void onGLContextCreated() {

                    }

                    @Override
                    public int onProcessVideoFrame(TRTCCloudDef.TRTCVideoFrame trtcVideoFrame,
                                                   TRTCCloudDef.TRTCVideoFrame trtcVideoFrame1) {
                        Map<String, Object> map = new HashMap<>();
                        map.put(TUIConstants.TUIBeauty.PARAM_NAME_SRC_TEXTURE_ID, trtcVideoFrame.texture.textureId);
                        map.put(TUIConstants.TUIBeauty.PARAM_NAME_FRAME_WIDTH, trtcVideoFrame.width);
                        map.put(TUIConstants.TUIBeauty.PARAM_NAME_FRAME_HEIGHT, trtcVideoFrame.height);
                        if (TUICore.callService(TUIConstants.TUIBeauty.SERVICE_NAME,
                                TUIConstants.TUIBeauty.METHOD_PROCESS_VIDEO_FRAME, map) != null) {
                            trtcVideoFrame1.texture.textureId = (int) TUICore
                                    .callService(TUIConstants.TUIBeauty.SERVICE_NAME,
                                            TUIConstants.TUIBeauty.METHOD_PROCESS_VIDEO_FRAME, map);
                        } else {
                            trtcVideoFrame1.texture.textureId = trtcVideoFrame.texture.textureId;
                        }
                        return 0;
                    }

                    @Override
                    public void onGLContextDestory() {
                        TUICore.callService(TUIConstants.TUIBeauty.SERVICE_NAME,
                                TUIConstants.TUIBeauty.METHOD_DESTROY_XMAGIC, null);
                    }
                });
        if (callback != null) {
            callback.onCallback(0, "success");
        }
    }

    @Override
    public void stopCameraPreview() {
        TRTCLogger.i(TAG, "stop camera preview.");
        mTRTCCloud.stopLocalPreview();
    }

    @Override
    public void switchCamera() {
        mTRTCCloud.switchCamera();
    }

    @Override
    public void setMirror(boolean isMirror) {
        TRTCLogger.i(TAG, "mirror:" + isMirror);
        if (isMirror) {
            mTRTCCloud.setLocalViewMirror(TRTCCloudDef.TRTC_VIDEO_MIRROR_TYPE_ENABLE);
        } else {
            mTRTCCloud.setLocalViewMirror(TRTCCloudDef.TRTC_VIDEO_MIRROR_TYPE_DISABLE);
        }
    }

    @Override
    public void muteLocalAudio(boolean mute) {
        TRTCLogger.i(TAG, "mute local audio, mute:" + mute);
        mTRTCCloud.muteLocalAudio(mute);
    }

    @Override
    public void muteRemoteAudio(String userId, boolean mute) {
        TRTCLogger.i(TAG, "mute remote audio, user id:" + userId + " mute:" + mute);
        mTRTCCloud.muteRemoteAudio(userId, mute);
    }

    @Override
    public void muteAllRemoteAudio(boolean mute) {
        TRTCLogger.i(TAG, "mute all remote audio, mute:" + mute);
        mTRTCCloud.muteAllRemoteAudio(mute);
    }

    @Override
    public boolean isEnterRoom() {
        return mIsInRoom;
    }

    @Override
    public void setMixConfig(List<TXTRTCMixUser> list, boolean isPK) {
        if (list == null) {
            mTRTCCloud.setMixTranscodingConfig(null);
        } else {
            // 背景大画面宽高
            int videoWidth = 720;
            int videoHeight = 1280;

            // 小画面宽高
            int subWidth = 180;
            int subHeight = 320;

            int offsetX = 5;
            int offsetY = 50;

            int bitrate = 200;

            int resolution = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_960_540;
            switch (resolution) {
                case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_160_160: {
                    videoWidth = 160;
                    videoHeight = 160;
                    subWidth = 32;
                    subHeight = 48;
                    offsetY = 10;
                    bitrate = 200;
                    break;
                }
                case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_320_180: {
                    videoWidth = 192;
                    videoHeight = 336;
                    subWidth = 54;
                    subHeight = 96;
                    offsetY = 30;
                    bitrate = 400;
                    break;
                }
                case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_320_240: {
                    videoWidth = 240;
                    videoHeight = 320;
                    subWidth = 54;
                    subHeight = 96;
                    offsetY = 30;
                    bitrate = 400;
                    break;
                }
                case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_480_480: {
                    videoWidth = 480;
                    videoHeight = 480;
                    subWidth = 72;
                    subHeight = 128;
                    bitrate = 600;
                    break;
                }
                case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_640_360: {
                    videoWidth = 368;
                    videoHeight = 640;
                    subWidth = 90;
                    subHeight = 160;
                    bitrate = 800;
                    break;
                }
                case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_640_480: {
                    videoWidth = 480;
                    videoHeight = 640;
                    subWidth = 90;
                    subHeight = 160;
                    bitrate = 800;
                    break;
                }
                case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_960_540: {
                    videoWidth = 544;
                    videoHeight = 960;
                    subWidth = 160;
                    subHeight = 288;
                    bitrate = 1000;
                    break;
                }
                case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_1280_720: {
                    videoWidth = 720;
                    videoHeight = 1280;
                    subWidth = 192;
                    subHeight = 336;
                    bitrate = 1500;
                    break;
                }
                default:
                    break;
            }

            TRTCCloudDef.TRTCTranscodingConfig config = new TRTCCloudDef.TRTCTranscodingConfig();
            config.videoWidth = videoWidth;
            config.videoHeight = videoHeight;
            config.videoGOP = 1;
            config.videoFramerate = 15;
            config.videoBitrate = bitrate;
            config.audioSampleRate = 48000;
            config.audioBitrate = 64;
            config.audioChannels = 1;

            // Set the anchor image position in the mixed image
            TRTCCloudDef.TRTCMixUser mixUser = new TRTCCloudDef.TRTCMixUser();
            mixUser.userId = mUserId;
            mixUser.roomId = mRoomId;
            mixUser.zOrder = 1;
            mixUser.x = 0;
            mixUser.y = 0;
            mixUser.width = videoWidth;
            mixUser.height = videoHeight;

            config.mixUsers = new ArrayList<>();
            config.mixUsers.add(mixUser);

            // Set the small image positions in the mixed image+
            int index = 0;
            for (TXTRTCMixUser txtrtcMixUser : list) {
                TRTCCloudDef.TRTCMixUser mixUserTemp = new TRTCCloudDef.TRTCMixUser();
                mixUserTemp.userId = txtrtcMixUser.userId;
                mixUserTemp.roomId = txtrtcMixUser.roomId == null ? mRoomId : txtrtcMixUser.roomId;
                mixUserTemp.streamType = TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG;
                mixUserTemp.zOrder = 2 + index;
                if (index < 3) {
                    // The first three small images are displayed from bottom to top on the right
                    mixUserTemp.x = videoWidth - offsetX - subWidth;
                    mixUserTemp.y = videoHeight - offsetY - index * subHeight - subHeight;
                    mixUserTemp.width = subWidth;
                    mixUserTemp.height = subHeight;
                } else if (index < 6) {
                    // The last three small images are displayed from bottom to top on the left
                    mixUserTemp.x = offsetX;
                    mixUserTemp.y = videoHeight - offsetY - (index - 3) * subHeight - subHeight;
                    mixUserTemp.width = subWidth;
                    mixUserTemp.height = subHeight;
                } else {
                    // Up to six small images can be added
                }
                config.mixUsers.add(mixUserTemp);
                ++index;
            }
            mTRTCCloud.setMixTranscodingConfig(config);
        }
    }

    @Override
    public void showVideoDebugLog(boolean isShow) {
        if (isShow) {
            mTRTCCloud.showDebugView(2);
        } else {
            mTRTCCloud.showDebugView(0);
        }
    }

    @Override
    public void startPK(String roomId, String userId, TXCallback callback) {
        TRTCLogger.i(TAG, "start pk, room id:" + roomId + " user id:" + userId);
        mPKCallback = callback;
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("roomId", Integer.valueOf(roomId));
            jsonObject.put("userId", userId);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        mTRTCCloud.ConnectOtherRoom(jsonObject.toString());
    }

    @Override
    public void stopPK() {
        TRTCLogger.i(TAG, "stop pk.");
        mTRTCCloud.DisconnectOtherRoom();
    }


    @Override
    public void startPlay(final String userId, TXCloudVideoView view, TXCallback callback) {
        TRTCLogger.i(TAG, "start play user id:" + userId + " view:" + view);
        mPlayCallbackMap.put(userId, callback);
        mTRTCCloud.startRemoteView(userId, view);
        // Stop the last timeout
        stopTimeoutRunnable(userId);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                TRTCLogger.e(TAG, "start play timeout:" + userId);
                TXCallback callback = mPlayCallbackMap.remove(userId);
                if (callback != null) {
                    callback.onCallback(-1, "play " + userId + " timeout.");
                }
            }
        };
        mPlayTimeoutRunnable.put(userId, runnable);
        mMainHandler.postDelayed(runnable, PLAY_TIME_OUT);

    }

    private void stopTimeoutRunnable(String userId) {
        if (mPlayTimeoutRunnable == null) {
            return;
        }
        Runnable runnable = mPlayTimeoutRunnable.get(userId);
        mMainHandler.removeCallbacks(runnable);
    }

    @Override
    public void stopPlay(String userId, TXCallback callback) {
        TRTCLogger.i(TAG, "stop play user id:" + userId);
        mPlayCallbackMap.remove(userId);
        stopTimeoutRunnable(userId);
        mTRTCCloud.stopRemoteView(userId);
        if (callback != null) {
            callback.onCallback(0, "stop play success.");
        }
    }

    @Override
    public void stopAllPlay() {
        TRTCLogger.i(TAG, "stop all play");
        mTRTCCloud.stopAllRemoteView();
    }

    @Override
    public void onEnterRoom(long l) {
        TRTCLogger.i(TAG, "on enter room, result:" + l);
        if (mEnterRoomCallback != null) {
            if (l > 0) {
                mIsInRoom = true;
                mEnterRoomCallback.onCallback(0, "enter room success.");
                mEnterRoomCallback = null;
            } else {
                mIsInRoom = false;
                mEnterRoomCallback.onCallback((int) l, l == ERR_TRTC_USER_SIG_CHECK_FAILED
                        ? "userSig invalid, please login again" : "enter room fail");
                mEnterRoomCallback = null;
            }
        }
    }

    @Override
    public void onExitRoom(int i) {
        TRTCLogger.i(TAG, "on exit room.");
        if (mExitRoomCallback != null) {
            mIsInRoom = false;
            mExitRoomCallback.onCallback(0, "exit room success.");
            mExitRoomCallback = null;
        }
    }

    @Override
    public void onConnectOtherRoom(String s, int i, String s1) {
        TRTCLogger.i(TAG, "on connect other room, code:" + i + " msg:" + s1);
        if (mPKCallback != null) {
            if (i == 0) {
                mPKCallback.onCallback(0, "connect other room success.");
            } else {
                mPKCallback.onCallback(i, "connect other room fail. msg:" + s1);
            }
        }
    }

    @Override
    public void onRemoteUserEnterRoom(String userId) {
        TRTCLogger.i(TAG, "on user enter, user id:" + userId);
        if (mDelegate != null) {
            mDelegate.onTRTCAnchorEnter(userId);
        }
    }

    @Override
    public void onRemoteUserLeaveRoom(String userId, int i) {
        TRTCLogger.i(TAG, "on user exit, user id:" + userId);
        if (mDelegate != null) {
            mDelegate.onTRTCAnchorExit(userId);
        }
    }

    @Override
    public void onUserVideoAvailable(String userId, boolean available) {
        TRTCLogger.i(TAG, "on user available, user id:" + userId + " available:" + available);
        if (mDelegate != null) {
            if (available) {
                mDelegate.onTRTCStreamAvailable(userId);
            } else {
                mDelegate.onTRTCStreamUnavailable(userId);
            }
        }
    }

    @Override
    public void onSetMixTranscodingConfig(int i, String s) {
        super.onSetMixTranscodingConfig(i, s);
        TRTCLogger.i(TAG, "on set mix transcoding, code:" + i + " msg:" + s);
    }

    @Override
    public TXBeautyManager getTXBeautyManager() {
        return mTXBeautyManager;
    }

    @Override
    public TXAudioEffectManager getAudioEffectManager() {
        return mTRTCCloud.getAudioEffectManager();
    }

    @Override
    public void setAudioQuality(int quality) {
        mTRTCCloud.setAudioQuality(quality);
    }

    @Override
    public void setVideoResolution(int resolution) {
        mVideoEncParam.videoResolution = resolution;
        mTRTCCloud.setVideoEncoderParam(mVideoEncParam);
        TRTCLogger.i(TAG, "setVideoResolution:" + resolution);
    }

    @Override
    public void setVideoFps(int fps) {
        mVideoEncParam.videoFps = fps;
        mTRTCCloud.setVideoEncoderParam(mVideoEncParam);
        TRTCLogger.i(TAG, "setVideoFps:" + fps);
    }

    @Override
    public void setVideoBitrate(int bitrate) {
        mVideoEncParam.videoBitrate = bitrate;
        mTRTCCloud.setVideoEncoderParam(mVideoEncParam);
        TRTCLogger.i(TAG, "setVideoBitrate:" + bitrate);
    }
}
