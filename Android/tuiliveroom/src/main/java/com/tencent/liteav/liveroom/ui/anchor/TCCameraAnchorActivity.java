package com.tencent.liteav.liveroom.ui.anchor;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.tencent.liteav.debug.GenerateGlobalConfig.XMAGIC_LICENSE_KEY;
import static com.tencent.liteav.debug.GenerateGlobalConfig.XMAGIC_LICENSE_URL;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.constraintlayout.widget.Guideline;

import com.blankj.utilcode.util.ToastUtils;
import com.tencent.liteav.basic.RTCubeUtils;
import com.tencent.liteav.basic.UserModel;
import com.tencent.liteav.basic.UserModelManager;
import com.tencent.liteav.liveroom.R;
import com.tencent.liteav.liveroom.model.LiveRoomManager;
import com.tencent.liteav.liveroom.model.TRTCLiveRoom;
import com.tencent.liteav.liveroom.model.TRTCLiveRoomCallback;
import com.tencent.liteav.liveroom.model.TRTCLiveRoomDef;
import com.tencent.liteav.liveroom.model.TRTCLiveRoomDelegate;
import com.tencent.liteav.liveroom.model.impl.base.TRTCLogger;
import com.tencent.liteav.liveroom.ui.common.utils.PermissionHelper;
import com.tencent.liteav.liveroom.ui.common.utils.TCUtils;
import com.tencent.liteav.liveroom.ui.widget.video.TCVideoView;
import com.tencent.liteav.liveroom.ui.widget.video.TCVideoViewMgr;
import com.tencent.qcloud.tuicore.TUIConstants;
import com.tencent.qcloud.tuicore.TUICore;
import com.tencent.qcloud.tuicore.TUILogin;
import com.tencent.qcloud.tuicore.interfaces.TUILoginListener;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Module:   TCCameraAnchorActivity
 * <p>
 * Function: Anchor stream push page
 * </p>
 */
public class TCCameraAnchorActivity extends Activity implements TRTCLiveRoomDelegate {
    private static final String TAG = "TCCameraAnchorActivity";


    protected static final String LIVE_TOTAL_TIME      = "live_total_time";
    protected static final String ANCHOR_HEART_COUNT   = "anchor_heart_count";
    protected static final String TOTAL_AUDIENCE_COUNT = "total_audience_count";
    public static final    int    ERROR_ROOM_ID_EXIT   = -1301;
    private static final   int    MAX_LEN              = 30;

    protected String  mCoverPicUrl;
    protected String  mSelfAvatar;
    protected String  mSelfName;
    protected String  mSelfUserId;
    protected String  mRoomName;
    protected String  mRoomId;
    protected long    mTotalMemberCount   = 0;
    protected long    mCurrentMemberCount = 0;
    protected long    mHeartCount         = 0;
    protected boolean mIsEnterRoom        = false;
    protected boolean mIsCreatingRoom     = false;

    protected TRTCLiveRoom mLiveRoom;

    private EditText mEditLiveRoomName;

    private   View             mToolbar;
    protected ConstraintLayout mRootView;
    protected Handler          mMainHandler = new Handler(Looper.getMainLooper());

    private   Timer              mBroadcastTimer;
    private   BroadcastTimerTask mBroadcastTimerTask;
    protected long               mSecond = 0;
    private   AlertDialog        mErrorDialog;

    private AnchorPreFunctionView mPreFunctionView;
    private AnchorPreView         mPreView;
    private AnchorFunctionView    mFunctionView;


    private ImageView mImagesAnchorHead;

    private Guideline             mGuideLineVertical;
    private RelativeLayout        mPKContainer;
    private ImageView             mImagePKLayer;
    private TCVideoViewMgr        mVideoViewMgr;
    private AnchorCountDownView   mCountDownView;
    private TXCloudVideoView      mTXCloudVideoView;
    private TXCloudVideoView      mVideoViewPKAnchor;
    private ConfirmDialogFragment mPKConfirmDialogFragment;

    private boolean      mShowLog;
    private boolean      mIsPaused         = false;
    private List<String> mAnchorUserIdList = new ArrayList<>();
    private int          mCurrentStatus    = TRTCLiveRoomDef.ROOM_STATUS_NONE;


    private final Map<String, ConfirmDialogFragment> mLinkMicConfirmDialogFragmentMap = new HashMap<>();


