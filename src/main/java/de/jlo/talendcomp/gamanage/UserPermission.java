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
