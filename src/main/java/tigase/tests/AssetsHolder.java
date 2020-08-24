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

import tigase.tests.dao.ReportsDao;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

class AssetsHolder {

	private final static Logger log = Logger.getLogger(AssetsHolder.class.getName());

	static void copyAssetsTo(ReportsDao dao) {
		for (AssetWithMime asset : getResourcesList()) {
			try (final InputStream resourceAsStream = AssetsHolder.class.getResourceAsStream("/templates" + asset.getAsset())) {
				dao.storeAsset(asset.getAsset(), asset.getAssetMime(), resourceAsStream);
			} catch (IOException e) {
				log.log(Level.SEVERE, "Could not copy asset: " + asset, e);
			}
		}
	}

	public static class AssetWithMime {
		private final String asset;
		private final String assetMime;

		public AssetWithMime(String asset, String assetMime) {
			this.asset = asset;
			this.assetMime = assetMime;
		}

		public String getAsset() {
			return asset;
		}

		public String getAssetMime() {
			return assetMime;
		}
	}

	//	@formatter:off
	private static List<AssetWithMime> getResourcesList() {
		return Arrays.asList(
				new AssetWithMime("/assets/css/bootstrap.min.css", "text/css"),
				new AssetWithMime("/assets/css/custom.css","text/css"),
				new AssetWithMime("/assets/css/fonts.css","text/css"),
				new AssetWithMime("/assets/css/fork-awesome.css","text/css"),
				new AssetWithMime("/assets/fonts/oswald-regular-webfont.woff","font/woff"),
				new AssetWithMime("/assets/fonts/forkawesome-webfont.ttf","font/ttf"),
				new AssetWithMime("/assets/images/tigase-logo.png","image/png"));
	}
	//	@formatter:on

	public static Optional<Reader> getTemplate() {
		BufferedReader reader = null;
		try {
			final InputStream resourceAsStream = SummaryGenerator.class.getResourceAsStream("/templates/index.vm");
			reader = new BufferedReader(new InputStreamReader(resourceAsStream, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			log.log(Level.SEVERE, "Problem reading template", e);
		}
		return Optional.ofNullable(reader);
	}
}
