module oci.oracle.adbs {
	requires oci.java.sdk.database;
	requires oci.java.sdk.identity;
	requires oci.java.sdk.limits;
	requires com.google.common;
	requires oci.java.sdk.common;
	requires com.fasterxml.jackson.databind;
	requires java.net.http;
	requires oci.java.sdk.workrequests;
	requires java.annotation;
	requires org.slf4j;

	opens com.oracle.dragonlite.rest to com.fasterxml.jackson.databind;
}