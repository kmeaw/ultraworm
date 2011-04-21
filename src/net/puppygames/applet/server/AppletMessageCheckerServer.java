/*
 * Copyright (c) 2003 Shaven Puppy Ltd
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'Shaven Puppy' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.puppygames.applet.server;

import java.rmi.Remote;
import java.sql.*;
import java.util.*;

import net.puppygames.applet.*;
import net.puppygames.gamecommerce.server.*;
import net.puppygames.gamecommerce.shared.ConfigurationDetails;
import net.puppygames.gamecommerce.shared.RegistrationDetails;

import com.shavenpuppy.jglib.util.Util;

/**
 * $Id: AppletMessageCheckerServer.java,v 1.8 2010/10/28 20:08:12 foo Exp $
 * Super Dudester hiscore server
 * <p>
 *
 * @author $Author: foo $
 * @version $Revision: 1.8 $
 */
public class AppletMessageCheckerServer extends BaseServerImpl implements AppletMessageCheckerRemote, Remote {

	/**
	 * C'tor
	 *
	 * @param server
	 * @param appTitle
	 * @param remoteName
	 * @throws Exception
	 */
	public AppletMessageCheckerServer(GamecommerceServerLocal server) throws Exception {
		super(server, "Applet message checker server", AppletMessageCheckerRemote.REMOTE_NAME);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see net.puppygames.applet.AppletMessageCheckerRemote#checkForMessages(java.lang.String,
	 *      int)
	 */
	@Override
	public MessageReturn checkForMessages(final String game, final int version) throws Exception, SQLException {
		return (MessageReturn) new SQLExec("MessageChecker", server.getConnectionCache()) {
			/*
			 * (non-Javadoc)
			 *
			 * @see net.puppygames.gamecommerce.server.SQLExec#exec(java.sql.Connection)
			 */
			@Override
			public Object exec(Connection conn) throws SQLException, Exception {
				PreparedStatement ps = null;
				ResultSet rs = null;
				try {
					ps = conn.prepareStatement("select title, message from appletmessages where game = ? and version > ?");
					ps.setString(1, game);
					ps.setInt(2, version);
					rs = ps.executeQuery();
					if (rs == null) {
						return null;
					}
					if (!rs.next()) {
						return null;
					}
					String title = rs.getString(1);
					String message = rs.getString(2);
					return new MessageReturn(title, message, null);
				} finally {
					if (rs != null) {
						rs.close();
					}
					if (ps != null) {
						ps.close();
					}
				}
			}
		}.action();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see net.puppygames.applet.AppletMessageCheckerRemote#checkForMessages(java.lang.String,
	 *      java.lang.String, int,
	 *      net.puppygames.gamecommerce.shared.ConfigurationDetails)
	 */
	@Override
	public MessageReturn checkForMessages(final String game, final String version, final int sequenceNumber,
			final ConfigurationDetails oldConfig) throws Exception, SQLException {
		return (MessageReturn) new SQLExec("MessageChecker", server.getConnectionCache()) {
			/*
			 * (non-Javadoc)
			 *
			 * @see net.puppygames.gamecommerce.server.SQLExec#exec(java.sql.Connection)
			 */
			@Override
			public Object exec(Connection conn) throws SQLException, Exception {
				PreparedStatement ps = null;
				ResultSet rs = null;
				try {
					ps = conn.prepareStatement("select title, message from appletmessages where game = ? and version > ?");
					ps.setString(1, game);
					ps.setInt(2, sequenceNumber);
					rs = ps.executeQuery();
					String title = null;
					String message = null;
					if (rs != null && rs.next()) {
						title = rs.getString(1);
						message = rs.getString(2);
					}
					if (rs != null) {
						rs.close();
					}
					ps.close();

					// Now check to see if our current config exists
					ps = conn.prepareStatement("select config from configurations where game = ? and version = ? and enabled = 1");
					ps.setString(1, game);
					ps.setString(2, version);
					rs = ps.executeQuery();
					List available = new LinkedList();
					ConfigurationDetails newConfig;
					while (rs.next()) {
						ConfigurationDetails cd = oldConfig.decode(rs.getString(1));
						available.add(cd);
					}
					if (available.size() > 0 && !available.contains(oldConfig)) {
						newConfig = (ConfigurationDetails) available.get(Util.random(0, available.size() - 1));
					} else {
						newConfig = null;
					}
					return new MessageReturn(title, message, newConfig);
				} finally {
					if (rs != null) {
						rs.close();
					}
					if (ps != null) {
						ps.close();
					}
				}
			}
		}.action();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see net.puppygames.applet.AppletMessageCheckerRemote#checkRegistrationValid(net.puppygames.gamecommerce.shared.RegistrationDetails)
	 */
	@Override
	public boolean checkRegistrationValid(final RegistrationDetails registration) {
		try {
			return ((Boolean) new SQLExec("RegChecker", server.getConnectionCache()) {
				/*
				 * (non-Javadoc)
				 *
				 * @see net.puppygames.gamecommerce.server.SQLExec#exec(java.sql.Connection)
				 */
				@Override
				public Object exec(Connection conn) throws SQLException, Exception {
					PreparedStatement ps = null;
					ResultSet rs = null;
					try {
						ps = conn.prepareStatement("select status from registrations where authcode = ?");
						ps.setString(1, registration.getAuthCode());
						rs = ps.executeQuery();
						if (rs == null) {
							// Not valid
							System.out.println("No results 1: Remotely disabling " + registration.toString());
							return Boolean.FALSE;
						}
						if (rs.next()) {
							int status = rs.getInt(1);
							switch (status) {
								case GamecommerceDatabase.STATUS_BANNED:
									System.out.println("Banned: Remotely disabling " + registration.toString());
									return Boolean.FALSE;
								default:
									return Boolean.TRUE;
							}
						} else {
							// Not valid by authcode... try to find by game/email...
							rs.close();
							ps.close();
							rs = null;
							ps = null;
							ps = conn.prepareStatement("select status from registrations where game = ? and email = ?");
							ps.setString(1, registration.getGame());
							ps.setString(2, registration.getEmail());
							rs = ps.executeQuery();
							if (rs == null) {
								// Not valid
								System.out.println("No results 2: Remotely disabling " + registration.toString());
								return Boolean.FALSE;
							}
							if (rs.next()) {
								int status = rs.getInt(1);
								switch (status) {
									case GamecommerceDatabase.STATUS_BANNED:
										System.out.println("Banned: Remotely disabling " + registration.toString());
										return Boolean.FALSE;
									default:
										return Boolean.TRUE;
								}
							} else {
								System.out.println("No results 3: Remotely disabling " + registration.toString());
								return Boolean.FALSE;
							}
						}
					} finally {
						try {
							if (ps != null) {
								ps.close();
							}
						} catch (Exception e) {
						}
						try {
							if (rs != null) {
								rs.close();
							}
						} catch (Exception e) {
						}
					}
				}
			}.action()).booleanValue();
		} catch (Exception e) {
			// Assume things are ok
			e.printStackTrace(System.err);
			return true;
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see net.puppygames.applet.AppletMessageCheckerRemote#getNews(java.util.Calendar)
	 */
	@Override
	public List<News> getNews(final Calendar lastCheck) {
		try {
			return (List) new SQLExec("getNews", server.getConnectionCache()) {
				@Override
				public Object exec(Connection conn) throws SQLException, Exception {
					PreparedStatement ps = null;
					ResultSet rs = null;
					try {
						List ret = new LinkedList();
						ps = conn
								.prepareStatement("select created, message, url from appletnews where created > ? order by created");
						ps.setDate(1, new java.sql.Date(lastCheck.getTimeInMillis()));
						rs = ps.executeQuery();
						while (rs.next()) {
							Calendar c = Calendar.getInstance();
							java.sql.Date created = rs.getDate(1);
							c.setTime(created);
							ret.add(new News(c, rs.getString(2), rs.getString(3)));
						}
						return ret;
					} finally {
						if (rs != null) {
							try {
								rs.close();
							} catch (Exception e) {
							}
						}
						if (ps != null) {
							try {
								ps.close();
							} catch (Exception e) {
							}
						}
					}
				}
			}.action();
		} catch (Exception e) {
			e.printStackTrace(System.err);
			return null;
		}
	}

}
