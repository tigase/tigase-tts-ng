package tigase.tests.dao;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public interface ReportsDao {

	String TEST_RESULTS_PATH = "test-results";

	List<TestType> getTestTypes();

	default Set<String> getAllDatabases() {
		return getTestTypes().stream()
				.flatMap(testType -> testType.getVersions().stream())
				.flatMap(version -> version.getTestReports().stream())
				.map(TestReport::getDbType)
				.collect(Collectors.toCollection(() -> new TreeSet<String>()));
	}

	void writeIndexFile(Consumer<Writer> consumer);

	void storeAsset(String assetPath, String mime, InputStream inputStream) throws IOException;

	class TestType
			implements Comparable<TestType> {

		String testType;
		List<Version> versions;

		public TestType(String testType, List<Version> versions) {
			this.testType = testType;
			this.versions = versions;
			Collections.sort(versions, Version.COMPARATOR);
		}

		public List<Version> getVersions() {
			return Collections.unmodifiableList(versions);
		}

		public Version getVersion(String version) {
			return versions.stream().filter(v -> version.equalsIgnoreCase(v.getVersion())).findFirst().get();
		}

		public String getTestType() {
			return testType;
		}

		@Override
		public String toString() {
			return "all".equalsIgnoreCase(testType) ? "All available tests" : testType;
		}

		@Override
		public int compareTo(TestType o) {
			if (this.equals(o)) {
				return 0;
			}
			if (this.getTestType().equals("all")) {
				return -1;
			}
			if (o.getTestType().equals("all")) {
				return 1;
			}
			return this.getTestType().compareTo(o.getTestType());
		}
	}

	class Version {

		public static Comparator<Version> COMPARATOR = Comparator.comparing(Version::getAsParsedVersion).reversed();
		Map<String, TestReport> testReports;
		String testType;
		String version;

		public Version(String testType, String version, Map<String, TestReport> testReports) {
			this.version = version;
			this.testReports = testReports;
		}

		public Collection<TestReport> getTestReports() {
			return testReports.values();
		}

		public TestReport getTestReportFor(String database) {
			return testReports.get(database);
		}

		public LocalDate getCommonDate() {
			return testReports.values()
					.stream()
					.map(TestReport::getFinishedDate)
					.max(LocalDate::compareTo)
					.orElse(LocalDate.MIN);
		}

		public String getVersion() {
			return version;
		}

		@Override
		public String toString() {
			return version + ": testReports=" + testReports;
		}

		private tigase.util.Version getAsParsedVersion() {
			try {
				return tigase.util.Version.of(this.version);
			} catch (IllegalArgumentException e) {
				System.out.println("can't parse version: " + this.version);
				return tigase.util.Version.ZERO;
			}
		}
	}
}
