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

import java.io.FilenameFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

abstract class AbstractDao
		implements ReportsDao {

	final static Logger log = Logger.getLogger(AbstractDao.class.getName());
	final Pattern versionPattern = Pattern.compile("(\\d\\.){2}\\d(-(SNAPSHOT|RC)\\d*)?-b\\d{4,}");
	protected String resultsPath;

	private List<TestType> types = null;

	AbstractDao(String path) {
		this.resultsPath = path;
	}

	@Override
	public List<TestType> getTestTypes() {
		if (types != null) {
			return types;
		}
		Map<String, List<Version>> testTypes = new ConcurrentHashMap<>();

		getVersionsList().stream().parallel().flatMap(ver -> getTypesOf(ver).stream().parallel()).forEach(vtt -> {
			testTypes.computeIfAbsent(vtt.getKey(), s -> new CopyOnWriteArrayList<>())
					.add(getVersion(vtt.getValue(), vtt.getKey()));
		});

		types = testTypes.entrySet()
				.stream()
				.map(entry -> new TestType(entry.getKey(), entry.getValue()))
				.sorted()
				.collect(Collectors.toList());
		return types;
	}

	abstract List<String> getNamesInPath(Path path, FilenameFilter filenameFilter);

	abstract TestReport getTestReportFromFile(Path results, String version, String testType, String database);

	abstract boolean fileExists(Path path);

	abstract TestReport getDummyTestReportFromLogFile(String version, String testType, String database, Path logFile);

	private List<String> getVersionsList() {
		final Path path = Paths.get(resultsPath, ReportsDao.TEST_RESULTS_PATH);
		log.log(Level.INFO, "Getting versions from: " + path);
		final FilenameFilter filenameFilter = (dir, name) -> versionPattern.matcher(name).matches();
		return getNamesInPath(path, filenameFilter);
	}

	private Version getVersion(String version, String type) {
		List<String> databases = getDatabasesFor(version, type);
		final Map<String, TestReport> collect = new HashMap<>();
		for (String db : databases) {
			Optional<TestReport> testReportFor = getTestReportFor(version, type, db);
			if (testReportFor.isPresent()) {
				TestReport testReport = testReportFor.get();
				collect.put(db, testReport);
			}
		}

		return new Version(type, version, collect);
	}

	private List<String> getDatabasesFor(String version, String testType) {
		final Path path = Paths.get(resultsPath, ReportsDao.TEST_RESULTS_PATH, version, testType);
		return getNamesInPath(path, null);
	}

	private Optional<TestReport> getTestReportFor(String version, String testType, String database) {
		TestReport testReport = null;
		final Path compactResults = Paths.get(resultsPath, ReportsDao.TEST_RESULTS_PATH, version, testType, database,
											  TestReport.TESTNG_RESULTS_COMPACT);
		final Path fullResults = Paths.get(resultsPath, ReportsDao.TEST_RESULTS_PATH, version, testType, database,
										   TestReport.TESTNG_RESULTS);
		final Path logFile = Paths.get(resultsPath, ReportsDao.TEST_RESULTS_PATH, version, testType, database,
									   TestReport.LOG_PATH);
		if (fileExists(compactResults)) {
			testReport = getTestReportFromFile(compactResults, version, testType, database);
		} else if (testReport == null && fileExists(fullResults)) {
			testReport = getTestReportFromFile(fullResults, version, testType, database);
		} else if (testReport == null && fileExists(logFile)) {
			testReport = getDummyTestReportFromLogFile(version, testType, database, logFile);
		}
		return Optional.ofNullable(testReport);
	}

	/**
	 * Get a list of mappings of type and version
	 */
	private List<Map.Entry<String, String>> getTypesOf(String version) {
		final Path path = Paths.get(resultsPath, ReportsDao.TEST_RESULTS_PATH, version);
		return getNamesInPath(path, null).stream()
				.map(type -> new AbstractMap.SimpleEntry<>(type, version))
				.collect(Collectors.toList());
	}
}
