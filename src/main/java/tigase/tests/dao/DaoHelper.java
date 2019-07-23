/*
 * Tigase Jabber/XMPP Server
 *  Copyright (C) 2004-2019 "Tigase, Inc." <office@tigase.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License,
 *  or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. Look for COPYING file in the top folder.
 *  If not, see http://www.gnu.org/licenses/.
 *
 */

package tigase.tests.dao;

import tigase.xml.DefaultElementFactory;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DaoHelper {

	private final static SimpleParser simpleParser = new SimpleParser();

	static Optional<LocalDate> geFinishedTimeFromData(Element data) {
		final List<Element> suites = data.findChildren(el -> "suite" == el.getName());
		return suites.stream()
				.map(suite -> suite.getAttributeStaticStr("finished-at"))
				.map(DaoHelper::parseFinishedAt)
				.filter(Objects::nonNull)
				.map(LocalDateTime::toLocalDate)
				.findAny();
	}

	private static Integer getAttributeValue(Element root, String attributeName) {
		try {
			return Integer.valueOf(root.getAttributeStaticStr(attributeName));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static long getDuration(Element data) {
		List<Element> suites = data.findChildren(el -> "suite" == el.getName());
		return suites.stream()
				.map(suite -> suite.getAttributeStaticStr("duration-ms"))
				.filter(Objects::nonNull)
				.mapToLong(Long::valueOf)
				.sum();
	}

	static Results getResults(Element data) {
		int total = getAttributeValue(data, "total");
		int failed = getAttributeValue(data, "failed");
		int ignored = getAttributeValue(data, "ignored");
		int passed = getAttributeValue(data, "passed");
		int skipped = getAttributeValue(data, "skipped");

		long duration = getDuration(data);

		return new Results(total, failed, ignored, passed, skipped, duration);
	}

	static Optional<Element> parseData(String data) {
		Results results = null;
		final DomBuilderHandler handler = new DomBuilderHandler(new DefaultElementFactory());
		simpleParser.parse(handler, data);
		return Optional.ofNullable(handler.getParsedElements().poll());
	}

	private static LocalDateTime parseFinishedAt(String text) {
		try {
			return LocalDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME);
		} catch (Exception e) {
			return null;
		}
	}
}
