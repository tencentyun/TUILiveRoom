package com.tencent.liteav.demo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.tencent.liteav.basic.AvatarConstant;
import com.tencent.liteav.basic.IntentUtils;
import com.tencent.liteav.basic.UserModelManager;
import com.tencent.liteav.debug.GenerateGlobalConfig;
import com.tencent.liteav.liveroom.TUILiveRoom;
import com.tencent.liteav.liveroom.ui.common.utils.TCConstants;
import com.tencent.qcloud.tuicore.TUILogin;
import com.tencent.qcloud.tuicore.interfaces.TUICallback;
import com.tencent.qcloud.tuicore.interfaces.TUILoginListener;

import java.util.Random;


public class MainActivity extends Activity {

    private EditText    mRoomIdEt;
    private TextView    mEnterRoomTv;
    private TUILiveRoom mLiveVideo;

    private final TextWatcher mEditTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (!TextUtils.isEmpty(mRoomIdEt.getText().toString())) {
                mEnterRoomTv.setEnabled(true);
            } else {
                mEnterRoomTv.setEnabled(false);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {

        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initData();
        initView();
    }

    private void initData() {
        TUILogin.addLoginListener(new TUILoginListener() {
            @Override
            public void onKickedOffline() {
                super.onKickedOffline();
            }

            @Override
            public void onUserSigExpired() {
                super.onUserSigExpired();
            }
        });
        String userId = UserModelManager.getInstance().getUserModel().userId;
        if (TextUtils.isEmpty(userId)) {
            Toast.makeText(this, getString(R.string.toast_login_success), Toast.LENGTH_SHORT).show();
        }
        TUILogin.login(this, GenerateGlobalConfig.SDKAPPID, userId,
                GenerateGlobalConfig.genTestUserSig(userId), new TUICallback() {
                    @Override
                    public void onSuccess() {

                    }

                    @Override
                    public void onError(int errorCode, String errorMessage) {

                    }
                });
    }

    private void initView() {
        mRoomIdEt = findViewById(R.id.et_room_id);
        mEnterRoomTv = findViewById(R.id.tv_enter_room);
        mLiveVideo = TUILiveRoom.sharedInstance(this);
        mRoomIdEt.addTextChangedListener(mEditTextWatcher);

        findViewById(R.id.tv_enter_room).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String roomId = mRoomIdEt.getText().toString().trim();
                enterRoom(roomId);
            }
        });
        findViewById(R.id.btn_create_room).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int index = new Random().nextInt(AvatarConstant.USER_AVATAR_ARRAY.length);
                String coverUrl = AvatarConstant.USER_AVATAR_ARRAY[index];
                int roomId = Integer.parseInt(TUILogin.getUserId());
                String roomName = TUILogin.getUserId();
                createRoom(roomId, roomName, coverUrl);
            }
        });
        findViewById(R.id.btn_trtcliveroom_link).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(TCConstants.TRTC_LIVE_ROOM_DOCUMENT_URL));
                IntentUtils.safeStartActivity(MainActivity.this, intent);
            }
        });
    }

    private void createRoom(int roomId, String roomName, String coverUrl) {
        mLiveVideo.createRoom(roomId, roomName, coverUrl);
    }

    private void enterRoom(final String roomId) {
        mLiveVideo.enterRoom(roomId);
    }
}
