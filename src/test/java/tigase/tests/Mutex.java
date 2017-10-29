/*
 * Mutex.java
 *
 * Tigase Jabber/XMPP Server - TTS-NG
 * Copyright (C) 2004-2015 "Tigase, Inc." <office@tigase.com>
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
package tigase.tests;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static tigase.TestLogger.log;

public class Mutex {

	private final HashSet<String> addedItems = new HashSet<String>();
	private final Object locker = new Object();
	private boolean forceFinished;

	public void clear() {
		synchronized (locker) {
			addedItems.clear();
		}
	}

	public boolean isItemNotified(String item) {
		synchronized (locker) {
			log("[Mutex] isItemNotified: " + item + " :: " + addedItems.contains(item));
			return addedItems.contains(item);
		}
	}

	public void notify(String... itemName) {
		synchronized (locker) {
			List<String> ii = Arrays.asList(itemName);
			log("[Mutex] notify: " + ii);
			addedItems.addAll(ii);
			locker.notify();
		}
	}

	public void notifyForce() {
		synchronized (locker) {
			this.forceFinished = true;
			locker.notify();
		}
	}

	public boolean waitFor(long timeout, String... items) throws InterruptedException {

		final Collection<String> waitForItems = new HashSet<String>(Arrays.asList(items));

		final long start = System.currentTimeMillis();
		final long end = start + (timeout * 3);

		synchronized (locker) {
			forceFinished = false;
			boolean done;
			while (!forceFinished && !(done = isDone(waitForItems))) {
				final long now = System.currentTimeMillis();
				long t = end - now;
				if (t < 1) {
					log("[Mutex] timeout. Not received " + showMissing(items));
					return false;
				}
				log("[Mutex] waiting for: " + waitForItems);

				locker.wait(t);
			}

			log("[Mutex] " + (forceFinished ? "forced to stop." : "received everything."));
			return true;
		}
	}

	private boolean isDone(Collection<String> waitForItems) {

		return addedItems.containsAll(waitForItems);
	}

	private Collection<String> showMissing(String[] items) {
		HashSet<String> x = new HashSet<String>(Arrays.asList(items));
		x.removeAll(addedItems);
		return x;
	}

}
