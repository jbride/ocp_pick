package com.ratwater.jbride;

public class OCPENV {

    public static final String AWS="aws";
    public static final String RAVELLO="ravello";
    public static final String RAVELLO_MASTER_PREFIX="master00-";

    private String type = AWS;
    private String guid = null;
    private String subdomainBase = "openshift.opentlc.com";
    private boolean loginAsAdmin = false;
    private String adminUserId = "opentlc-mgr";
    private String adminPasswd = null;
    private String userId = "user1";
    private String userPasswd = null;

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public String getSubdomainBase() {
        return subdomainBase;
    }
    public void setSubdomainBase(String sb) {
        this.subdomainBase = sb;
    }
    public boolean isLoginAsAdmin() {
        return loginAsAdmin;
    }
    public void setLoginAsAdmin(boolean loginAsAdmin) {
        this.loginAsAdmin = loginAsAdmin;
    }
    public String getAdminUserId() {
        return adminUserId;
    }
    public void setAdminUserId(String adminUser) {
        this.adminUserId = adminUser;
    }
    public String getAdminPasswd() {
        return adminPasswd;
    }
    public void setAdminPasswd(String x) {
        this.adminPasswd = x;
    }
    public String getUserId() {
		return userId;
    }
    public void setUserId(String x) {
		userId = x;
	}
	public String getUserPasswd() {
		return userPasswd;
    }
    public void setUserPasswd(String x) {
		userPasswd = x;
	}
    public String toString() {
        return "OCPEnv [guid=" + guid + ", adminUserId=" + adminUserId + ", loginAsAdmin=" + loginAsAdmin
                + ", userId=" + userId + ", type=" + type + ", subdomainBase=" +subdomainBase + "]";
    }
}