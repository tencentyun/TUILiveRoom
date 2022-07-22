package com.tencent.liteav.liveroom.ui.audience;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.constraintlayout.widget.Guideline;

import com.blankj.utilcode.util.ToastUtils;
import com.tencent.liteav.basic.UserModel;
import com.tencent.liteav.basic.UserModelManager;
import com.tencent.liteav.liveroom.R;
import com.tencent.liteav.liveroom.model.TRTCLiveRoom;
import com.tencent.liteav.liveroom.model.TRTCLiveRoomCallback;
import com.tencent.liteav.liveroom.model.TRTCLiveRoomDef;
import com.tencent.liteav.liveroom.model.TRTCLiveRoomDelegate;
import com.tencent.liteav.liveroom.ui.common.utils.PermissionHelper;
import com.tencent.liteav.liveroom.ui.common.utils.TCConstants;
import com.tencent.liteav.liveroom.ui.common.utils.TCUtils;
import com.tencent.liteav.liveroom.ui.widget.video.TCVideoView;
import com.tencent.liteav.liveroom.ui.widget.video.TCVideoViewMgr;
import com.tencent.qcloud.tuicore.TUILogin;
import com.tencent.qcloud.tuicore.interfaces.TUILoginListener;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Module:   TCAudienceActivity
 * <p>
 * Function: Audience watch UI
 **/
public class TCAudienceActivity extends AppCompatActivity {
    private static final String TAG = TCAudienceActivity.class.getSimpleName();

    private static final long LINK_MIC_INTERVAL = 3 * 1000;
    private static final int  LINK_MIC_TIMEOUT  = 15;

    private static final int CODE_ERROR   = -1;
    private static final int CODE_TIMEOUT = -2;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private ConstraintLayout     mRootView;
    private TXCloudVideoView     mVideoViewAnchor;
    private TXCloudVideoView     mVideoViewPKAnchor;
    private Guideline            mGuideLineVertical;
    private Guideline            mGuideLineHorizontal;
    private TextView             mTextAnchorLeave;
    private ImageView            mImageBackground;
    private Button               mButtonLinkMic;
    private AlertDialog          mDialogError;
    private TCVideoViewMgr       mVideoViewMgr;
    private RelativeLayout       mPKContainer;
    private Toast                mToastNotice;
    private Timer                mNoticeTimer;
    private AudienceFunctionView mFunctionView;
    private TRTCLiveRoom         mLiveRoom;

    private final ConcurrentMap<String, TRTCLiveRoomDef.TRTCLiveUserInfo> mUserInfoMap =
            new ConcurrentHashMap<>();

    private boolean  mShowLog;
    private long     mLastLinkMicTime;
    private long     mCurrentAudienceCount;
    private boolean  isEnterRoom     = false;
    private boolean  isUseCDNPlay    = false;
    private boolean  mIsAnchorEnter  = false;
    private boolean  mIsBeingLinkMic = false;
    private String   mRoomId         = "";
    private int      mCurrentStatus  = TRTCLiveRoomDef.ROOM_STATUS_NONE;
    private String   mAnchorAvatarURL;
    private String   mAnchorNickname;
    private String   mAnchorId;
    private String   mSelfUserId     = "";
    private String   mSelfNickname   = "";
    private String   mSelfAvatar     = "";
    private String   mCoverUrl       = "";
    private Runnable mGetAudienceRunnable;

    // If the anchor does not enter within a certain period of time
    private Runnable mShowAnchorLeave = new Runnable() {
        @Override
        public void run() {
            if (mTextAnchorLeave != null) {
                mTextAnchorLeave.setVisibility(mIsAnchorEnter ? View.GONE : View.VISIBLE);
                mImageBackground.setVisibility(mIsAnchorEnter ? View.GONE : View.VISIBLE);
            }
        }
    };

