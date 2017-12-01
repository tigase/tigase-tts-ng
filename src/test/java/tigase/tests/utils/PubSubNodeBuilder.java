/*
 * PubSubNodeBuilder.java
 *
 * Tigase TTS-NG
 * Copyright (C) 2015-2017 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License,
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
 */
package tigase.tests.utils;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.XDataType;
import tigase.jaxmpp.j2se.Jaxmpp;

import java.util.function.Consumer;

/**
 * Created by andrzej on 22.04.2017.
 */
public class PubSubNodeBuilder {

	private final PubSubManager manager;
	private final String name;
	private Consumer<JabberDataElement> configurator;
	private boolean ifNotExists;
	private Jaxmpp jaxmpp;
	private PubSubNode.Type nodeType = PubSubNode.Type.leaf;
	private String parentCollection;
	private BareJID pubSubJid;
	private boolean replaceIfExists;

	protected PubSubNodeBuilder(PubSubManager manager, String node) {
		this.manager = manager;
		this.name = node;
	}

	public PubSubNodeBuilder setConfigurator(Consumer<JabberDataElement> configurator) {
		this.configurator = configurator;
		return this;
	}

	public PubSubNode build() throws JaxmppException, InterruptedException {
		JabberDataElement config = new JabberDataElement(XDataType.submit);
		config.addTextSingleField("pubsub#title", name);
		if (nodeType == PubSubNode.Type.collection) {
			config.addTextSingleField("pubsub#node_type", "collection");
		} else {
			config.addTextSingleField("pubsub#max_items", "100");
		}
		if (parentCollection != null) {
			config.addTextSingleField("pubsub#collection", parentCollection);
		}

		if (configurator != null) {
			configurator.accept(config);
		}
		return manager.createNode(this, config);
	}

	public PubSubNodeBuilder setParentCollection(String parentCollection) {
		this.parentCollection = parentCollection;
		return this;
	}

	public PubSubNodeBuilder setNodeType(PubSubNode.Type nodeType) {
		this.nodeType = nodeType;
		return this;
	}

	protected BareJID getPubSubJid() {
		return pubSubJid;
	}

	public void setPubSubJid(BareJID pubsubJid) {
		this.pubSubJid = pubsubJid;
	}

	protected String getName() {
		return name;
	}

	protected Jaxmpp getJaxmpp() {
		return jaxmpp;
	}

	public PubSubNodeBuilder setJaxmpp(Jaxmpp jaxmpp) {
		this.jaxmpp = jaxmpp;
		if (pubSubJid == null) {
			this.pubSubJid = BareJID.bareJIDInstance(
					"pubsub." + jaxmpp.getSessionObject().getUserBareJid().getDomain());
		}
		return this;
	}

	protected boolean getIfNotExists() {
		return ifNotExists;
	}

	public PubSubNodeBuilder setIfNotExists(boolean ifNotExists) {
		this.ifNotExists = ifNotExists;
		return this;
	}

	protected boolean getReplaceIfExists() {
		return replaceIfExists;
	}

	public PubSubNodeBuilder setReplaceIfExists(boolean replaceIfExists) {
		this.replaceIfExists = replaceIfExists;
		return this;
	}
}
