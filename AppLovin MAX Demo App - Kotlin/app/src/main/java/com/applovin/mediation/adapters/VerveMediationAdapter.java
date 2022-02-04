package com.applovin.mediation.adapters;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import com.applovin.impl.sdk.utils.BundleUtils;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.adapter.MaxAdViewAdapter;
import com.applovin.mediation.adapter.MaxAdapterError;
import com.applovin.mediation.adapter.MaxInterstitialAdapter;
import com.applovin.mediation.adapter.MaxRewardedAdapter;
import com.applovin.mediation.adapter.MaxSignalProvider;
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxNativeAdAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxSignalCollectionListener;
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.mediation.adapter.parameters.MaxAdapterSignalCollectionParameters;
import com.applovin.mediation.nativeAds.MaxNativeAd;
import com.applovin.mediation.nativeAds.MaxNativeAdView;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkConfiguration;
import com.applovin.sdk.AppLovinSdkUtils;

import net.pubnative.lite.sdk.HyBid;
import net.pubnative.lite.sdk.HyBidError;
import net.pubnative.lite.sdk.interstitial.HyBidInterstitialAd;
import net.pubnative.lite.sdk.models.AdSize;
import net.pubnative.lite.sdk.models.NativeAd;
import net.pubnative.lite.sdk.request.HyBidNativeAdRequest;
import net.pubnative.lite.sdk.rewarded.HyBidRewardedAd;
import net.pubnative.lite.sdk.utils.PNBitmapDownloader;
import net.pubnative.lite.sdk.views.HyBidAdView;
import net.pubnative.lite.sdk.vpaid.enums.AudioState;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

