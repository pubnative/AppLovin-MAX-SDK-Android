package com.applovin.mediation.adapters;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import com.applovin.impl.sdk.utils.BundleUtils;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.adapter.MaxAdapterError;
import com.applovin.mediation.adapter.MaxNativeAdAdapter;
import com.applovin.mediation.adapter.listeners.MaxNativeAdAdapterListener;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.mediation.nativeAds.MaxNativeAd;
import com.applovin.mediation.nativeAds.MaxNativeAdView;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkUtils;

import net.pubnative.lite.sdk.HyBid;
import net.pubnative.lite.sdk.models.NativeAd;
import net.pubnative.lite.sdk.request.HyBidNativeAdRequest;
import net.pubnative.lite.sdk.utils.Logger;

import java.lang.ref.WeakReference;

public class VerveMediationNativeAdapter extends VerveMediationBaseAdapter implements MaxNativeAdAdapter {
    private NativeAd mNativeAd;

    public VerveMediationNativeAdapter(AppLovinSdk appLovinSdk) {
        super(appLovinSdk);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mNativeAd != null) {
            mNativeAd.stopTracking();
            mNativeAd = null;
        }
    }

    @Override
    public void loadNativeAd(MaxAdapterResponseParameters parameters, Activity activity, MaxNativeAdAdapterListener adapterListener) {
        log("Loading native ad");

        if (adapterListener == null || parameters == null) {
            log("Adapter error. Null parameters");
            return;
        }

        String zoneId;

        if (!TextUtils.isEmpty(parameters.getThirdPartyAdPlacementId())) {
            zoneId = parameters.getThirdPartyAdPlacementId();
        } else {
            log("Could not find the required params in MaxAdapterResponseParameters");
            adapterListener.onNativeAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION);
            return;
        }

        updateMuteState(parameters);
        updateUserConsent(parameters);

        HyBidNativeAdRequest nativeAdRequest = new HyBidNativeAdRequest();
        nativeAdRequest.setMediation(true);
        nativeAdRequest.setPreLoadMediaAssets(true);
        nativeAdRequest.load(zoneId, new NativeAdListener(activity, parameters, adapterListener));
    }

    private class NativeAdListener implements HyBidNativeAdRequest.RequestListener, NativeAd.Listener {
        final Bundle serverParameters;
        final WeakReference<Activity> activityRef;
        final MaxNativeAdAdapterListener listener;

        public NativeAdListener(final Activity activity, final MaxAdapterResponseParameters parameters, final MaxNativeAdAdapterListener listener) {
            serverParameters = parameters.getServerParameters();
            activityRef = new WeakReference<>(activity);

            this.listener = listener;
        }

        @Override
        public void onRequestSuccess(NativeAd ad) {
            mNativeAd = ad;

            String templateName = BundleUtils.getString("template", "", serverParameters);
            boolean isTemplateAd = AppLovinSdkUtils.isValidString(templateName);

            if (!hasRequiredAssets(isTemplateAd, mNativeAd)) {
                e("Native ad (" + mNativeAd + ") does not have required assets.");
                listener.onNativeAdLoadFailed(MaxAdapterError.MISSING_REQUIRED_NATIVE_AD_ASSETS);
                return;
            }

            processNativeAd();
        }

        @Override
        public void onRequestFail(Throwable throwable) {
            if (listener != null) {
                listener.onNativeAdLoadFailed(toMaxError(throwable));
            }
        }

        @Override
        public void onAdImpression(NativeAd ad, View view) {
            if (listener != null) {
                listener.onNativeAdDisplayed(null);
            }
        }

        @Override
        public void onAdClick(NativeAd ad, View view) {
            if (listener != null) {
                listener.onNativeAdClicked();
            }
        }

        private void processNativeAd() {
            final Activity activity = activityRef.get();
            if (activity == null) {
                log("Native ad failed to load: activity reference is null when ad is loaded");
                listener.onNativeAdLoadFailed(MaxAdapterError.INVALID_LOAD_STATE);
                return;
            }

            AppLovinSdkUtils.runOnUiThread(() -> {
                if (listener != null && mNativeAd != null) {

                    MaxNativeAd.Builder builder = new MaxNativeAd.Builder();
                    builder.setAdFormat(MaxAdFormat.NATIVE);

                    if (mNativeAd.getBannerBitmap() != null) {
                        ImageView bannerView = new ImageView(activity);
                        bannerView.setImageBitmap(mNativeAd.getBannerBitmap());
                        builder.setMediaView(bannerView);
                    }

                    if (mNativeAd.getIconBitmap() != null) {
                        ImageView iconView = new ImageView(activity);
                        iconView.setImageBitmap(mNativeAd.getIconBitmap());
                        builder.setIconView(iconView);
                    }

                    if (!TextUtils.isEmpty(mNativeAd.getTitle())) {
                        builder.setTitle(mNativeAd.getTitle());
                    }

                    if (!TextUtils.isEmpty(mNativeAd.getDescription())) {
                        builder.setBody(mNativeAd.getDescription());
                    }

                    if (!TextUtils.isEmpty(mNativeAd.getCallToActionText())) {
                        builder.setCallToAction(mNativeAd.getCallToActionText());
                    }

                    View contentInfo = mNativeAd.getContentInfo(activity);
                    if (contentInfo != null) {
                        builder.setOptionsView(contentInfo);
                    }

                    MaxNativeAd maxNativeAd = new NativeAdListener.MaxVerveNativeAd(builder);
                    listener.onNativeAdLoaded(maxNativeAd, null);
                }
            });
        }

        private boolean hasRequiredAssets(final boolean isTemplateAd, final NativeAd assets) {
            if (isTemplateAd) {
                return AppLovinSdkUtils.isValidString(assets.getTitle());
            } else {
                return AppLovinSdkUtils.isValidString(assets.getTitle())
                        && AppLovinSdkUtils.isValidString(assets.getCallToActionText())
                        && AppLovinSdkUtils.isValidString(assets.getBannerUrl());
            }
        }

        private class MaxVerveNativeAd extends MaxNativeAd {
            private MaxVerveNativeAd(Builder builder) {
                super(builder);
            }

            @Override
            public void prepareViewForInteraction(MaxNativeAdView maxNativeAdView) {
                if (mNativeAd == null) {
                    e("Failed to register native ad view for interaction. Verve native ad is null");
                } else {
                    mNativeAd.startTracking(maxNativeAdView, NativeAdListener.this);
                }
            }
        }
    }
}
