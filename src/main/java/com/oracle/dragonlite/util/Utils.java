package com.oracle.dragonlite.util;

public class Utils {
	public static void sleep(final long milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (InterruptedException ignored) {
		}
	}
}
