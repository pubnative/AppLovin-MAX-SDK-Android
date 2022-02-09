package com.applovin.mediation.adapters;

import android.app.Activity;
import android.text.TextUtils;

import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.adapter.MaxAdViewAdapter;
import com.applovin.mediation.adapter.MaxAdapterError;
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.sdk.AppLovinSdk;

import net.pubnative.lite.sdk.models.AdSize;
import net.pubnative.lite.sdk.views.HyBidAdView;

public class VerveMediationBannerAdapter extends VerveMediationBaseAdapter implements MaxAdViewAdapter {

    private HyBidAdView mAdView;

    public VerveMediationBannerAdapter(AppLovinSdk appLovinSdk) {
        super(appLovinSdk);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAdView != null) {
            mAdView.destroy();
            mAdView = null;
        }
    }

    @Override
    public void loadAdViewAd(MaxAdapterResponseParameters parameters, MaxAdFormat adFormat, Activity activity, MaxAdViewAdapterListener adapterListener) {
        log("Loading " + adFormat.getLabel() + " ad view ad...");
        if (adapterListener == null || parameters == null) {
            log("Adapter error. Null parameters");
            return;
        }

        String zoneId;
        if (!TextUtils.isEmpty(parameters.getThirdPartyAdPlacementId())) {
            zoneId = parameters.getThirdPartyAdPlacementId();
        } else {
            log("Could not find the required params in MaxAdapterResponseParameters");
            adapterListener.onAdViewAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION);
            return;
        }

        updateMuteState(parameters);
        updateUserConsent(parameters);

        mAdView = new HyBidAdView(activity, getSize(adFormat));
        mAdView.setMediation(true);
        mAdView.renderAd(parameters.getBidResponse(), new AdViewListener(adapterListener));
    }

    private static AdSize getSize(MaxAdFormat adFormat) {
        if (adFormat == MaxAdFormat.BANNER) {
            return AdSize.SIZE_320x50;
        } else if (adFormat == MaxAdFormat.LEADER) {
            return AdSize.SIZE_728x90;
        } else if (adFormat == MaxAdFormat.MREC) {
            return AdSize.SIZE_300x250;
        } else {
            throw new IllegalArgumentException("Invalid ad format: " + adFormat);
        }
    }

    // ---------------------------------- HyBidAdViewListener --------------------------------------
    private class AdViewListener
            implements HyBidAdView.Listener {
        private final MaxAdViewAdapterListener listener;

        private AdViewListener(final MaxAdViewAdapterListener listener) {
            this.listener = listener;
        }

        @Override
        public void onAdLoaded() {
            log("AdView ad loaded");
            listener.onAdViewAdLoaded(mAdView);
        }

        @Override
        public void onAdLoadFailed(final Throwable error) {
            log("AdView failed to load with error: " + error);
            MaxAdapterError adapterError = toMaxError(error);
            listener.onAdViewAdLoadFailed(adapterError);
        }

        @Override
        public void onAdImpression() {
            log("AdView did track impression");
            listener.onAdViewAdDisplayed();
        }

        @Override
        public void onAdClick() {
            log("AdView clicked");
            listener.onAdViewAdClicked();
        }
    }
}
