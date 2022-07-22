package com.tencent.liteav.liveroom.model;

import android.content.Context;
import android.os.Handler;

import com.tencent.liteav.audio.TXAudioEffectManager;
import com.tencent.liteav.beauty.TXBeautyManager;
import com.tencent.liteav.liveroom.model.impl.TRTCLiveRoomImpl;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.util.List;

public abstract class TRTCLiveRoom {
    protected TRTCLiveRoom() {
    }

    /**
     * Gets a `TRTCLiveRoom` singleton object
     *
     * @param context Android context, which will be internally converted to `ApplicationContext` for system API calls
     * @return `TRTCLiveRoom` instance
     * @note To terminate a singleton object, call `{@link TRTCLiveRoom#destroySharedInstance()}`.
     */
    public static synchronized TRTCLiveRoom sharedInstance(Context context) {
        return TRTCLiveRoomImpl.sharedInstance(context);
    }

    /**
     * Terminates a `TRTCLiveRoom` singleton object
     *
     * @note After the instance is terminated, the externally cached `TRTCLiveRoom` instance can no longer be used.
     * You need to call `{@link TRTCLiveRoom#sharedInstance(Context)}` again to get a new instance.
     */
    public static void destroySharedInstance() {
        TRTCLiveRoomImpl.destroySharedInstance();
    }

    /**
     * Set the event callbacks of the component
     * <p>
     * You can use `TRTCLiveRoomDelegate` to get different status notifications of `TRTCLiveRoom`.
     *
     * @param delegate Callback API
     * @note Callbacks from `TRTCLiveRoomDelegate` are sent to you in the main thread by default.
     * If you need to specify a thread for event callbacks,
     * use `{@link TRTCLiveRoom#setDelegateHandler(Handler)}`.
     */
    public abstract void setDelegate(TRTCLiveRoomDelegate delegate);

    /**
     * Sets the thread where the event callback is
     *
     * @param handler Handler thread. Callbacks for various statuses of `TRTCLiveRoom` are
     *                returned via this handler. Do not use it together with `setDelegate`.
     */
    public abstract void setDelegateHandler(Handler handler);


