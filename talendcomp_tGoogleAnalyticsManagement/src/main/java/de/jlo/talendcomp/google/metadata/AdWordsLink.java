/**
 * Copyright 2015 Jan Lolling jan.lolling@gmail.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jlo.talendcomp.google.metadata;

public class AdWordsLink {
	
	private String Id;
	private String name;
	private String selfLink;
	private String webpropertyId;
	private String webpropertyName;
	private long accountId;
	private Boolean autoTaggingEnabled;
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
	public Boolean isAutoTaggingEnabled() {
		return autoTaggingEnabled;
	}
	public void setAutoTaggingEnabled(Boolean autoTaggingEnabled) {
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
