/*
 * Tigase TTS-NG - Test suits for Tigase XMPP Server
 * Copyright (C) 2004 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
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
package tigase.tests;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import tigase.tests.dao.DaoFactory;
import tigase.tests.dao.ReportsDao;

import java.io.Reader;
import java.io.Writer;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Helper class userd to (re-)generate test summary page. Can be invoked using following command:
 *
 * <pre>mvn clean install -DskipTests  exec:java -Dexec.mainClass="tigase.tests.SummaryGenerator" -Dexec.args="arn:aws:s3:::build.tigase.net/tests-results/tts-ng/"</pre>
 *
 * In case of using S3 backend it's essential that required AWS credentials are available, e.g.:
 * $ export AWS_ACCESS_KEY_ID="key"
 * $ export AWS_SECRET_ACCESS_KEY="secret"
 */
public class SummaryGenerator {

	public static void main(String[] args) throws Exception {

		final long start = System.currentTimeMillis();

		if (args.length != 1) {
			System.err.println("Please provide path to results");
			System.exit(1);
		}

		final ReportsDao dao = DaoFactory.createDao(args[0]);

		System.out.println("Rendering index template...");
		dao.writeIndexFile(writer -> {
			SummaryGenerator.generate(dao, writer);
			AssetsHolder.copyAssetsTo(dao);
		});

		System.out.println("Generating took: " + LocalTime.ofSecondOfDay(
				Duration.ofMillis(System.currentTimeMillis() - start).getSeconds()).format(DateTimeFormatter.ISO_TIME));
		System.exit(0);
	}

	private static void generate(ReportsDao dao, Writer writer) {
		Velocity.init();
		VelocityContext context = new VelocityContext();
		final List<ReportsDao.TestType> testTypes = dao.getTestTypes();

		System.out.println("testTypes: " + testTypes);
		testTypes.forEach(testType -> {
			System.out.println("testType: " + testType + " versions: ");
			testType.getVersions().forEach(version -> {
				System.out.println(testType + ": " + version);
			});
		});
		System.out.println("dao.getAllDatabases(): " + dao.getAllDatabases());

		context.put("rootPath", ReportsDao.TEST_RESULTS_PATH);
		context.put("testTypes", testTypes);
		context.put("databases", dao.getAllDatabases());
		context.put("generationDate", new Date());

		final Optional<Reader> template = AssetsHolder.getTemplate();
		if (template.isPresent()) {
			Velocity.evaluate(context, writer, "tag", template.get());
		}
	}
}