    private final ConcurrentMap<String, TRTCLiveRoomDef.TRTCLiveUserInfo> mUserInfoMap = new ConcurrentHashMap<>();

    private static final int LINK_MIC_MEMBER_MAX = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UserModelManager.getInstance().getUserModel().userType = UserModel.UserType.LIVE_ROOM;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.trtcliveroom_activity_anchor);

        UserModel userModel = UserModelManager.getInstance().getUserModel();
        mSelfUserId = userModel.userId;
        mSelfName = userModel.userName;
        mSelfAvatar = userModel.userAvatar;
        mCoverPicUrl = userModel.userAvatar;
        mRoomId = getRoomId();

        mLiveRoom = TRTCLiveRoom.sharedInstance(this);
        mIsEnterRoom = false;
        if (TextUtils.isEmpty(mSelfName)) {
            mSelfName = mSelfUserId;
        }

        initView();
        startPreview();
    }


    private void initView() {
        mRootView = (ConstraintLayout) findViewById(R.id.root);
        mEditLiveRoomName = (EditText) findViewById(R.id.et_live_room_name);
        mEditLiveRoomName.setFocusableInTouchMode(!RTCubeUtils.isRTCubeApp(this));
        mPreFunctionView = findViewById(R.id.anchor_pre_function);
        mPreView = findViewById(R.id.anchor_preview);
        mFunctionView = findViewById(R.id.anchor_function_view);
        mFunctionView.setRoomId(mRoomId);
        if (!TextUtils.isEmpty(mSelfName)) {
            mEditLiveRoomName.setText(getString(R.string.trtcliveroom_create_room_default, mSelfName));
        }
        mToolbar = findViewById(R.id.tool_bar_view);
        mPreFunctionView.setListener(new AnchorPreFunctionView.OnPreFunctionClickListener() {
            @Override
            public void onStartClick() {
                if (mIsCreatingRoom) {
                    return;
                }
                String roomName = mEditLiveRoomName.getText().toString().trim();
                if (TextUtils.isEmpty(roomName)) {
                    ToastUtils.showLong(getText(R.string.trtcliveroom_warning_room_name_empty));
                    return;
                }
                if (roomName.getBytes().length > MAX_LEN) {
                    ToastUtils.showLong(getText(R.string.trtcliveroom_warning_room_name_too_long));
                    return;
                }
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mEditLiveRoomName.getWindowToken(), 0);
                mRoomName = roomName;
                createRoom();
            }

            @Override
            public void onCloseClick() {
                if (!mIsEnterRoom) {
                    finishRoom();
                } else {
                    showExitInfoDialog(getString(R.string.trtcliveroom_warning_anchor_exit_room), false);
                }
            }
        });
        TUILogin.addLoginListener(mTUILoginListener);

        Map<String, Object> map = new HashMap<>();
        map.put(TUIConstants.TUIBeauty.PARAM_NAME_CONTEXT, this);
        map.put(TUIConstants.TUIBeauty.PARAM_NAME_LICENSE_URL, XMAGIC_LICENSE_URL);
        map.put(TUIConstants.TUIBeauty.PARAM_NAME_LICENSE_KEY, XMAGIC_LICENSE_KEY);
        TUICore.callService(TUIConstants.TUIBeauty.SERVICE_NAME, TUIConstants.TUIBeauty.METHOD_INIT_XMAGIC, map);
        mTXCloudVideoView = (TXCloudVideoView) findViewById(R.id.video_view_anchor);
        mTXCloudVideoView.setLogMargin(10, 10, 45, 55);

        mPKContainer = (RelativeLayout) findViewById(R.id.pk_container);
        mImagePKLayer = (ImageView) findViewById(R.id.iv_pk_layer);

        mImagesAnchorHead = (ImageView) findViewById(R.id.iv_anchor_head);
        showHeadIcon(mImagesAnchorHead, mSelfAvatar);

        mImagesAnchorHead.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLog();
            }
        });

        List<TCVideoView> videoViews = new ArrayList<>();
        videoViews.add((TCVideoView) findViewById(R.id.video_view_link_mic_1));
        videoViews.add((TCVideoView) findViewById(R.id.video_view_link_mic_2));
        videoViews.add((TCVideoView) findViewById(R.id.video_view_link_mic_3));
        mVideoViewMgr = new TCVideoViewMgr(videoViews, new TCVideoView.OnRoomViewListener() {
            @Override
            public void onKickUser(String userID) {
                if (userID != null) {
                    mLiveRoom.kickoutJoinAnchor(userID, new TRTCLiveRoomCallback.ActionCallback() {
                        @Override
                        public void onCallback(int code, String msg) {
                        }
                    });
                }
            }
        });

        mGuideLineVertical = (Guideline) findViewById(R.id.gl_vertical);
        mFunctionView = findViewById(R.id.anchor_function_view);
        mFunctionView.setListener(new AnchorFunctionView.OnAnchorFunctionListener() {
            @Override
            public void onClose() {
                if (!mIsEnterRoom) {
                    finishRoom();
                    return;
                }
                if (mCurrentStatus == TRTCLiveRoomDef.ROOM_STATUS_PK) {
                    stopPK();
                    return;
                }
                showExitInfoDialog(getString(R.string.trtcliveroom_warning_anchor_exit_room), false);
            }
        });
        initCountDownView();
    }

    private void showHeadIcon(ImageView view, String avatar) {
        TCUtils.showPicWithUrl(this, view, avatar, R.drawable.trtcliveroom_bg_cover);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopTimer();
        finishRoom();
        exitRoom();

        TUILogin.removeLoginListener(mTUILoginListener);
        UserModelManager.getInstance().getUserModel().userType = UserModel.UserType.NONE;
        mLiveRoom.showVideoDebugLog(false);
        if (mMainHandler != null) {
            mMainHandler.removeCallbacksAndMessages(null);
        }
        mFunctionView.stopRecordAnimation();
        mVideoViewMgr.recycleVideoView();
        mVideoViewMgr = null;
    }


    private void createRoom() {
        mIsCreatingRoom = true;
        enterRoom();
    }

    private String getRoomId() {
        String rooId = "";
        try {
            rooId = mSelfUserId;
        } catch (Exception e) {
            TRTCLogger.e(TAG, "getRoomId failed,roomId:" + mSelfUserId + ",msg:" + e.getMessage());
        }
        return rooId;
    }

    @Override
    public void onBackPressed() {
        if (mIsEnterRoom) {
            showExitInfoDialog(getString(R.string.trtcliveroom_warning_anchor_exit_room), false);
        } else {
            finishRoom();
        }
    }


    private void enterRoom() {
        mLiveRoom.setDelegate(this);
        TRTCLiveRoomDef.TRTCCreateRoomParam param = new TRTCLiveRoomDef.TRTCCreateRoomParam();
        param.roomName = mRoomName;
        param.coverUrl = mSelfAvatar;
        mLiveRoom.createRoom(mRoomId, param, new TRTCLiveRoomCallback.ActionCallback() {
            @Override
            public void onCallback(int code, String msg) {
                if (code == 0) {
                    mIsEnterRoom = true;
                    mPreFunctionView.setVisibility(GONE);
                    mPreView.setVisibility(GONE);
                    mFunctionView.setVisibility(VISIBLE);
                    freshToolView();
                    startTimer();
                    onCreateRoomSuccess();
                    onTRTCRoomCreateSuccess();

                } else {
                    Log.w(TAG, String.format("创建直播间错误, code=%s,error=%s", code, msg));
                    showErrorAndQuit(code, getString(R.string.trtcliveroom_error_create_live_room, msg));
                }
                mIsCreatingRoom = false;
            }
        });
    }

    private void onTRTCRoomCreateSuccess() {
        LiveRoomManager.getInstance().createRoom(mRoomId, new LiveRoomManager.ActionCallback() {
            @Override
            public void onSuccess() {

            }

            @Override
            public void onError(int errorCode, String message) {
                if (errorCode == ERROR_ROOM_ID_EXIT) {
                    onSuccess();
                } else {
                    ToastUtils.showLong("create room failed[" + errorCode + "]:" + message);
                    finish();
                }
            }
        });
    }

    private void freshToolView() {
        ConstraintSet set = new ConstraintSet();
        set.clone(mRootView);
        set.connect(mToolbar.getId(), ConstraintSet.BOTTOM, R.id.fl_audio_effect, ConstraintSet.TOP);
        set.applyTo(mRootView);
    }

    protected void showPublishFinishDetailsDialog() {
        Bundle args = new Bundle();
        args.putString(LIVE_TOTAL_TIME, TCUtils.formattedTime(mSecond));
        args.putString(ANCHOR_HEART_COUNT, String.format(Locale.CHINA, "%d", mHeartCount));
        args.putString(TOTAL_AUDIENCE_COUNT, String.format(Locale.CHINA, "%d", mTotalMemberCount));
        FinishDetailDialogFragment dialogFragment = new FinishDetailDialogFragment();
        dialogFragment.setArguments(args);
        dialogFragment.setCancelable(false);
        if (dialogFragment.isAdded()) {
            dialogFragment.dismiss();
        } else {
            dialogFragment.show(getFragmentManager(), "");
        }
    }

    public void showExitInfoDialog(String msg, Boolean isError) {
        final ExitConfirmDialogFragment dialogFragment = new ExitConfirmDialogFragment();
        dialogFragment.setCancelable(false);
        dialogFragment.setMessage(msg);

        if (dialogFragment.isAdded()) {
            dialogFragment.dismiss();
            return;
        }

        if (isError) {
            exitRoom();
            dialogFragment.setPositiveClickListener(new ExitConfirmDialogFragment.PositiveClickListener() {
                @Override
                public void onClick() {
                    dialogFragment.dismiss();
                    showPublishFinishDetailsDialog();
                }
            });
            dialogFragment.show(getFragmentManager(), "ExitConfirmDialogFragment");
            return;
        }

        dialogFragment.setPositiveClickListener(new ExitConfirmDialogFragment.PositiveClickListener() {
            @Override
            public void onClick() {
                dialogFragment.dismiss();
                exitRoom();
                showPublishFinishDetailsDialog();
            }
        });

        dialogFragment.setNegativeClickListener(new ExitConfirmDialogFragment.NegativeClickListener() {
            @Override
            public void onClick() {
                dialogFragment.dismiss();
            }
        });
        dialogFragment.show(getFragmentManager(), "ExitConfirmDialogFragment");
    }


    private class BroadcastTimerTask extends TimerTask {
        public void run() {
            ++mSecond;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mFunctionView.onBroadcasterTimeUpdate(mSecond);
                }
            });
        }
    }

    private void startTimer() {
        if (mBroadcastTimer == null) {
            mBroadcastTimer = new Timer(true);
            mBroadcastTimerTask = new BroadcastTimerTask();
            mBroadcastTimer.schedule(mBroadcastTimerTask, 1000, 1000);
        }
    }

    private void stopTimer() {
        if (null != mBroadcastTimer) {
            mBroadcastTimerTask.cancel();
        }
    }

    @Override
    public void onError(int code, String message) {

    }

    @Override
    public void onWarning(int code, String message) {

    }

    @Override
    public void onDebugLog(String message) {

    }


    @Override
    public void onAudienceEnter(TRTCLiveRoomDef.TRTCLiveUserInfo userInfo) {
        mTotalMemberCount++;
    }


    @Override
    public void onKickoutJoinAnchor() {

    }


    @Override
    public void onRecvRoomTextMsg(String message, TRTCLiveRoomDef.TRTCLiveUserInfo userInfo) {
    }

    @Override
    public void onRecvRoomCustomMsg(String cmd, String message, TRTCLiveRoomDef.TRTCLiveUserInfo userInfo) {

    }


    private final TUILoginListener mTUILoginListener = new TUILoginListener() {
        @Override
        public void onKickedOffline() {
            Log.e(TAG, "onKickedOffline");
            mLiveRoom.destroyRoom(null);
            finish();
        }
    };


    protected void startPreview() {
        mTXCloudVideoView.setVisibility(VISIBLE);
        PermissionHelper.requestPermission(this, PermissionHelper.PERMISSION_CAMERA,
                new PermissionHelper.PermissionCallback() {
                    @Override
                    public void onGranted() {
                        mLiveRoom.startCameraPreview(true, mTXCloudVideoView, null);
                    }

                    @Override
                    public void onDenied() {

                    }

                    @Override
                    public void onDialogApproved() {

                    }

                    @Override
                    public void onDialogRefused() {
                        exitRoom();
                        finish();
                    }
                });
    }


    private void exitRoom() {
        if (!mIsEnterRoom) {
            return;
        }
        LiveRoomManager.getInstance().destroyRoom(mRoomId, new LiveRoomManager.ActionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "onSuccess: 后台销毁房间成功");
            }

            @Override
            public void onError(int code, String message) {
                Log.d(TAG, "onFailed: 后台销毁房间失败[" + code);
            }
        });
        mLiveRoom.destroyRoom(new TRTCLiveRoomCallback.ActionCallback() {
            @Override
            public void onCallback(int code, String msg) {
                if (code == 0) {
                    Log.d(TAG, "IM销毁房间成功");
                } else {
                    Log.d(TAG, "IM销毁房间失败:" + msg);
                }
            }
        });
        mIsEnterRoom = false;
        mLiveRoom.setDelegate(null);
    }

    private void initCountDownView() {
        mCountDownView = findViewById(R.id.anchor_count_down);
        mCountDownView.setListener(new AnchorCountDownView.OnTimeEndListener() {
            @Override
            public void onTimeEnd() {
                mCountDownView.setVisibility(GONE);
                startPush();
            }
        });
    }

    private void onCreateRoomSuccess() {
        mFunctionView.startRecordAnimation();
        mTXCloudVideoView.setVisibility(VISIBLE);
        PermissionHelper.requestPermission(TCCameraAnchorActivity.this,
                PermissionHelper.PERMISSION_MICROPHONE,
                new PermissionHelper.PermissionCallback() {
                    @Override
                    public void onGranted() {
                        mCountDownView.setVisibility(VISIBLE);
                        mCountDownView.start();
                    }

                    @Override
                    public void onDenied() {

                    }

                    @Override
                    public void onDialogApproved() {

                    }

                    @Override
                    public void onDialogRefused() {
                        exitRoom();
                        finish();
                    }
                });
    }

    private void startPush() {
        mLiveRoom.startPublish(mSelfUserId + "_stream", new TRTCLiveRoomCallback.ActionCallback() {
            @Override
            public void onCallback(int code, String msg) {
                if (code == 0) {
                    Log.d(TAG, "开播成功");
                    showAlertUserLiveTips();
                } else {
                    Log.e(TAG, "开播失败" + msg);
                }
            }
        });
    }


    private void finishRoom() {
        mLiveRoom.stopCameraPreview();
        finish();
    }

    private void showAlertUserLiveTips() {
        if (!isFinishing()) {
            try {
                Class clz = Class.forName("com.tencent.liteav.privacy.util.RTCubeAppLegalUtils");
                Method method = clz.getDeclaredMethod("showAlertUserLiveTips", Context.class);
                method.invoke(null, this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRoomDestroy(String roomId) {
        Log.e(TAG, "onRoomDestroy");
        mLiveRoom.destroyRoom(null);
        if (!isFinishing()) {
            showDestroyDialog();
        }
    }

    private void showDestroyDialog() {
        try {
            Class clz = Class.forName("com.tencent.liteav.privacy.util.RTCubeAppLegalUtils");
            Method method = clz.getDeclaredMethod("showRoomDestroyTips", Context.class);
            method.invoke(null, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setAnchorViewFull(boolean isFull) {
        if (isFull) {
            ConstraintSet set = new ConstraintSet();
            set.clone(mRootView);
            set.connect(mTXCloudVideoView.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            set.connect(mTXCloudVideoView.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            set.connect(mTXCloudVideoView.getId(), ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            set.connect(mTXCloudVideoView.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            set.applyTo(mRootView);
        } else {
            ConstraintSet set = new ConstraintSet();
            set.clone(mRootView);
            set.connect(mTXCloudVideoView.getId(), ConstraintSet.TOP, mPKContainer.getId(), ConstraintSet.TOP);
            set.connect(mTXCloudVideoView.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            set.connect(mTXCloudVideoView.getId(), ConstraintSet.BOTTOM, mPKContainer.getId(), ConstraintSet.BOTTOM);
            set.connect(mTXCloudVideoView.getId(), ConstraintSet.END, mGuideLineVertical.getId(), ConstraintSet.END);
            set.applyTo(mRootView);
        }
    }

    @Override
    public void onAnchorEnter(final String userId) {
        mAnchorUserIdList.add(userId);
        final TCVideoView view = mVideoViewMgr.applyVideoView(userId);
        if (mCurrentStatus != TRTCLiveRoomDef.ROOM_STATUS_PK) {
            view.startLoading();
        }
        mLiveRoom.startPlay(userId, view.getPlayerVideo(), new TRTCLiveRoomCallback.ActionCallback() {
            @Override
            public void onCallback(int code, String msg) {
                if (code == 0) {
                    Log.d(TAG, userId + "");
                    if (mCurrentStatus != TRTCLiveRoomDef.ROOM_STATUS_PK) {
                        view.stopLoading(true);
                    }
                }
            }
        });
    }

    @Override
    public void onRoomInfoChange(TRTCLiveRoomDef.TRTCLiveRoomInfo roomInfo) {
        Log.d(TAG, "onRoomInfoChange");
        final int oldStatus = mCurrentStatus;
        mCurrentStatus = roomInfo.roomStatus;
        setAnchorViewFull(mCurrentStatus != TRTCLiveRoomDef.ROOM_STATUS_PK);
        Log.d(TAG, "onRoomInfoChange: " + mCurrentStatus);
        if (oldStatus == TRTCLiveRoomDef.ROOM_STATUS_PK
                && mCurrentStatus != TRTCLiveRoomDef.ROOM_STATUS_PK) {
            mImagePKLayer.setVisibility(GONE);
            TCVideoView videoView = mVideoViewMgr.getPKUserView();
            mVideoViewPKAnchor = videoView.getPlayerVideo();
            if (mPKContainer.getChildCount() != 0) {
                mPKContainer.removeView(mVideoViewPKAnchor);
                videoView.addView(mVideoViewPKAnchor);
                mVideoViewMgr.clearPKView();
                mVideoViewPKAnchor = null;
            }
        } else if (mCurrentStatus == TRTCLiveRoomDef.ROOM_STATUS_PK) {
            mImagePKLayer.setVisibility(VISIBLE);
            TCVideoView videoView = mVideoViewMgr.getPKUserView();
            videoView.showKickoutBtn(false);
            mVideoViewPKAnchor = videoView.getPlayerVideo();
            ((ViewGroup) (mVideoViewPKAnchor.getParent())).removeView(mVideoViewPKAnchor);
            mPKContainer.addView(mVideoViewPKAnchor);
        }
    }

    @Override
    public void onAnchorExit(String userId) {
        mAnchorUserIdList.remove(userId);
        mLiveRoom.stopPlay(userId, null);
        mVideoViewMgr.recycleVideoView(userId);
    }

    @Override
    public void onRequestRoomPK(final TRTCLiveRoomDef.TRTCLiveUserInfo userInfo, final int timeout) {
        final ConfirmDialogFragment dialogFragment = new ConfirmDialogFragment();
        mPKConfirmDialogFragment = dialogFragment;
        dialogFragment.setCancelable(false);
        dialogFragment.setMessage(getString(R.string.trtcliveroom_request_pk, userInfo.userName));
        if (dialogFragment.isAdded()) {
            dialogFragment.dismiss();
            return;
        }
        dialogFragment.setPositiveText(getString(R.string.trtcliveroom_accept));
        dialogFragment.setNegativeText(getString(R.string.trtcliveroom_refuse));
        dialogFragment.setPositiveClickListener(new ConfirmDialogFragment.PositiveClickListener() {
            @Override
            public void onClick() {
                mFunctionView.setButtonPKState(AnchorFunctionView.PKState.STOP);
                dialogFragment.dismiss();
                mLiveRoom.responseRoomPK(userInfo.userId, true, "");
            }
        });

        dialogFragment.setNegativeClickListener(new ConfirmDialogFragment.NegativeClickListener() {
            @Override
            public void onClick() {
                mFunctionView.setButtonPKState(AnchorFunctionView.PKState.PK);
                dialogFragment.dismiss();
                mLiveRoom.responseRoomPK(userInfo.userId, false,
                        getString(R.string.trtcliveroom_anchor_refuse_pk_request));
            }
        });
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!mIsPaused) {
                    dialogFragment.show(getFragmentManager(), "ConfirmDialogFragment");
                }
            }
        });
    }

    @Override
    public void onQuitRoomPK() {
        mFunctionView.setButtonPKState(AnchorFunctionView.PKState.PK);
        ToastUtils.showShort(R.string.trtcliveroom_tips_quit_pk);
        if (mPKConfirmDialogFragment != null && mPKConfirmDialogFragment.isAdded()) {
            mPKConfirmDialogFragment.dismissAllowingStateLoss();
        }
    }

    @Override
    public void onCancelJoinAnchor() {
    }

    @Override
    public void onCancelRoomPK() {
        mFunctionView.setButtonPKState(AnchorFunctionView.PKState.PK);
        if (mPKConfirmDialogFragment != null && mPKConfirmDialogFragment.isAdded()) {
            mPKConfirmDialogFragment.dismissAllowingStateLoss();
        }
    }

    @Override
    public void onAudienceExit(final TRTCLiveRoomDef.TRTCLiveUserInfo userInfo) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                ConfirmDialogFragment fragment = mLinkMicConfirmDialogFragmentMap.remove(userInfo.userId);
                if (null != fragment && fragment.isAdded()) {
                    fragment.dismissAllowingStateLoss();
                }
            }
        });
    }

    @Override
    public void onRequestJoinAnchor(final TRTCLiveRoomDef.TRTCLiveUserInfo userInfo, String reason, final int timeout) {
        if (mAnchorUserIdList.size() >= LINK_MIC_MEMBER_MAX) {
            mLiveRoom.responseJoinAnchor(userInfo.userId, false, "");
            return;
        }
        final ConfirmDialogFragment dialogFragment = new ConfirmDialogFragment();
        mLinkMicConfirmDialogFragmentMap.put(userInfo.userId, dialogFragment);
        dialogFragment.setCancelable(false);
        dialogFragment.setMessage(getString(R.string.trtcliveroom_request_link_mic, userInfo.userName));
        if (dialogFragment.isAdded()) {
            dialogFragment.dismiss();
            return;
        }
        dialogFragment.setPositiveText(getString(R.string.trtcliveroom_accept));
        dialogFragment.setNegativeText(getString(R.string.trtcliveroom_refuse));
        dialogFragment.setPositiveClickListener(new ConfirmDialogFragment.PositiveClickListener() {
            @Override
            public void onClick() {
                dialogFragment.dismiss();
                mLiveRoom.responseJoinAnchor(userInfo.userId, true, "");
            }
        });

        dialogFragment.setNegativeClickListener(new ConfirmDialogFragment.NegativeClickListener() {
            @Override
            public void onClick() {
                dialogFragment.dismiss();
                mLiveRoom.responseJoinAnchor(userInfo.userId, false,
                        getString(R.string.trtcliveroom_anchor_refuse_link_request));
            }
        });
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!mIsPaused) {
                    dialogFragment.show(getFragmentManager(), "ConfirmDialogFragment");
                }
            }
        });
    }

    @Override
    public void onAudienceRequestJoinAnchorTimeout(final String userId) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                ConfirmDialogFragment fragment = mLinkMicConfirmDialogFragmentMap.remove(userId);
                if (null != fragment && null != fragment.getDialog()) {
                    fragment.dismissAllowingStateLoss();
                }
            }
        });
    }

    @Override
    public void onAnchorRequestRoomPKTimeout(String userId) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (null != mPKConfirmDialogFragment && null != mPKConfirmDialogFragment.getDialog()) {
                    mPKConfirmDialogFragment.dismissAllowingStateLoss();
                    mPKConfirmDialogFragment = null;
                }
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }

    private void stopPK() {
        mFunctionView.setButtonPKState(AnchorFunctionView.PKState.PK);
        mLiveRoom.quitRoomPK(null);
    }


    protected void showErrorAndQuit(int errorCode, String errorMsg) {
        mFunctionView.stopRecordAnimation();
        if (mErrorDialog == null) {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog
                    .Builder(this, R.style.TRTCLiveRoomDialogTheme)
                    .setTitle(R.string.trtcliveroom_error)
                    .setMessage(errorMsg)
                    .setNegativeButton(R.string.trtcliveroom_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mErrorDialog.dismiss();
                            stopTimer();
                            exitRoom();
                            finish();
                        }
                    });

            mErrorDialog = builder.create();
        }
        if (mErrorDialog.isShowing()) {
            mErrorDialog.dismiss();
        }
        if (!isFinishing()) {
            mErrorDialog.show();
        }
    }

    private void showLog() {
        mShowLog = !mShowLog;
        mLiveRoom.showVideoDebugLog(mShowLog);
        if (mTXCloudVideoView != null) {
            mTXCloudVideoView.showLog(mShowLog);
        }
        if (mVideoViewPKAnchor != null) {
            mVideoViewPKAnchor.showLog(mShowLog);
        }

        mVideoViewMgr.showLog(mShowLog);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsPaused = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsPaused = true;
    }
}