    private TRTCLiveRoomDelegate mTRTCLiveRoomDelegate = new TRTCLiveRoomDelegate() {
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
        public void onRoomInfoChange(TRTCLiveRoomDef.TRTCLiveRoomInfo roomInfo) {
            if (isUseCDNPlay) {
                return;
            }
            final int oldStatus = mCurrentStatus;
            mCurrentStatus = roomInfo.roomStatus;
            setAnchorViewFull(mCurrentStatus != TRTCLiveRoomDef.ROOM_STATUS_PK);
            Log.d(TAG, "onRoomInfoChange: " + mCurrentStatus);
            if (oldStatus == TRTCLiveRoomDef.ROOM_STATUS_PK
                    && mCurrentStatus != TRTCLiveRoomDef.ROOM_STATUS_PK) {
                TCVideoView videoView = mVideoViewMgr.getPKUserView();
                mVideoViewPKAnchor = videoView.getPlayerVideo();
                if (mPKContainer.getChildCount() != 0) {
                    mPKContainer.removeView(mVideoViewPKAnchor);
                    videoView.addView(mVideoViewPKAnchor);
                    mVideoViewMgr.clearPKView();
                    mVideoViewPKAnchor = null;
                }
            } else if (mCurrentStatus == TRTCLiveRoomDef.ROOM_STATUS_PK) {
                TCVideoView videoView = mVideoViewMgr.getPKUserView();
                mVideoViewPKAnchor = videoView.getPlayerVideo();
                videoView.removeView(mVideoViewPKAnchor);
                mPKContainer.addView(mVideoViewPKAnchor);
            }
        }

        @Override
        public void onRoomDestroy(String roomId) {
            showErrorAndQuit(0, getString(R.string.trtcliveroom_warning_room_disband));
        }

        @Override
        public void onAnchorEnter(final String userId) {
            if (userId.equals(mAnchorId)) {
                mIsAnchorEnter = true;
                mTextAnchorLeave.setVisibility(View.GONE);
                mVideoViewAnchor.setVisibility(View.VISIBLE);
                mImageBackground.setVisibility(View.GONE);
                mLiveRoom.startPlay(userId, mVideoViewAnchor, new TRTCLiveRoomCallback.ActionCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                        if (code != 0) {
                            onAnchorExit(userId);
                        }
                    }
                });
            } else {
                TCVideoView view = mVideoViewMgr.applyVideoView(userId);
                view.showKickoutBtn(false);
                mLiveRoom.startPlay(userId, view.getPlayerVideo(), null);
            }
        }

        @Override
        public void onAnchorExit(String userId) {
            if (userId.equals(mAnchorId)) {
                mVideoViewAnchor.setVisibility(View.GONE);
                mImageBackground.setVisibility(View.VISIBLE);
                mTextAnchorLeave.setVisibility(View.VISIBLE);
                mLiveRoom.stopPlay(userId, null);
            } else {
                mVideoViewMgr.recycleVideoView(userId);
                mLiveRoom.stopPlay(userId, null);
            }
        }

        @Override
        public void onAudienceEnter(TRTCLiveRoomDef.TRTCLiveUserInfo userInfo) {
            Log.d(TAG, "onAudienceEnter: " + userInfo);
        }

        @Override
        public void onAudienceExit(TRTCLiveRoomDef.TRTCLiveUserInfo userInfo) {
            Log.d(TAG, "onAudienceExit: " + userInfo);
        }

        @Override
        public void onRequestJoinAnchor(TRTCLiveRoomDef.TRTCLiveUserInfo userInfo, String reason, int timeout) {

        }

        @Override
        public void onCancelRoomPK() {

        }

        @Override
        public void onCancelJoinAnchor() {

        }

        @Override
        public void onKickoutJoinAnchor() {
            makeToast(getResources().getString(R.string.trtcliveroom_warning_kick_out_by_anchor),
                    Toast.LENGTH_LONG).show();
            stopLinkMic();
        }

        @Override
        public void onRequestRoomPK(TRTCLiveRoomDef.TRTCLiveUserInfo userInfo, int timeout) {

        }

        @Override
        public void onQuitRoomPK() {

        }

        @Override
        public void onRecvRoomTextMsg(String message, TRTCLiveRoomDef.TRTCLiveUserInfo userInfo) {
        }

        @Override
        public void onRecvRoomCustomMsg(String cmd, String message, TRTCLiveRoomDef.TRTCLiveUserInfo userInfo) {
        }

        @Override
        public void onAudienceRequestJoinAnchorTimeout(String userId) {

        }

        @Override
        public void onAnchorRequestRoomPKTimeout(String userId) {

        }
    };

    private final Toast makeToast(String message, int duration) {
        final Toast toast = new Toast(this);
        TextView textView = new TextView(this);
        textView.setBackgroundColor(getResources().getColor(R.color.trtcliveroom_color_bg_toast_green));
        textView.setTextColor(Color.WHITE);
        textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        textView.setTextSize(16);
        textView.setPadding(30, 40, 30, 40);
        textView.setText(message);
        toast.setView(textView);
        toast.setDuration(duration);
        toast.setGravity(Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 200);
        return toast;
    }

    protected void showErrorAndQuit(int errorCode, String errorMsg) {
        if (isFinishing()) {
            return;
        }
        if (mDialogError == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.TRTCLiveRoomDialogTheme)
                    .setTitle(R.string.trtcliveroom_error)
                    .setMessage(errorMsg)
                    .setCancelable(false)
                    .setNegativeButton(R.string.trtcliveroom_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mDialogError.dismiss();
                            exitRoom();
                            finish();
                        }
                    });

            mDialogError = builder.create();
        }
        if (mDialogError.isShowing()) {
            mDialogError.dismiss();
        }
        mDialogError.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UserModelManager.getInstance().getUserModel().userType = UserModel.UserType.LIVE_ROOM;
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.trtcliveroom_activity_audience);

        Intent intent = getIntent();
        isUseCDNPlay = intent.getBooleanExtra(TCConstants.USE_CDN_PLAY, false);
        mRoomId = intent.getStringExtra(TCConstants.GROUP_ID);
        mAnchorId = intent.getStringExtra(TCConstants.PUSHER_ID);
        mAnchorNickname = intent.getStringExtra(TCConstants.PUSHER_NAME);
        mCoverUrl = intent.getStringExtra(TCConstants.COVER_PIC);
        mAnchorAvatarURL = intent.getStringExtra(TCConstants.PUSHER_AVATAR);

        UserModel userModel = UserModelManager.getInstance().getUserModel();
        mSelfNickname = userModel.userName;
        mSelfUserId = userModel.userId;
        mSelfAvatar = userModel.userAvatar;

        List<TCVideoView> videoViewList = new ArrayList<>();
        videoViewList.add((TCVideoView) findViewById(R.id.video_view_link_mic_1));
        videoViewList.add((TCVideoView) findViewById(R.id.video_view_link_mic_2));
        videoViewList.add((TCVideoView) findViewById(R.id.video_view_link_mic_3));
        mVideoViewMgr = new TCVideoViewMgr(videoViewList, null);

        mLiveRoom = TRTCLiveRoom.sharedInstance(this);
        mLiveRoom.setDelegate(mTRTCLiveRoomDelegate);

        initView();
        enterRoom();
        mHandler.postDelayed(mShowAnchorLeave, 3000);
        TUILogin.addLoginListener(mTUILoginListener);
    }


    private void initView() {
        mFunctionView = findViewById(R.id.audience_function_view);
        mFunctionView.setRoomId(mRoomId + "", mAnchorId);
        mFunctionView.setListener(new AudienceFunctionView.OnCloseListener() {
            @Override
            public void onClose() {
                exitRoom();
                finish();
            }
        });
        mFunctionView.setAnchorInfo(mAnchorAvatarURL, mAnchorNickname, mRoomId + "");
        mVideoViewAnchor = (TXCloudVideoView) findViewById(R.id.video_view_anchor);
        mVideoViewAnchor.setLogMargin(10, 10, 45, 55);

        findViewById(R.id.iv_anchor_record_ball).setVisibility(View.GONE);
        mCurrentAudienceCount++;

        mImageBackground = (ImageView) findViewById(R.id.audience_background);
        mImageBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        TCUtils.showPicWithUrl(TCAudienceActivity.this,
                mImageBackground, mCoverUrl, R.drawable.trtcliveroom_bg_cover);

        mButtonLinkMic = (Button) findViewById(R.id.audience_btn_linkmic);
        mButtonLinkMic.setVisibility(View.VISIBLE);
        mButtonLinkMic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsBeingLinkMic) {
                    long curTime = System.currentTimeMillis();
                    if (curTime < mLastLinkMicTime + LINK_MIC_INTERVAL) {
                        Toast.makeText(getApplicationContext(),
                                R.string.trtcliveroom_tips_rest, Toast.LENGTH_SHORT).show();
                    } else {
                        mLastLinkMicTime = curTime;
                        startLinkMic();
                    }
                } else {
                    stopLinkMic();
                }
            }
        });

        mGuideLineVertical = (Guideline) findViewById(R.id.gl_vertical);
        mGuideLineHorizontal = (Guideline) findViewById(R.id.gl_horizontal);
        mPKContainer = (RelativeLayout) findViewById(R.id.pk_container);
        mRootView = (ConstraintLayout) findViewById(R.id.root);
        mTextAnchorLeave = (TextView) findViewById(R.id.tv_anchor_leave);
    }

    private void setAnchorViewFull(boolean isFull) {
        if (isFull) {
            ConstraintSet set = new ConstraintSet();
            set.clone(mRootView);
            set.connect(mVideoViewAnchor.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            set.connect(mVideoViewAnchor.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            set.connect(mVideoViewAnchor.getId(), ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            set.connect(mVideoViewAnchor.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            set.applyTo(mRootView);
        } else {
            ConstraintSet set = new ConstraintSet();
            set.clone(mRootView);
            set.connect(mVideoViewAnchor.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            set.connect(mVideoViewAnchor.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            set.connect(mVideoViewAnchor.getId(), ConstraintSet.BOTTOM,
                    mGuideLineHorizontal.getId(), ConstraintSet.BOTTOM);
            set.connect(mVideoViewAnchor.getId(), ConstraintSet.END, mGuideLineVertical.getId(), ConstraintSet.END);
            set.applyTo(mRootView);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        UserModelManager.getInstance().getUserModel().userType = UserModel.UserType.NONE;
        mLiveRoom.showVideoDebugLog(false);
        mHandler.removeCallbacks(mGetAudienceRunnable);
        mHandler.removeCallbacks(mShowAnchorLeave);
        exitRoom();
        mVideoViewMgr.recycleVideoView();
        mVideoViewMgr = null;
        stopLinkMic();
        hideNoticeToast();
        TUILogin.removeLoginListener(mTUILoginListener);
    }

    private void enterRoom() {
        if (isEnterRoom) {
            return;
        }
        mLiveRoom.enterRoom(mRoomId, new TRTCLiveRoomCallback.ActionCallback() {
            @Override
            public void onCallback(int code, String msg) {
                if (code == 0) {
                    ToastUtils.showShort(R.string.trtcliveroom_tips_enter_room_success);
                    isEnterRoom = true;
                    getAudienceList();
                } else {
                    ToastUtils.showLong(getString(R.string.trtcliveroom_tips_enter_room_fail, code));
                    exitRoom();
                    finish();
                }
            }
        });
    }

    private void getAudienceList() {
        mGetAudienceRunnable = new Runnable() {
            @Override
            public void run() {
                mLiveRoom.getAudienceList(new TRTCLiveRoomCallback.UserListCallback() {
                    @Override
                    public void onCallback(int code, String msg, List<TRTCLiveRoomDef.TRTCLiveUserInfo> list) {
                        if (code == 0) {
                            for (TRTCLiveRoomDef.TRTCLiveUserInfo info : list) {
                                mUserInfoMap.putIfAbsent(info.userId, info);
                            }
                            mCurrentAudienceCount += list.size();
                        } else {
                            mHandler.postDelayed(mGetAudienceRunnable, 2000);
                        }
                    }
                });
            }
        };
        mHandler.postDelayed(mGetAudienceRunnable, 2000);
    }

    private void exitRoom() {
        if (isEnterRoom && mLiveRoom != null) {
            mLiveRoom.exitRoom(null);
            isEnterRoom = false;
        }
    }

    private void startLinkMic() {
        PermissionHelper.requestPermission(this,
                PermissionHelper.PERMISSION_MICROPHONE, new PermissionHelper.PermissionCallback() {
                    @Override
                    public void onGranted() {
                        PermissionHelper.requestPermission(TCAudienceActivity.this,
                                PermissionHelper.PERMISSION_CAMERA, new PermissionHelper.PermissionCallback() {

                                    public void onGranted() {
                                        onStartLinkMic();
                                    }

                                    @Override
                                    public void onDenied() {
                                    }

                                    @Override
                                    public void onDialogApproved() {

                                    }

                                    @Override
                                    public void onDialogRefused() {

                                    }
                                });
                    }

                    @Override
                    public void onDenied() {

                    }

                    @Override
                    public void onDialogApproved() {

                    }

                    @Override
                    public void onDialogRefused() {
                    }
                });
    }

    private void onStartLinkMic() {
        mButtonLinkMic.setEnabled(false);
        mButtonLinkMic.setBackgroundResource(R.drawable.trtcliveroom_linkmic_off);
        showNoticeToast(getString(R.string.trtcliveroom_wait_anchor_accept));
        mLiveRoom.requestJoinAnchor(getString(R.string.trtcliveroom_request_link_mic_anchor, mSelfUserId),
                LINK_MIC_TIMEOUT, new TRTCLiveRoomCallback.ActionCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                        if (code == 0) {
                            hideNoticeToast();
                            makeToast(getString(R.string.trtcliveroom_anchor_accept_link_mic),
                                    Toast.LENGTH_SHORT).show();
                            joinPusher();
                            return;
                        }
                        if (code == CODE_ERROR) {
                            makeToast(getString(R.string.trtcliveroom_anchor_refuse_link_request),
                                    Toast.LENGTH_SHORT).show();
                        } else if (code == CODE_TIMEOUT) {
                            ToastUtils.showShort(getString(R.string.trtcliveroom_link_mic_anchor_timeout));
                        } else {
                            ToastUtils.showShort(getString(R.string.trtcliveroom_error_request_link_mic, msg));
                        }
                        mButtonLinkMic.setEnabled(true);
                        hideNoticeToast();
                        mIsBeingLinkMic = false;
                        mButtonLinkMic.setBackgroundResource(R.drawable.trtcliveroom_linkmic_on);
                    }
                });
    }

    private void joinPusher() {
        TCVideoView videoView = mVideoViewMgr.applyVideoView(mSelfUserId);
        mLiveRoom.startCameraPreview(true, videoView.getPlayerVideo(), new TRTCLiveRoomCallback.ActionCallback() {
            @Override
            public void onCallback(int code, String msg) {
                if (code == 0) {
                    mLiveRoom.startPublish(mSelfUserId + "_stream", new TRTCLiveRoomCallback.ActionCallback() {
                        @Override
                        public void onCallback(int code, String msg) {
                            if (code == 0) {
                                mButtonLinkMic.setEnabled(true);
                                mIsBeingLinkMic = true;
                                mFunctionView.setSwitchCamVisibility(View.VISIBLE);
                            } else {
                                stopLinkMic();
                                mButtonLinkMic.setEnabled(true);
                                mButtonLinkMic.setBackgroundResource(R.drawable.trtcliveroom_linkmic_on);
                                Toast.makeText(TCAudienceActivity.this,
                                        getString(R.string.trtcliveroom_fail_link_mic, msg), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        });
    }

    private void stopLinkMic() {
        mIsBeingLinkMic = false;
        if (mButtonLinkMic != null) {
            mButtonLinkMic.setEnabled(true);
            mButtonLinkMic.setBackgroundResource(R.drawable.trtcliveroom_linkmic_on);
        }
        mFunctionView.setSwitchCamVisibility(View.INVISIBLE);
        mLiveRoom.stopCameraPreview();
        mLiveRoom.stopPublish(null);
        if (mVideoViewMgr != null) {
            mVideoViewMgr.recycleVideoView(mSelfUserId);
        }
    }


    private void showNoticeToast(String text) {
        if (mToastNotice == null) {
            mToastNotice = makeToast(text, Toast.LENGTH_LONG);
        }

        if (mNoticeTimer == null) {
            mNoticeTimer = new Timer();
        }
        mNoticeTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mToastNotice.show();
            }
        }, 0, 3000);
    }

    private void hideNoticeToast() {
        if (mToastNotice != null) {
            mToastNotice.cancel();
            mToastNotice = null;
        }
        if (mNoticeTimer != null) {
            mNoticeTimer.cancel();
            mNoticeTimer = null;
        }
    }

    private TUILoginListener mTUILoginListener = new TUILoginListener() {
        @Override
        public void onKickedOffline() {
            Log.e(TAG, "onKickedOffline");
            finish();
        }
    };
}
