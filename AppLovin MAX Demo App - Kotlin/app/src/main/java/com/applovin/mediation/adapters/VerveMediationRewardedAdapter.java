package com.applovin.mediation.adapters;

import android.app.Activity;
import android.text.TextUtils;

import com.applovin.mediation.MaxReward;
import com.applovin.mediation.adapter.MaxAdapterError;
import com.applovin.mediation.adapter.MaxRewardedAdapter;
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.sdk.AppLovinSdk;

import net.pubnative.lite.sdk.rewarded.HyBidRewardedAd;

public class VerveMediationRewardedAdapter extends VerveMediationBaseAdapter implements MaxRewardedAdapter {

    private HyBidRewardedAd mRewardedAd;

    public VerveMediationRewardedAdapter(AppLovinSdk appLovinSdk) {
        super(appLovinSdk);
    }

    @Override
    public void onDestroy() {
        if (mRewardedAd != null) {
            mRewardedAd.destroy();
            mRewardedAd = null;
        }
    }

    @Override
    public void loadRewardedAd(MaxAdapterResponseParameters parameters, Activity activity, MaxRewardedAdapterListener adapterListener) {
        log("Loading rewarded ad");

        if (adapterListener == null || parameters == null) {
            log("Adapter error. Null parameters");
            return;
        }

        String zoneId;
        if (!TextUtils.isEmpty(parameters.getThirdPartyAdPlacementId())) {
            zoneId = parameters.getThirdPartyAdPlacementId();
        } else {
            log("Could not find the required params in MaxAdapterResponseParameters");
            adapterListener.onRewardedAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION);
            return;
        }

        updateMuteState(parameters);
        updateUserConsent(parameters);

        mRewardedAd = new HyBidRewardedAd(activity, zoneId, new RewardedListener(adapterListener));
        mRewardedAd.setMediation(true);
        mRewardedAd.load();
    }

    @Override
    public void showRewardedAd(MaxAdapterResponseParameters parameters, Activity activity, MaxRewardedAdapterListener adapterListener) {
        log("Showing rewarded ad...");

        if (mRewardedAd.isReady()) {
            configureReward(parameters);
            mRewardedAd.show();
        } else {
            log("Rewarded ad not ready");
            if (adapterListener != null) {
                adapterListener.onRewardedAdDisplayFailed(MaxAdapterError.AD_NOT_READY);
            }
        }
    }

    // -------------------------------- HyBidRewardedAdListener ------------------------------------
    private class RewardedListener
            implements HyBidRewardedAd.Listener {
        private final MaxRewardedAdapterListener listener;
        private boolean hasGrantedReward;

        private RewardedListener(final MaxRewardedAdapterListener listener) {
            this.listener = listener;
        }

        @Override
        public void onRewardedLoaded() {
            log("Rewarded ad loaded");
            listener.onRewardedAdLoaded();
        }

        @Override
        public void onRewardedLoadFailed(final Throwable error) {
            log("Rewarded ad failed to load with error: " + error);
            MaxAdapterError adapterError = toMaxError(error);
            listener.onRewardedAdLoadFailed(adapterError);
        }

        @Override
        public void onRewardedOpened() {
            log("Rewarded ad did track impression");
            listener.onRewardedAdDisplayed();
            listener.onRewardedAdVideoStarted();
        }

        @Override
        public void onRewardedClick() {
            log("Rewarded ad clicked");
            listener.onRewardedAdClicked();
        }

        @Override
        public void onReward() {
            log("Rewarded ad reward granted");
            hasGrantedReward = true;
        }

        @Override
        public void onRewardedClosed() {
            log("Rewarded ad did disappear");
            listener.onRewardedAdVideoCompleted();

            if (hasGrantedReward || shouldAlwaysRewardUser()) {
                MaxReward reward = getReward();
                log("Rewarded user with reward: " + reward);
                listener.onUserRewarded(reward);
            }

            log("Rewarded ad hidden");
            listener.onRewardedAdHidden();
        }
    }
}