    /**
     * @param sdkAppId You can view `SDKAppID` in **[Application Management](https://console.cloud.tencent.com/trtc/app)** > **Application Info** in the TRTC console.
     * @param userId   ID of the current user, which is a string that can contain only
     *                 letters (a–z and A–Z), digits (0–9), hyphens (-), and underscores (_)
     * @param userSig  Tencent Cloud's proprietary security protection signature. For more information on how to get it, see [UserSig](https://cloud.tencent.com/document/product/647/17275).
     * @param config   Global configuration information.
     *                 Initialize it during login as it cannot be modified after login.
     * @param callback Callback for login. The return code will be `0` if login is successful.
     */
    public abstract void login(int sdkAppId, String userId, String userSig,
                               TRTCLiveRoomDef.TRTCLiveRoomConfig config, TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Logs out
     */
    public abstract void logout(TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Set user information. The user information you set will be stored in Tencent Cloud IM.
     *
     * @param userName  Username
     * @param avatarURL User profile photo
     * @param callback  Result callback for whether the setting succeeds
     */
    public abstract void setSelfProfile(String userName, String avatarURL,
                                        TRTCLiveRoomCallback.ActionCallback callback);


    /**
     * Creates a room (called by anchor)
     * <p>
     *
     * @param roomId    Room ID. You need to assign and manage the IDs in a centralized manner.
     *                  Multiple `roomId` values can be aggregated into a live room list. Currently,
     *                  Tencent Cloud does not provide list management services.
     *                  You need to manage the list on your own.
     * @param roomParam Room information, such as room name and cover information.
     *                  If both the room list and room information are managed on your server,
     *                  you can ignore this parameter.
     * @param callback  Callback for room creation result. The `code` will be 0 if the operation succeeds.
     */
    public abstract void createRoom(String roomId, TRTCLiveRoomDef.TRTCCreateRoomParam roomParam,
                                    TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Terminates a room (called by anchor)
     * <p>
     * After creating a room, the anchor can call this API to terminate it.
     */
    public abstract void destroyRoom(TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Enters a room (called by audience)
     * <p>
     *
     * @param roomId   Room ID
     * @param callback Result callback for whether room entry succeeds
     */
    public abstract void enterRoom(String roomId, TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Exits a room
     *
     * @param callback Result callback for whether room exit succeeds
     */
    public abstract void exitRoom(TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Get room list details
     * <p>
     * The details are set through `roomInfo` by the anchor during room creation (`createRoom()`).
     * If both the room list and room information are managed on your server, you can ignore this function.
     *
     * @param roomIdList Room ID list
     * @param callback   Callback for room details
     * @see {@link TRTCLiveRoomDef.TRTCLiveRoomInfo}
     */
    public abstract void getRoomInfos(List<String> roomIdList, TRTCLiveRoomCallback.RoomInfoCallback callback);

    /**
     * Gets the anchor and co-anchors in a room. This API works only if it is called after `enterRoom()`.
     *
     * @param callback Callback for user details
     */
    public abstract void getAnchorList(TRTCLiveRoomCallback.UserListCallback callback);

    /**
     * Gets the information of all audience members in a room.
     * This API takes effect only if it is called after `enterRoom()`.
     *
     * @param callback Callback for user details
     */
    public abstract void getAudienceList(TRTCLiveRoomCallback.UserListCallback callback);

    /**
     * Enables local video preview
     *
     * @param isFront  true: Front camera; false: Rear camera
     * @param view     Control that carries the video image
     * @param callback Callback for the operation
     */
    public abstract void startCameraPreview(boolean isFront,
                                            TXCloudVideoView view, TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Stops local video capturing and preview
     */
    public abstract void stopCameraPreview();

    /**
     * Starts live streaming (pushing streams). This API can be called in the following scenarios:
     * - The anchor starts live streaming.
     * - An audience member starts co-anchoring.
     *
     * @param streamId It is used to bind live streaming CDNs.
     *                 You need to specify the `streamId` of the anchor if you want the audience
     *                 to play back the anchor's streams via live streaming CDNs.
     * @param callback Callback for the operation
     */
    public abstract void startPublish(String streamId, TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Stops live streaming (pushing streams). This API can be called in the following scenarios:
     * - The anchor ends live streaming.
     * - An audience member ends co-anchoring.
     *
     * @param callback Callback for the operation
     */
    public abstract void stopPublish(TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Plays a remote video. This API can be called in common playback and co-anchoring scenarios.
     *
     * @param userId   ID of the user whose video is to be played
     * @param view     The control that carries the video image
     * @param callback Callback for the operation
     */
    public abstract void startPlay(String userId, TXCloudVideoView view, TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Stops playing back the remote video image
     *
     * @param userId   Remote user ID
     * @param callback Callback for the operation
     * @note Call this API after receiving the `onAnchorExit` callback
     */
    public abstract void stopPlay(String userId, TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Sends a co-anchoring request (called by audience)
     * <p>
     *
     * @param reason   Reason for co-anchoring
     * @param timeout  Timeout period in seconds
     * @param callback Callback for co-anchoring request
     * @see {@link TRTCLiveRoomDelegate#onRequestJoinAnchor}
     */
    public abstract void requestJoinAnchor(String reason, int timeout, TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Responds to a co-anchoring request (called by the anchor)
     * <p>
     * The anchor needs to call this API to respond to the co-anchoring request f
     * rom an audience member after receiving the `{@link TRTCLiveRoomDelegate#onRequestJoinAnchor}` callback.
     *
     * @param userId Audience member ID
     * @param agree  true: Accept; false: Reject
     * @param reason Reason for accepting/rejecting co-anchoring
     */
    public abstract void responseJoinAnchor(String userId, boolean agree, String reason);

    /**
     * Removes an audience member from co-anchoring
     * <p>
     * After the anchor calls this API to remove an audience member from co-anchoring,
     * the removed user will receive the `{@link TRTCLiveRoomDelegate#onKickoutJoinAnchor()}` callback.
     *
     * @see {@link TRTCLiveRoomDelegate#onKickoutJoinAnchor()}
     */
    public abstract void kickoutJoinAnchor(String userId, TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Cancels a co-anchoring request (called by audience)
     * <p>
     * After an audience member cancels the co-anchoring request,
     * the anchor will receive the `{@link TRTCLiveRoomDelegate#onCancelJoinAnchor()}` callback
     *
     * @param reason   Reason for co-anchoring
     * @param callback Callback of the response
     */
    public abstract void cancelRequestJoinAnchor(String reason, TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Requests cross-room communication
     *
     * @param roomId   Room ID of the anchor to call
     * @param userId   User ID of the anchor to call
     * @param timeout  Timeout period in seconds
     * @param callback Callback for cross-room communication request result
     * @see {@link TRTCLiveRoomDelegate#onRequestRoomPK}
     */
    public abstract void requestRoomPK(int roomId, String userId,
                                       int timeout, TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Responds to a cross-room communication request
     * <p>
     * The anchor responds to a communication request from another anchor
     *
     * @param userId User ID of the anchor sending the communication request
     * @param agree  true: Accept; false: Reject
     * @param reason Reason for accepting/rejecting the communication
     */
    public abstract void responseRoomPK(String userId, boolean agree, String reason);

    /**
     * Cancels the request for cross-room communication
     *
     * @param userId   User ID of the anchor to invite
     * @param callback Callback of the response
     */
    public abstract void cancelRequestRoomPK(String userId, TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Exits cross-room communication
     * <p>
     * If either anchor exits cross-room communication,
     * the other anchor will receive the `{@link TRTCLiveRoomDelegate#onQuitRoomPK}` callback.
     *
     * @param callback Callback for exiting cross-room communication
     */
    public abstract void quitRoomPK(TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Switches between the front and rear cameras
     */
    public abstract void switchCamera();

    /**
     * Specifies whether to mirror the video.
     */
    public abstract void setMirror(boolean isMirror);

    /**
     * Mutes/UnMutes the local user
     */
    public abstract void muteLocalAudio(boolean mute);

    /**
     * Mutes/UnMutes a remote user
     */
    public abstract void muteRemoteAudio(String userId, boolean mute);

    /**
     * Mutes/UnMutes all remote users
     */
    public abstract void muteAllRemoteAudio(boolean mute);

    /**
     * Sound effect control APIs
     */
    public abstract TXAudioEffectManager getAudioEffectManager();

    /**
     * Sets the audio quality
     *
     * @param quality TRTC_AUDIO_QUALITY_MUSIC/TRTC_AUDIO_QUALITY_DEFAULT/TRTC_AUDIO_QUALITY_SPEECH
     */
    public abstract void setAudioQuality(int quality);

    /**
     * Beauty filter control APIs
     */
    public abstract TXBeautyManager getBeautyManager();


    /**
     * Broadcasts a chat message in the room. This API is generally used for on-screen comments.
     *
     * @param message  Text chat message
     * @param callback Callback for the sending result
     */
    public abstract void sendRoomTextMsg(String message, TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Broadcast a custom (command) message in the room,
     * which is generally used to broadcast liking and gifting messages
     *
     * @param cmd      Custom command word used to distinguish between different message types
     * @param message  Text chat message
     * @param callback Callback for the sending result
     */
    public abstract void sendRoomCustomMsg(String cmd, String message, TRTCLiveRoomCallback.ActionCallback callback);

    /**
     * Specifies whether to display debugging information on the UI
     */
    public abstract void showVideoDebugLog(boolean isShow);

    /**
     * Sets the resolution
     *
     * @param resolution For the specific settings, see `TRTCCloudDef.TRTC_VIDEO_RESOLUTION_xx`.
     */
    public abstract void setVideoResolution(int resolution);

    /**
     * Sets the frame rate
     *
     * @param fps fps
     */
    public abstract void setVideoFps(int fps);

    /**
     * Sets the bitrate
     *
     * @param bitrate Bitrate
     */
    public abstract void setVideoBitrate(int bitrate);

}