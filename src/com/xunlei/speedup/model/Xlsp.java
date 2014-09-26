/**
 * Xlsp.java V1.0 2014-7-7 下午5:07:12
 *
 * Copyright Talkweb Information System Co. ,Ltd. All rights reserved.
 *
 * Modification history(By Time Reason):
 *
 * Description:
 */

package com.xunlei.speedup.model;

public class Xlsp {

    // url，产品id，产品版本号，miui版本号，小米账号id(如果能拿到)，手机号码(如果能拿到)

    String downUrl;
    // 产品id，产品版本号
    String appId;
    String appVersion;
    // miui版本号，小米账号id
    String miuiId;
    String miuiVersion;
    // 手机号码(如果能拿到)
    String phoneNum;

    String peerid;

    public String getDownUrl() {
        return downUrl;
    }

    public void setDownUrl(String downUrl) {
        this.downUrl = downUrl;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getMiuiId() {
        return miuiId;
    }

    public void setMiuiId(String miuiId) {
        this.miuiId = miuiId;
    }

    public String getMiuiVersion() {
        return miuiVersion;
    }

    public void setMiuiVersion(String miuiVersion) {
        this.miuiVersion = miuiVersion;
    }

    public String getPhoneNum() {
        return phoneNum;
    }

    public void setPhoneNum(String phoneNum) {
        this.phoneNum = phoneNum;
    }

    public String getPeerid() {
        return peerid;
    }

    public void setPeerid(String peerid) {
        this.peerid = peerid;
    }

}
