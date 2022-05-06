package com.oracle.dragonlite.util;

import com.oracle.dragonlite.exception.DLException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Based on https://stackoverflow.com/questions/2939218/getting-the-external-ip-address-in-java
 */
public final class PublicIPv4Retriever {

	private static final Pattern IPV4_PATTERN = Pattern.compile("^(<html><head><title>Current IP Check</title></head><body>Current IP Address: )?((([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5]))(</body></html>\\n)?$");

	private static final String[] IPV4_SERVICES = {
			"http://checkip.dyndns.org/",
			"http://checkip.amazonaws.com/",
			"https://ipv4.icanhazip.com/",
			"http://myexternalip.com/raw",
			"http://ipecho.net/plain",
			"http://www.trackip.net/ip"
	};

	public static String get() {
		List<Callable<String>> callables = new ArrayList<>();
		for (String ipService : IPV4_SERVICES) {
			callables.add(() -> get(ipService));
		}

		final ExecutorService executorService = Executors.newCachedThreadPool();
		try {
			return executorService.invokeAny(callables);
		}
		catch (Exception e) {
			throw new DLException(DLException.UNKNOWN_CURRENT_IP_ADDRESS, e);
		}
		finally {
			executorService.shutdown();
		}
	}

	private static String get(String url) throws IOException {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL(url).openStream()))) {
			final String ip = in.readLine();
			final Matcher m = IPV4_PATTERN.matcher(ip);
			if (m.matches()) {
				return m.group(2);
			}
			else {
				throw new IOException("Invalid IPv4 address: " + ip);
			}
		}
	}

	//private static final Pattern IPV4_PATTERN = Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
}
