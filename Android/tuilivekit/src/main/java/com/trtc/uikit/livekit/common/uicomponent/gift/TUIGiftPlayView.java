package com.trtc.uikit.livekit.common.uicomponent.gift;

import static com.trtc.uikit.livekit.common.uicomponent.gift.service.GiftConstants.KEY_LIKE;
import static com.trtc.uikit.livekit.common.uicomponent.gift.service.GiftConstants.SUB_KEY_LIKE_SEND;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import com.opensource.svgaplayer.SVGACallback;
import com.opensource.svgaplayer.SVGAImageView;
import com.opensource.svgaplayer.SVGAParser;
import com.opensource.svgaplayer.SVGAVideoEntity;
import com.tencent.qcloud.tuicore.TUICore;
import com.tencent.qcloud.tuicore.TUILogin;
import com.tencent.qcloud.tuicore.interfaces.ITUINotification;
import com.trtc.tuikit.common.livedata.Observer;
import com.trtc.uikit.livekit.R;
import com.trtc.uikit.livekit.common.uicomponent.gift.model.TUIGift;
import com.trtc.uikit.livekit.common.uicomponent.gift.model.TUIGiftUser;
import com.trtc.uikit.livekit.common.uicomponent.gift.service.GiftPresenter;
import com.trtc.uikit.livekit.common.uicomponent.gift.store.GiftSendData;
import com.trtc.uikit.livekit.common.uicomponent.gift.store.GiftStore;
import com.trtc.uikit.livekit.common.uicomponent.gift.view.GiftBulletFrameLayout;
import com.trtc.uikit.livekit.common.uicomponent.gift.view.IGiftPlayView;
import com.trtc.uikit.livekit.common.uicomponent.gift.view.like.GiftHeartLayout;
import com.trtc.uikit.livekit.common.utils.LiveKitLog;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class TUIGiftPlayView extends LinearLayout implements IGiftPlayView {

    private static final String  TAG                       = "TUIGiftPlayView";
    private static final int     MAX_SHOW_GIFT_BULLET_SIZE = 3;

    private final Context                mContext;
    private LinearLayout                 mGiftBulletGroup;
    private GiftPresenter                mPresenter;
    private final String                 mRoomId;
    private SVGAImageView                mAnimationView;
    private GiftHeartLayout              mHeartLayout;
    private final List<TUIGift>          mSentGiftAnimation;
    private TUIGiftPlayViewListener      mGiftPlayViewListener;
    private final SVGAParser             mSVGAParser;
    private int                          mLikeCount = 0;
    private final ITUINotification       mLikeNotification = (key, subKey, param) -> receiveLike();
    private final Observer<GiftSendData> mGiftSendDataObserver = giftSendData ->
            receiveGift(giftSendData.gift, giftSendData.giftCount, giftSendData.sender, giftSendData.receiver);

    public TUIGiftPlayView(Context context, String roomId) {
        super(context);
        this.mContext = context;
        this.mRoomId = roomId;
        mSVGAParser = SVGAParser.Companion.shareParser();
        mSVGAParser.init(context);
        mSentGiftAnimation = new LinkedList<>();
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.livekit_gift_layout_svga_animator, this, true);
        mGiftBulletGroup = findViewById(R.id.gift_bullet_group);
        mAnimationView = findViewById(R.id.gift_svga_view);
        mHeartLayout = findViewById(R.id.heart_layout);
        mAnimationView.setCallback(new SVGACallback() {
            @Override
            public void onPause() {
            }

            @Override
            public void onFinished() {
                if (isAttachedToWindow()) {
                    mAnimationView.setVisibility(GONE);
                    mSentGiftAnimation.remove(0);
                    showGiftAnimation();
                }
            }

            @Override
            public void onRepeat() {
            }

            @Override
            public void onStep(int i, double v) {
            }
        });
        initPresenter();
    }

    @Override
    protected void onAttachedToWindow() {
        TUICore.registerEvent(KEY_LIKE, SUB_KEY_LIKE_SEND, mLikeNotification);
        GiftStore.getInstance().mGiftSendData.observe(mGiftSendDataObserver);
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        TUICore.unRegisterEvent(KEY_LIKE, SUB_KEY_LIKE_SEND, mLikeNotification);
        GiftStore.getInstance().mGiftSendData.removeObserver(mGiftSendDataObserver);
        mPresenter.destroyPresenter();
        mAnimationView.setCallback(null);
        mAnimationView.stopAnimation();
        super.onDetachedFromWindow();
    }

    private void initPresenter() {
        mPresenter = new GiftPresenter(mRoomId);
        mPresenter.initGiftPlayView(this);
    }

    private void showGiftBullet(TUIGift model, TUIGiftUser sender, TUIGiftUser receiver, int giftCount) {
        if (mGiftBulletGroup.getChildCount() >= MAX_SHOW_GIFT_BULLET_SIZE) {
            //If there are more than 3 gifts, the first gift barrage will be removed from the interface
            View firstShowBulletView = mGiftBulletGroup.getChildAt(0);
            if (firstShowBulletView != null) {
                GiftBulletFrameLayout bulletView = (GiftBulletFrameLayout) firstShowBulletView;
                bulletView.clearHandler();
                mGiftBulletGroup.removeView(bulletView);
            }
        }
        GiftBulletFrameLayout giftFrameLayout = new GiftBulletFrameLayout(mContext);
        mGiftBulletGroup.addView(giftFrameLayout);
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mGiftBulletGroup.getLayoutParams();
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        if (giftFrameLayout.setGift(model, giftCount, sender, receiver)) {
            giftFrameLayout.startAnimation();
        }
    }

    private void showGiftAnimation() {
        if (mSentGiftAnimation.isEmpty()) {
            mAnimationView.setVisibility(GONE);
            return;
        }
        if (mGiftPlayViewListener != null) {
            mGiftPlayViewListener.onPlayGiftAnimation(this, mSentGiftAnimation.get(0));
        }
    }

    /**
     * Insert a gift record (local sending/receiving gifts from the remote will pass through)
     */
    @Override
    public void receiveGift(final TUIGift gift, int giftCount, TUIGiftUser sender, TUIGiftUser receiver) {
        if (gift == null) {
            LiveKitLog.error(TAG + " receiveGift data is empty");
            return;
        }
        if (sender != null && TextUtils.equals(sender.userId, TUILogin.getUserId())) {
            sender.userName = mContext.getString(R.string.livekit_gift_me);
        }
        if (receiver != null && TextUtils.equals(receiver.userId, TUILogin.getUserId())) {
            receiver.userName = mContext.getString(R.string.livekit_gift_me);
        }
        if (TextUtils.isEmpty(gift.animationUrl)) {
            showGiftBullet(gift, sender, receiver, giftCount);
        } else {
            mSentGiftAnimation.add(gift);
            if (mSentGiftAnimation.size() == 1) {
                showGiftAnimation();
            }
        }
        if (mGiftPlayViewListener != null) {
            mGiftPlayViewListener.onReceiveGift(gift, giftCount, sender, receiver);
        }
    }

    public void receiveLike() {
        mLikeCount++;
        mHeartLayout.addFavor();
    }

    public int getLikeCount() {
        return mLikeCount;
    }

    public void playGiftAnimation(InputStream stream) {
        if (stream == null) {
            LiveKitLog.error(TAG + " InputStream is null");
            return;
        }
        mSVGAParser.decodeFromInputStream(stream, "", new SVGAParser.ParseCompletion() {
            @Override
            public void onComplete(@NonNull SVGAVideoEntity svgaVideoEntity) {
                mAnimationView.setVisibility(VISIBLE);
                mAnimationView.setVideoItem(svgaVideoEntity);
                mAnimationView.startAnimation();
            }

            @Override
            public void onError() {
                LiveKitLog.error(TAG + " decodeFromURL onError");
            }
        }, true, null, "");
    }

    public void setListener(TUIGiftPlayViewListener listener) {
        mGiftPlayViewListener = listener;
    }

    public interface TUIGiftPlayViewListener {
        void onReceiveGift(TUIGift gift, int giftCount, TUIGiftUser sender, TUIGiftUser receiver);

        void onPlayGiftAnimation(TUIGiftPlayView view, TUIGift gift);
    }
}
