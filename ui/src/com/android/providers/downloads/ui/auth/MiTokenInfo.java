package com.android.providers.downloads.ui.auth;

public class MiTokenInfo {

	public String userId;

	public String code;

	public String state;

	public String tokenType;

	public String macKey;

	public String macAlgorithm;

	public String expiresIn;

	public String scope;

	public String accessToken;

	public String refreshToken;

	public String error;

	public String errorDescription;

	@Override
	public String toString() {
		return "accessToken=" + accessToken + ",expiresIn=" + expiresIn + ",scope=" + scope + ",state=" + state + ",tokenType=" + tokenType + ",macKey=" + macKey + ",macAlogorithm=" + macAlgorithm;
	}
}
