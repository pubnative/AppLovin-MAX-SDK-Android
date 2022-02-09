package com.applovin.mediation.adapters;

import android.app.Activity;
import android.os.Bundle;

import com.applovin.mediation.adapter.MaxAdapterError;
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkConfiguration;

import net.pubnative.lite.sdk.HyBid;
import net.pubnative.lite.sdk.HyBidError;
import net.pubnative.lite.sdk.vpaid.enums.AudioState;

import java.util.concurrent.atomic.AtomicBoolean;

public class VerveMediationBaseAdapter extends MediationAdapterBase {
    private static final AtomicBoolean initialized = new AtomicBoolean();
    private static InitializationStatus status;

    public VerveMediationBaseAdapter(AppLovinSdk appLovinSdk) {
        super(appLovinSdk);
    }

    @Override
    public void initialize(MaxAdapterInitializationParameters maxAdapterInitializationParameters, Activity activity, OnCompletionListener onCompletionListener) {

    }

    @Override
    public String getSdkVersion() {
        return HyBid.getSDKVersionInfo();
    }

    @Override
    public String getAdapterVersion() {
        return "2.12.0.0";
    }

    @Override
    public void onDestroy() {

    }

    protected void updateUserConsent(final MaxAdapterResponseParameters parameters) {
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

    protected static void updateMuteState(final MaxAdapterResponseParameters parameters) {
        Bundle serverParameters = parameters.getServerParameters();
        if (serverParameters.containsKey("is_muted")) {
            if (serverParameters.getBoolean("is_muted")) {
                HyBid.setVideoAudioStatus(AudioState.MUTED);
            } else {
                HyBid.setVideoAudioStatus(AudioState.DEFAULT);
            }
        }
    }

    protected static MaxAdapterError toMaxError(Throwable verveError) {
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
}
