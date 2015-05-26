/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2015 "Tigase, Inc." <office@tigase.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License,
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
 */
package tigase.server;


import tigase.util.ClassUtil;
import tigase.xml.XMLUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.testng.ISuite;
import org.testng.ISuiteListener;

import static tigase.TestLogger.log;

/**
 *
 * @author Wojciech Kapcia
 */
public class ServerBootstrap
		implements ISuiteListener {

	private Thread thread = null;
	private Properties props = null;
	private Boolean enabled = false;

	@Override
	public void onFinish( ISuite suite ) {
		try {
			log( " == stopping thread" );
			if ( enabled && thread != null ){
				thread.stop();
			}
		} catch ( Exception e ) {
			log( "error shutting down server" );
		}
	}

	public static void main( String[] args ) {
		ServerBootstrap serverBootstrap = new ServerBootstrap();
		serverBootstrap.onStart( null );
	}

	@Override
	public void onStart( ISuite suite ) {

		try {
			String property = getProperty( "test.local_server.enabled" );
			if ( property != null ){
				enabled = Boolean.valueOf( property );
			}
		} catch ( IOException ex ) {
			log( ex.getMessage() );
		}

		if ( enabled ){
			thread = new Thread( new XMPPServerThread() );
			thread.setDaemon( true );
			thread.start();

			try {
				Thread.sleep( 1 * 10 * 1000 );
			} catch ( InterruptedException ex ) {
				Logger.getLogger( ServerBootstrap.class.getName() ).log( Level.SEVERE, null, ex );
			}
		}

	}

	private class XMPPServerThread implements Runnable {

		@Override
		public void run() {

			String[] args = new String[ 2 ];
			args[0] = "--property-file";
			args[1] = "src/test/resources/server/init.properties";

			XMPPServer.setOSGi( false );
			XMPPServer.parseParams( args );

			System.out.println( ( new ComponentInfo( XMLUtils.class ) ).toString() );
			System.out.println( ( new ComponentInfo( ClassUtil.class ) ).toString() );
			System.out.println( ( new ComponentInfo( XMPPServer.class ) ).toString() );
			XMPPServer.start( args );

		}

	}


	private String getProperty(String key) throws IOException {
		if ( this.props == null ){
			loadProperties();
		}
		return this.props.getProperty( key );
	}

	private void loadProperties() throws IOException {
		if ( this.props == null ){
			try ( InputStream stream = getClass().getResourceAsStream( "/server.properties" ) ) {
				this.props = new Properties();
				this.props.load( stream );
			}
		}
	}

}
