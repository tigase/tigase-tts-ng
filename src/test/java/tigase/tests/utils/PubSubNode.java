/*
 * PubSubNode.java
 *
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
package tigase.tests.utils;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.Objects;

/**
 * Created by andrzej on 22.04.2017.
 */
public class PubSubNode {

	private final PubSubManager manager;
	private final BareJID pubsubJid;
	private final String name;

	public PubSubNode(PubSubManager manager, BareJID pubsubJid, String name) {
		this.manager = manager;
		this.pubsubJid  = pubsubJid;
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public BareJID getPubsubJid() {
		return pubsubJid;
	}

	public void deleteNode() throws JaxmppException, InterruptedException {
		manager.deleteNode(this);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof PubSubNode) {
			PubSubNode n = (PubSubNode) obj;
			return getName().equals(n.getName()) && getPubsubJid().equals(n.getPubsubJid());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(pubsubJid, name);
	}

	@Override
	public String toString() {
		return "PubSubNode[jid=" + pubsubJid + ",node=" + name + "]";
	}

	public enum Type {
		leaf,
		collection
	}

}
