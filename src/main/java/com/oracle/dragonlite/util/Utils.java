package com.oracle.dragonlite.util;

import java.time.Duration;

public class Utils {
	public static void sleep(final long milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (InterruptedException ignored) {
		}
	}

	public static String getDurationSince(long startTime) {
		final long durationMillis = System.currentTimeMillis() - startTime;
		if (durationMillis < 1000) {
			return String.format("0.%03ds", durationMillis);
		} else {
			final Duration duration = Duration.ofMillis(durationMillis);
			return duration.toString().substring(2).replaceAll("(\\d[HMS])(?!$)", "$1 ").replaceAll("\\.\\d+", "").toLowerCase();
		}
	}
}
