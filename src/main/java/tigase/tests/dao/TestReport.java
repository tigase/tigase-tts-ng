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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

public class TestReport {

	public static final String TESTNG_RESULTS = "testng-results.xml";
	public static final String TESTNG_RESULTS_COMPACT = "testng-results-compact.xml";

	public enum ResultType {

		FAILED("danger"),
		SKIPPED("warning"),
		PASSED("success");

		private String cssClass;

		ResultType(String colour) {
			cssClass = colour;
		}

		public String getCssClass() {
			return cssClass;
		}
	}

	public static String LOG_PATH = "server-log/tigase-console.log";
	public static String REPORT_PATH = "html/index.html";
	private String dbType;
	private LocalDate finishedDate;
	private Results metrics;
	private String testType;

	// TODO: zapisywać kompaktowe wyniki w XML? JSON? Pliku tekstowym?
	// TODO: używać transformacji XML do HTML?
	private String version;

	TestReport(String testType, String version, String dbType, Results metrics, LocalDate date) {
		this.testType = testType;
		this.version = version;
		this.dbType = dbType;
		this.metrics = metrics;
		this.finishedDate = date;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();

		sb.append(version).append("@").append(dbType).append(": ");
		if (metrics != null) {
			sb.append(metrics.getMetricsInfo()).append("\t");
		}
		sb.append(dbType).append(", ");
		sb.append(finishedDate).append(", ");
		if (LOG_PATH != null) {
			sb.append("log: ").append(LOG_PATH);
		}
		return sb.toString();
	}

	public Path getLogPath() {
		return Paths.get(version, testType, dbType, LOG_PATH);
	}

	public Path getReportPath() {
		return Paths.get(version, testType, dbType, REPORT_PATH);
	}

	public String getDbType() {
		return dbType;
	}

	public LocalDate getFinishedDate() {
		return finishedDate;
	}

	public Results getMetrics() {
		return metrics;
	}

	public String getTestType() {
		return testType;
	}

	public String getVersion() {
		return version;
	}

	public ResultType getTestResult() {
		if (metrics != null && metrics.getFailed() > 0) {
			return ResultType.FAILED;
		} else if (metrics == null || metrics.getSkipped() > 0) {
			return ResultType.SKIPPED;
		} else {
			return ResultType.PASSED;
		}
	}
}
