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

import tigase.tests.SummaryGenerator;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;

public class Results {

	private final static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
	private final Duration duration;
	private final int failed;
	private final int ignored;
	private final int passed;
	private final int skipped;
	private final int total;

	public Results(int total, int failed, int ignored, int passed, int skipped, long msDuration) {
		this.total = total;
		this.failed = failed;
		this.ignored = ignored;
		this.passed = passed;
		this.skipped = skipped;
		this.duration = Duration.ofMillis(msDuration);
	}

	public int getTotal() {
		return total;
	}

	public int getFailed() {
		return failed;
	}

	public int getIgnored() {
		return ignored;
	}

	public int getPassed() {
		return passed;
	}

	public int getSkipped() {
		return skipped;
	}

	public Duration getDuration() {
		return duration;
	}

	public String getMetricsInfo() {
		return SummaryGenerator.isIgnoreSkipped()
			   ? String.format("%1$s / %2$s | %3$s", passed, failed, total)
			   : String.format("%1$s / %2$s / %3$s | %4$s", passed, skipped, failed, total);
	}

	public String getFormattedDuration() {
		return formatter.format(LocalTime.ofSecondOfDay(duration.getSeconds()));
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", Results.class.getSimpleName() + "[", "]").add("total=" + total)
				.add("failed=" + failed)
				.add("ignored=" + ignored)
				.add("passed=" + passed)
				.add("skipped=" + skipped)
				.add("duration=" + duration)
				.toString();
	}
}
