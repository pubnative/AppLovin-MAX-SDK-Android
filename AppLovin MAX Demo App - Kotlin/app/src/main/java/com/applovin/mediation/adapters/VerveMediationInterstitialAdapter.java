package com.applovin.mediation.adapters;

import android.app.Activity;
import android.text.TextUtils;

import com.applovin.mediation.adapter.MaxAdapterError;
import com.applovin.mediation.adapter.MaxInterstitialAdapter;
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.sdk.AppLovinSdk;

import net.pubnative.lite.sdk.interstitial.HyBidInterstitialAd;

public class VerveMediationInterstitialAdapter extends VerveMediationBaseAdapter implements MaxInterstitialAdapter {

    private HyBidInterstitialAd mInterstitialAd;

    public VerveMediationInterstitialAdapter(AppLovinSdk appLovinSdk) {
        super(appLovinSdk);
    }

    @Override
    public void onDestroy() {
        if (mInterstitialAd != null) {
            mInterstitialAd.destroy();
            mInterstitialAd = null;
        }
    }

    @Override
    public void loadInterstitialAd(MaxAdapterResponseParameters parameters, Activity activity, MaxInterstitialAdapterListener adapterListener) {
        log("Loading interstitial ad");

        if (adapterListener == null || parameters == null) {
            log("Adapter error. Null parameters");
            return;
        }

        String zoneId;
        if (!TextUtils.isEmpty(parameters.getThirdPartyAdPlacementId())) {
            zoneId = parameters.getThirdPartyAdPlacementId();
        } else {
            log("Could not find the required params in MaxAdapterResponseParameters");
            adapterListener.onInterstitialAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION);
            return;
        }

        updateMuteState(parameters);
        updateUserConsent(parameters);

        mInterstitialAd = new HyBidInterstitialAd(activity, zoneId, new InterstitialListener(adapterListener));
        mInterstitialAd.setMediation(true);
        mInterstitialAd.load();
    }

    @Override
    public void showInterstitialAd(MaxAdapterResponseParameters parameters, Activity activity, MaxInterstitialAdapterListener listener) {
        log("Showing interstitial ad...");

        if (mInterstitialAd.isReady()) {
            mInterstitialAd.show();
        } else {
            log("Interstitial ad not ready");
            if (listener != null) {
                listener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_NOT_READY);
            }
        }
    }

    // ----------------------------- HyBidInterstitialAdListener -----------------------------------
    private class InterstitialListener
            implements HyBidInterstitialAd.Listener {
        private final MaxInterstitialAdapterListener listener;

        private InterstitialListener(final MaxInterstitialAdapterListener listener) {
            this.listener = listener;
        }

        @Override
        public void onInterstitialLoaded() {
            log("Interstitial ad loaded");
            listener.onInterstitialAdLoaded();
        }

        @Override
        public void onInterstitialLoadFailed(final Throwable error) {
            log("Interstitial ad failed to load with error: " + error);
            MaxAdapterError adapterError = toMaxError(error);
            listener.onInterstitialAdLoadFailed(adapterError);
        }

        @Override
        public void onInterstitialImpression() {
            log("Interstitial did track impression");
            listener.onInterstitialAdDisplayed();
        }

        @Override
        public void onInterstitialClick() {
            log("Interstitial clicked");
            listener.onInterstitialAdClicked();
        }

        @Override
        public void onInterstitialDismissed() {
            log("Interstitial hidden");
            listener.onInterstitialAdHidden();
        }
    }
}