public class VerveMediationAdapter
        extends MediationAdapterBase
        implements MaxInterstitialAdapter, MaxRewardedAdapter, MaxAdViewAdapter, MaxSignalProvider {
    private static final AtomicBoolean initialized = new AtomicBoolean();
    private static InitializationStatus status;

    private HyBidInterstitialAd interstitialAd;
    private HyBidRewardedAd rewardedAd;
    private HyBidAdView adViewAd;
    private NativeAd nativeAd;

    // Explicit default constructor declaration
    public VerveMediationAdapter(final AppLovinSdk sdk) {
        super(sdk);
    }

    @Override
    public String getSdkVersion() {
        return HyBid.getSDKVersionInfo();
    }

    @Override
    public String getAdapterVersion() {
        return "2.10.0.0";
    }

    @Override
    public void initialize(final MaxAdapterInitializationParameters parameters, final Activity activity, final OnCompletionListener onCompletionListener) {
        if (initialized.compareAndSet(false, true)) {
            status = InitializationStatus.INITIALIZING;

            final String appToken = parameters.getServerParameters().getString("app_token", "");
            log("Initializing Verve SDK with app token: " + appToken + "...");

            if (parameters.isTesting()) {
                HyBid.setTestMode(true);
            }

            HyBid.setLocationUpdatesEnabled(false);
            HyBid.initialize(appToken, activity.getApplication());

            if (HyBid.isInitialized()) {
                log("Verve SDK initialized");
                status = InitializationStatus.INITIALIZED_SUCCESS;
            } else {
                log("Verve SDK failed to initialize");
                status = InitializationStatus.INITIALIZED_FAILURE;
            }

            onCompletionListener.onCompletion(status, null);
        } else {
            log("Verve attempted to initialize already - marking initialization as " + status);
            onCompletionListener.onCompletion(status, null);
        }
    }

    @Override
    public void onDestroy() {
        if (interstitialAd != null) {
            interstitialAd.destroy();
            interstitialAd = null;
        }

        if (rewardedAd != null) {
            rewardedAd.destroy();
            rewardedAd = null;
        }

        if (adViewAd != null) {
            adViewAd.destroy();
            adViewAd = null;
        }

        if (nativeAd != null) {
            nativeAd.stopTracking();
            nativeAd = null;
        }
    }

    @Override
    public void collectSignal(final MaxAdapterSignalCollectionParameters parameters, final Activity activity, final MaxSignalCollectionListener callback) {
        log("Collecting Signal...");

        String signal = HyBid.getCustomRequestSignalData();
        callback.onSignalCollected(signal);
    }

    @Override
    public void loadInterstitialAd(final MaxAdapterResponseParameters parameters, final Activity activity, final MaxInterstitialAdapterListener listener) {
        log("Loading interstitial ad");

        updateMuteState(parameters);
        updateUserConsent(parameters);

        interstitialAd = new HyBidInterstitialAd(activity, new InterstitialListener(listener));
        interstitialAd.prepareAd(parameters.getBidResponse());
    }

    @Override
    public void showInterstitialAd(final MaxAdapterResponseParameters parameters, final Activity activity, final MaxInterstitialAdapterListener listener) {
        log("Showing interstitial ad...");

        if (interstitialAd.isReady()) {
            interstitialAd.show();
        } else {
            log("Interstitial ad not ready");
            listener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_NOT_READY);
        }
    }

    @Override
    public void loadRewardedAd(final MaxAdapterResponseParameters parameters, final Activity activity, final MaxRewardedAdapterListener listener) {
        log("Loading rewarded ad");

        updateMuteState(parameters);
        updateUserConsent(parameters);

        rewardedAd = new HyBidRewardedAd(activity, new RewardedListener(listener));
        rewardedAd.prepareAd(parameters.getBidResponse());
    }

    @Override
    public void showRewardedAd(final MaxAdapterResponseParameters parameters, final Activity activity, final MaxRewardedAdapterListener listener) {
        log("Showing rewarded ad...");

        if (rewardedAd.isReady()) {
            configureReward(parameters);
            rewardedAd.show();
        } else {
            log("Rewarded ad not ready");
            listener.onRewardedAdDisplayFailed(MaxAdapterError.AD_NOT_READY);
        }
    }

    @Override
    public void loadAdViewAd(final MaxAdapterResponseParameters parameters, final MaxAdFormat adFormat, final Activity activity, final MaxAdViewAdapterListener listener) {
        log("Loading " + adFormat.getLabel() + " ad view ad...");

        updateMuteState(parameters);
        updateUserConsent(parameters);

        adViewAd = new HyBidAdView(activity, getSize(adFormat));
        adViewAd.renderAd(parameters.getBidResponse(), new AdViewListener(listener));
    }

    @Override
    public void loadNativeAd(MaxAdapterResponseParameters maxAdapterResponseParameters, Activity activity, MaxNativeAdAdapterListener maxNativeAdAdapterListener) {
        HyBidNativeAdRequest nativeAdRequest = new HyBidNativeAdRequest();
        nativeAdRequest.setPreLoadMediaAssets(true);
        nativeAdRequest.load("7", new NativeAdListener(activity, maxAdapterResponseParameters, maxNativeAdAdapterListener));
    }

    private void updateUserConsent(final MaxAdapterResponseParameters parameters) {
        if (getWrappingSdk().getConfiguration().getConsentDialogState() == AppLovinSdkConfiguration.ConsentDialogState.APPLIES) {
            Boolean hasUserConsent = parameters.hasUserConsent();
            if (hasUserConsent != null) {
                HyBid.getUserDataManager().setIABGDPRConsentString(hasUserConsent ? "1" : "0");
            } else { /* Don't do anything if huc value not set */ }
        }

        Boolean isAgeRestrictedUser = parameters.isAgeRestrictedUser();
        if (isAgeRestrictedUser != null) {
            HyBid.setCoppaEnabled(isAgeRestrictedUser);
        }

        if (AppLovinSdk.VERSION_CODE >= 91100) {
            Boolean isDoNotSell = parameters.isDoNotSell();
            if (isDoNotSell != null && isDoNotSell) {
                HyBid.getUserDataManager().setIABUSPrivacyString("1NYN");
            } else {
                HyBid.getUserDataManager().removeIABUSPrivacyString();
            }
        }
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

    private static void updateMuteState(final MaxAdapterResponseParameters parameters) {
        Bundle serverParameters = parameters.getServerParameters();
        if (serverParameters.containsKey("is_muted")) {
            if (serverParameters.getBoolean("is_muted")) {
                HyBid.setVideoAudioStatus(AudioState.MUTED);
            } else {
                HyBid.setVideoAudioStatus(AudioState.DEFAULT);
            }
        }
    }

    private static MaxAdapterError toMaxError(Throwable verveError) {
        MaxAdapterError adapterError = MaxAdapterError.UNSPECIFIED;
        if (verveError instanceof HyBidError) {
            HyBidError hyBidError = (HyBidError) verveError;
            switch (hyBidError.getErrorCode()) {
                case NO_FILL:
                case NULL_AD:
                    adapterError = MaxAdapterError.NO_FILL;
                    break;
                case INVALID_ASSET:
                case UNSUPPORTED_ASSET:
                    adapterError = MaxAdapterError.INVALID_CONFIGURATION;
                    break;
                case PARSER_ERROR:
                case SERVER_ERROR_PREFIX:
                    adapterError = MaxAdapterError.SERVER_ERROR;
                    break;
                case INVALID_AD:
                case INVALID_ZONE_ID:
                case INVALID_SIGNAL_DATA:
                    adapterError = MaxAdapterError.BAD_REQUEST;
                    break;
                case NOT_INITIALISED:
                    adapterError = MaxAdapterError.NOT_INITIALIZED;
                    break;
                case AUCTION_NO_AD:
                case ERROR_RENDERING_BANNER:
                case ERROR_RENDERING_INTERSTITIAL:
                case ERROR_RENDERING_REWARDED:
                    adapterError = MaxAdapterError.AD_NOT_READY;
                    break;
                case INTERNAL_ERROR:
                    adapterError = MaxAdapterError.INTERNAL_ERROR;
                    break;
            }
        }
        return new MaxAdapterError(adapterError.getErrorCode(), adapterError.getErrorMessage(), 0, verveError.getMessage());
    }

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

    private class AdViewListener
            implements HyBidAdView.Listener {
        private final MaxAdViewAdapterListener listener;

        private AdViewListener(final MaxAdViewAdapterListener listener) {
            this.listener = listener;
        }

        @Override
        public void onAdLoaded() {
            log("AdView ad loaded");
            listener.onAdViewAdLoaded(adViewAd);
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
            nativeAd = ad;

            String templateName = BundleUtils.getString("template", "", serverParameters);
            boolean isTemplateAd = AppLovinSdkUtils.isValidString(templateName);

            if (!hasRequiredAssets(isTemplateAd, nativeAd)) {
                e("Native ad (" + nativeAd + ") does not have required assets.");
                listener.onNativeAdLoadFailed(new MaxAdapterError(-5400, "Missing Native Ad Assets"));
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
                if (listener != null && nativeAd != null) {

                    MaxNativeAd.Builder builder = new MaxNativeAd.Builder();
                    builder.setAdFormat(MaxAdFormat.NATIVE);

                    if (nativeAd.getBannerBitmap() != null) {
                        ImageView bannerView = new ImageView(activity);
                        bannerView.setImageBitmap(nativeAd.getBannerBitmap());
                        builder.setMediaView(bannerView);
                    }

                    if (nativeAd.getIconBitmap() != null) {
                        ImageView iconView = new ImageView(activity);
                        iconView.setImageBitmap(nativeAd.getIconBitmap());
                        builder.setIconView(iconView);
                    }

                    if (!TextUtils.isEmpty(nativeAd.getTitle())) {
                        builder.setTitle(nativeAd.getTitle());
                    }

                    if (!TextUtils.isEmpty(nativeAd.getDescription())) {
                        builder.setBody(nativeAd.getDescription());
                    }

                    if (!TextUtils.isEmpty(nativeAd.getCallToActionText())) {
                        builder.setCallToAction(nativeAd.getCallToActionText());
                    }

                    View contentInfo = nativeAd.getContentInfo(activity);
                    if (contentInfo != null) {
                        builder.setOptionsView(contentInfo);
                    }

                    MaxNativeAd maxNativeAd = new MaxVerveNativeAd(builder);
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
                if (nativeAd == null) {
                    e("Failed to register native ad view for interaction. Verve native ad is null");
                } else {
                    nativeAd.startTracking(maxNativeAdView, NativeAdListener.this);
                }
            }
        }
    }
}