//=============================================================================
// Copyright 2006-2010 Daniel W. Dyer
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//=============================================================================
package tigase;

import org.testng.*;
import org.testng.xml.XmlSuite;
import org.uncommons.reportng.ReportNGException;
import tigase.xml.Element;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static tigase.tests.dao.TestReport.TESTNG_RESULTS_COMPACT;

/**
 * JUnit XML reporter for TestNG that uses Velocity templates to generate its
 * output.
 *
 * @author Daniel Dyer
 */
public class CompactXMLReporter
		implements IReporter {

	@Override
	public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites, String outputDirectoryName) {
		File outputDirectory = new File(outputDirectoryName);
		outputDirectory.mkdirs();

		final Element results = new Element("testng-results");

		int failed = 0;
		int skipped = 0;
		int passed = 0;

		System.out.println("Processing " + suites.size() + " suites");

		for (ISuite suite : suites) {
			System.out.println("Processing " + suite.getName());

			final Element suiteEl = new Element("suite");
			suiteEl.setAttribute("name", suite.getName());

			long duration = 0;
			Date start = null;
			Date end = null;

			for (ISuiteResult result : suite.getResults().values()) {
				final ITestContext context = result.getTestContext();
				if (start == null || context.getStartDate().before(start)) {
					start = context.getStartDate();
				}
				if (end == null || context.getEndDate().after(end)) {
					end = context.getEndDate();
				}

				final Set<ITestResult> skippedTests = context.getSkippedTests().getAllResults();
				final Set<ITestResult> failedTests = context.getFailedTests().getAllResults();
				final Set<ITestResult> passedTests = context.getPassedTests().getAllResults();
				skipped += skippedTests.size();
				failed += failedTests.size();
				passed += passedTests.size();

				duration += getDuration(skippedTests, failedTests, passedTests);
			}
			suiteEl.setAttribute("started-at",
								 String.valueOf(LocalDateTime.ofInstant(start.toInstant(), ZoneId.systemDefault())));
			suiteEl.setAttribute("finished-at",
								 String.valueOf(LocalDateTime.ofInstant(end.toInstant(), ZoneId.systemDefault())));
			suiteEl.setAttribute("duration-ms", String.valueOf(duration));

			System.out.println("Generated suite element: " + suiteEl.childrenToString());

			results.addChild(suiteEl);
		}
		results.setAttribute("failed", String.valueOf(failed));
		results.setAttribute("skipped", String.valueOf(skipped));
		results.setAttribute("passed", String.valueOf(passed));
		results.setAttribute("total", String.valueOf(failed + skipped + passed));

		try {
			Files.write(Paths.get(outputDirectoryName, TESTNG_RESULTS_COMPACT), results.toStringPretty().getBytes());
		} catch (IOException e) {
			throw new ReportNGException("Failed generating Compact XML report.", e);
		}

	}

	private long getDuration(Set<ITestResult>... allResults) {
		long duration = 0;
		for (Set<ITestResult> allResult : allResults) {
			for (ITestResult testResult : allResult) {
				duration += testResult.getEndMillis() - testResult.getStartMillis();
			}
		}
		return duration;
	}
}
