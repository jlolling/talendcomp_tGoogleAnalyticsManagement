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
package de.jlo.talendcomp.gamanage;

import java.util.List;

public abstract class UserPermission {
	
	private String email;
	private String localPermissions;
	private String effectivePermissions;
	
	public String getLocalPermissions() {
		return localPermissions;
	}
	
	public void setLocalPermissions(List<String> permissionList) {
		if (permissionList != null && permissionList.isEmpty() == false) {
			StringBuilder sb = new StringBuilder();
			boolean firstLoop = true;
			for (String p : permissionList) {
				if (firstLoop) {
					firstLoop = false;
				} else {
					sb.append(",");
				}
				sb.append(p);
			}
			this.localPermissions = sb.toString();
		}
	}
	
	public String getEffectivePermissions() {
		return effectivePermissions;
	}
	
	public void setEffectivePermissions(List<String> permissionList) {
		if (permissionList != null && permissionList.isEmpty() == false) {
			StringBuilder sb = new StringBuilder();
			boolean firstLoop = true;
			for (String p : permissionList) {
				if (firstLoop) {
					firstLoop = false;
				} else {
					sb.append(",");
				}
				sb.append(p);
			}
			this.effectivePermissions = sb.toString();
		}
	}

	public String getEmail() {
		return email;
	}
	
	public void setEmail(String email) {
		this.email = email;
	}
	
}
