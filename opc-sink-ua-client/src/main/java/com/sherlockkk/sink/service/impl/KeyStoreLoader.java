/*
 * Copyright (c) 2019 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.sherlockkk.sink.service.impl;

import com.sherlockkk.sink.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.regex.Pattern;

public class KeyStoreLoader {

	private static final Pattern IP_ADDR_PATTERN = Pattern.compile(
			"^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

	private static final String CLIENT_ALIAS = "client-ai";
	private static final char[] PASSWORD = "password".toCharArray();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private X509Certificate clientCertificate;
	private KeyPair clientKeyPair;

	public KeyStoreLoader load(Path baseDir) throws Exception {
		KeyStore keyStore = KeyStore.getInstance("PKCS12");

		Path serverKeyStore = baseDir.resolve("opc-sink-client.pfx");

		logger.info("Loading KeyStore at {}", serverKeyStore);

		if (!Files.exists(serverKeyStore)) {
			keyStore.load(null, PASSWORD);

			KeyPair keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);

			SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
					.setCommonName("OPC-SINK OPC-UA Client")
					.setOrganization("BONC")
					.setOrganizationalUnit("dev")
					.setLocalityName("Beijing")
					.setStateName("Beijing")
					.setCountryCode("CN")
					.setApplicationUri("urn:opc-sink")
					.addDnsName("localhost")
					.addIpAddress("127.0.0.1");

			// Get as many hostnames and IP addresses as we can listed in the certificate.
			for (String hostname : HostnameUtil.getHostnames("0.0.0.0")) {
				if (IP_ADDR_PATTERN.matcher(hostname).matches()) {
					builder.addIpAddress(hostname);
				} else {
					builder.addDnsName(hostname);
				}
			}

			X509Certificate certificate = builder.build();

			keyStore.setKeyEntry(CLIENT_ALIAS, keyPair.getPrivate(), PASSWORD, new X509Certificate[]{certificate});
			try (OutputStream out = Files.newOutputStream(serverKeyStore)) {
				keyStore.store(out, PASSWORD);
			}
		} else {
			try (InputStream in = Files.newInputStream(serverKeyStore)) {
				keyStore.load(in, PASSWORD);
			}
		}

		Key serverPrivateKey = keyStore.getKey(CLIENT_ALIAS, PASSWORD);
		if (serverPrivateKey instanceof PrivateKey) {
			clientCertificate = (X509Certificate) keyStore.getCertificate(CLIENT_ALIAS);
			PublicKey serverPublicKey = clientCertificate.getPublicKey();
			clientKeyPair = new KeyPair(serverPublicKey, (PrivateKey) serverPrivateKey);
		}

		return this;
	}

	public X509Certificate getClientCertificate() {
		return clientCertificate;
	}

	public KeyPair getClientKeyPair() {
		return clientKeyPair;
	}

}
