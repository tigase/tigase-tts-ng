/*
 * Tigase Jabber/XMPP Server - TTS-NG
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package tigase.tests.http;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.httpfileupload.HttpFileUploadModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.tests.AbstractTest;
import tigase.tests.Mutex;
import tigase.tests.utils.Account;

import java.io.*;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

public class TestHTTPFileUpload
		extends AbstractTest {

	private static final String USER_PREFIX = "HFU_";

	private CloseableHttpClient httpClient;
	private Account user;
	private Jaxmpp userJaxmpp;

	private JID componentJid;
	private byte[] sendFileHash;
	private final AtomicReference<HttpFileUploadModule.Slot> slotRef = new AtomicReference<>();
	private final Mutex mutex = new Mutex();
	private File fileToSend;

	@BeforeClass
	public void setUp() throws Exception {
		httpClient = HttpClients.custom()
				.setDefaultRequestConfig(RequestConfig.custom().setSocketTimeout(5000).build())
				.build();
		user = createAccount().setLogPrefix(USER_PREFIX).build();
		userJaxmpp = user.createJaxmpp().setConfigurator(jaxmpp -> {
			jaxmpp.getModulesManager().register(new HttpFileUploadModule());
			return jaxmpp;
		}).setConnected(true).build();
	}

	@AfterClass
	public void cleanUp() throws Exception {
		httpClient.close();
	}

	@Test
	public void testComponentDiscovery() throws JaxmppException, InterruptedException {
		Map<JID, Long> components = new HashMap<>();
		userJaxmpp.getModule(HttpFileUploadModule.class)
				.findHttpUploadComponents(BareJID.bareJIDInstance(user.getJid().getDomain()), results -> {
					components.putAll(results);
					mutex.notify("upload:components:discovery:count:" + results.size());
					mutex.notify("upload:components:discovery");
				});
		mutex.waitFor(30 * 1000, "upload:components:discovery");
		assertTrue(mutex.isItemNotified("upload:components:discovery:count:1"));
		componentJid = components.keySet().iterator().next();
		assertNotNull(componentJid);
	}

	@Test(dependsOnMethods = {"testComponentDiscovery"})
	public void testSlotAllocation()
			throws IOException, JaxmppException, InterruptedException, NoSuchAlgorithmException {
		prepareFileToSend();

		userJaxmpp.getModule(HttpFileUploadModule.class)
				.requestUploadSlot(componentJid, "randomFile.txt", fileToSend.length(), null,
								   new HttpFileUploadModule.RequestUploadSlotHandler() {
									   @Override
									   public void onSuccess(HttpFileUploadModule.Slot slot) throws JaxmppException {
										   slotRef.set(slot);
										   mutex.notify("upload:slotRequest:success");
										   mutex.notify("upload:slotRequest");
									   }

									   @Override
									   public void onError(Stanza responseStanza, XMPPException.ErrorCondition error)
											   throws JaxmppException {
										   mutex.notify("upload:slotRequest:error");
										   mutex.notify("upload:slotRequest");
									   }

									   @Override
									   public void onTimeout() throws JaxmppException {
										   mutex.notify("upload:slotRequest:timeout");
										   mutex.notify("upload:slotRequest");
									   }
								   });

		mutex.waitFor(30 * 1000, "upload:slotRequest");
		assertTrue(mutex.isItemNotified("upload:slotRequest:success"));
		assertNotNull(slotRef.get());
		assertNotNull(slotRef.get().getGetUri());
		assertNotNull(slotRef.get().getPutUri());
	}

	@Test(dependsOnMethods = {"testSlotAllocation"})
	public void testFileUpload()
			throws JaxmppException, InterruptedException, IOException, NoSuchAlgorithmException, DigestException {

		HttpPut putRequest = new HttpPut(slotRef.get().getPutUri());
		slotRef.get().getPutHeaders().forEach(putRequest::setHeader);
		putRequest.setEntity(new FileEntity(fileToSend, ContentType.APPLICATION_OCTET_STREAM));

		HttpResponse uploadResponse = httpClient.execute(putRequest);
		assertEquals(201, uploadResponse.getStatusLine().getStatusCode());
	}

	@Test(dependsOnMethods = {"testFileUpload"})
	public void testFileDownload() throws IOException, NoSuchAlgorithmException {
		HttpGet getRequest = new HttpGet(slotRef.get().getGetUri());
		HttpResponse downloadResponse = httpClient.execute(getRequest);
		assertEquals(200, downloadResponse.getStatusLine().getStatusCode());

		byte[] recvFileHash = null;
		try (InputStream is = downloadResponse.getEntity().getContent()) {
			recvFileHash = calculateHash(is);
		}

		assertEquals(sendFileHash, recvFileHash);
	}

	private File prepareFileToSend() throws IOException, NoSuchAlgorithmException {
		int size = ThreadLocalRandom.current().nextInt(512 * 1024, 2 * 1024 * 1024);
		byte[] data = new byte[size];
		ThreadLocalRandom.current().nextBytes(data);
		fileToSend = File.createTempFile("hfu_", "tmp");
		fileToSend.createNewFile();
		fileToSend.deleteOnExit();
		try (OutputStream os = new FileOutputStream(fileToSend)) {
			os.write(data);
		}

		try (InputStream is = new FileInputStream(fileToSend)) {
			sendFileHash = calculateHash(is);
		}

		return fileToSend;
	}

	private static byte[] calculateHash(InputStream is) throws IOException, NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-512");
		byte[] tmp = new byte[1024];
		int read = 0;
		while ((read = is.read(tmp)) > -1) {
			md.update(tmp, 0, read);
		}
		return md.digest();
	}
}
