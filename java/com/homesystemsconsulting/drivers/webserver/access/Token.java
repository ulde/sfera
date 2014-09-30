package com.homesystemsconsulting.drivers.webserver.access;

import java.util.UUID;

import com.homesystemsconsulting.drivers.webserver.HttpRequestHeader;

public class Token {
	
	public static int maxAgeSeconds;
	
	private final String uuid;
	private final User user;
	private final String userAgent;
	private final long expirationTime;

	/**
	 * 
	 * @param user
	 * @param httpRequestHeader
	 */
	public Token(User user, HttpRequestHeader httpRequestHeader) {
		this.uuid = UUID.randomUUID().toString();
		this.user = user;
		this.userAgent = httpRequestHeader.getUserAgent();
		this.expirationTime = System.currentTimeMillis() + (maxAgeSeconds * 1000);
	}

	/**
	 * 
	 * @return
	 */
	public String getUUID() {
		return uuid;
	}
	
	/**
	 * 
	 * @return
	 */
	public User getUser() {
		return user;
	}
	
	/**
	 * 
	 * @param httpRequestHeader
	 * @return
	 */
	public boolean match(HttpRequestHeader httpRequestHeader) {
		return this.userAgent == null || this.userAgent.equals(httpRequestHeader.getUserAgent());
	}

	/**
	 * 
	 * @return
	 */
	public boolean isExpired() {
		return System.currentTimeMillis() > this.expirationTime;
	}
}
