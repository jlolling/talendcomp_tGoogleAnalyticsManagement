package de.jlo.talendcomp.gamanage;

public class AdWordsLink {
	
	private String Id;
	private String name;
	private String selfLink;
	private String webpropertyId;
	private String webpropertyName;
	private long accountId;
	private boolean autoTaggingEnabled;
	private String customerId;
	private long profileId = 0;
	
	public String getId() {
		return Id;
	}
	public void setId(String id) {
		Id = id;
	}
	public String getWebpropertyId() {
		return webpropertyId;
	}
	public void setWebpropertyId(String webpropertyId) {
		this.webpropertyId = webpropertyId;
	}
	public String getWebpropertyName() {
		return webpropertyName;
	}
	public void setWebpropertyName(String webpropertyName) {
		this.webpropertyName = webpropertyName;
	}
	public long getAccountId() {
		return accountId;
	}
	public void setAccountId(long accountId) {
		this.accountId = accountId;
	}
	public boolean isAutoTaggingEnabled() {
		return autoTaggingEnabled;
	}
	public void setAutoTaggingEnabled(boolean autoTaggingEnabled) {
		this.autoTaggingEnabled = autoTaggingEnabled;
	}
	public String getCustomerId() {
		return customerId;
	}
	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}
	public long getProfileId() {
		return profileId;
	}
	public void setProfileId(Long profileId) {
		if (profileId != null) {
			this.profileId = profileId.longValue();
		}
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getSelfLink() {
		return selfLink;
	}
	public void setSelfLink(String selfLink) {
		this.selfLink = selfLink;
	}

}
