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
			System.out.println("testType: " + testTypes + " versions: " + testType.getVersions());
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
