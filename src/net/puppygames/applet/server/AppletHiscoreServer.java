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
import java.rmi.server.RemoteServer;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.*;

import net.puppygames.applet.*;
import net.puppygames.gamecommerce.server.*;

/**
 * $Id: AppletHiscoreServer.java,v 1.22 2008/11/12 16:47:32 foo Exp $ Super
 * Dudester hiscore server
 * <p>
 *
 * @author $Author: foo $
 * @version $Revision: 1.22 $
 */
public class AppletHiscoreServer extends BaseServerImpl implements AppletHiscoreServerRemote, Remote {

	/** A Map of TreeSets */
	private final Map hiscores = new HashMap();

	/** Naughty words */
	private static final String[] BAD_WORDS = {"motherfucker", "fucka", "cunty", "cunt", "fuck", "shite", "shitt", "shit", "cock",
			"dick", "bitch", "crap", "turd", "ass", "arse", "slut", "penis", "balls", "bastard", "mother"};

	private static final String[][] BAD_WORDS2 = { {"hate", "love"}, {"hamas", "hummus"}};

	/** Words that get you banned immediately */
	private static final String[] BAD_WORDS3 = {"fuck", "cunt", "shit"};

	/**
	 * C'tor
	 *
	 * @param server
	 * @param appTitle
	 * @param remoteName
	 * @throws Exception
	 */
	public AppletHiscoreServer(GamecommerceServerLocal server) throws Exception {
		super(server, "Applet hiscore server", AppletHiscoreServerRemote.REMOTE_NAME);
		loadHiScores();
	}

	private synchronized void loadHiScores() throws Exception {
		// Load the hiscores
		new SQLExec("LoadScores", server.getConnectionCache()) {
			@Override
			public Object exec(Connection conn) throws SQLException, Exception {
				PreparedStatement st = null;
				ResultSet rs = null;
				try {
					st = conn
							.prepareStatement("select ahs.game, ahs.scoregroup, ahs.name, ahs.installation, ahs.points, ahs.medals, ahs.registered, ahs.version "
									+ " from applethiscores ahs join games on ahs.game = games.game and ahs.version = games.currentversion "
									+ " where date > ?");
					GregorianCalendar c = new GregorianCalendar();
					c.add(Calendar.WEEK_OF_YEAR, -1);
					java.util.Date d = c.getTime();
					st.setDate(1, new java.sql.Date(d.getTime()));
					rs = st.executeQuery();
					while (rs.next()) {
						Score score = new Score(rs.getString(1), "none", rs.getString(2), rs.getString(3), rs.getLong(4), rs
								.getInt(5), rs.getString(6), rs.getBoolean(7));
						TreeSet scores = (TreeSet) hiscores.get(score.getGame());
						if (scores == null) {
							scores = new TreeSet();
							hiscores.put(score.getGame(), scores);
						}
						scores.add(score);
					}
					return null;
				} finally {
					try {
						if (rs != null) {
							rs.close();
						}
					} catch (Exception e) {
					}
					try {
						if (st != null) {
							st.close();
						}
					} catch (Exception e) {
					}
				}

			}
		}.action();

		// Calculate ranks
		for (Iterator i = hiscores.values().iterator(); i.hasNext();) {
			TreeSet table = (TreeSet) i.next();
			int count = 0;
			for (Iterator j = table.iterator(); j.hasNext();) {
				Score s = (Score) j.next();
				if (count == MAX_SCORES) {
					j.remove();
				} else {
					s.setRank(count++);
				}
			}
		}
	}

	private synchronized void doLoadHiscores(Connection conn, String game) throws SQLException {
		PreparedStatement st = null;
		ResultSet rs = null;
		try {
			st = conn
					.prepareStatement("select ahs.game, ahs.scoregroup, ahs.name, ahs.installation, ahs.points, ahs.medals, ahs.registered from applethiscores ahs join games on ahs.game = games.game and ahs.version = games.currentversion where ahs.game = ? and ahs.date > ? order by ahs.points desc limit "
							+ MAX_SCORES);
			GregorianCalendar c = new GregorianCalendar();
			c.add(Calendar.WEEK_OF_YEAR, -1);
			java.util.Date d = c.getTime();
			st.setString(1, game);
			st.setDate(2, new java.sql.Date(d.getTime()));
			rs = st.executeQuery();
			TreeSet scores = new TreeSet();
			hiscores.put(game, scores);
			while (rs.next()) {
				Score score = new Score(rs.getString(1), "none", rs.getString(2), rs.getString(3), rs.getLong(4), rs
						.getInt(5), rs.getString(6), rs.getBoolean(7));
				scores.add(score);
			}
			// Calculate ranks
			int count = 0;
			for (Iterator j = scores.iterator(); j.hasNext();) {
				Score s = (Score) j.next();
				if (count == MAX_SCORES) {
					j.remove();
				} else {
					s.setRank(count++);
				}
			}
		} finally {
			try {
				if (rs != null) {
					rs.close();
				}
			} catch (Exception e) {
			}
			try {
				if (st != null) {
					st.close();
				}
			} catch (Exception e) {
			}
		}
	}

	private void loadHiScores(final String game) throws Exception {
		// Load the hiscores
		new SQLExec("LoadScores", server.getConnectionCache()) {
			@Override
			public Object exec(Connection conn) throws SQLException, Exception {
				doLoadHiscores(conn, game);
				return null;
			}
		}.action();

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see net.puppygames.applet.AppletHiscoreServerRemote#getHiscores(java.lang.String)
	 */
	@Override
	public synchronized List<Score> getHiscores(String game) {
		TreeSet scores = (TreeSet) hiscores.get(game);
		if (scores == null) {
			scores = new TreeSet();
			hiscores.put(game, scores);
		} else {
			// Clean naughty words
			for (Iterator i = scores.iterator(); i.hasNext();) {
				Score score = (Score) i.next();
				for (int j = 0; j < BAD_WORDS.length; j++) {
					score.setName(score.getName().replaceAll(BAD_WORDS[j], "puppy"));
				}
				for (int j = 0; j < BAD_WORDS2.length; j++) {
					score.setName(score.getName().replaceAll(BAD_WORDS2[j][0], BAD_WORDS2[j][1]));
				}
			}
		}
		return new ArrayList(scores);
	}

	private void ban(Connection conn, String game, long installation, String reason) throws SQLException {
		System.out.println("Banning user: "+installation+"/"+game+"/"+reason);
		PreparedStatement ps = null;
		try {
			ps = conn.prepareStatement("insert into appletban (installation, date, reason) values (?, now(), ?)");
			ps.setLong(1, installation);
			ps.setString(2, reason);
			ps.executeUpdate();

			// Delete all their other scores
			ps = conn.prepareStatement("delete from applethiscores where installation = ?");
			ps.setLong(1, installation);
			ps.executeUpdate();
		} finally {
			try {
				if (ps != null) {
					ps.close();
				}
			} catch (Exception e) {
			}
		}

		doLoadHiscores(conn, game);

	}

	private boolean checkAlreadyBanned(Connection conn, long installation) throws SQLException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Check for banned users
			ps = conn.prepareStatement("select 1 from appletban where installation=?");
			ps.setLong(1, installation);
			rs = ps.executeQuery();
			if (rs.next()) {
				return true;
			} else {
				return false;
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

	private void deleteHiScore(Connection conn, Score score) throws SQLException {
		PreparedStatement ps = null;
		try {
			ps = conn.prepareStatement("delete from applethiscores where game=? and scoregroup "
					+ (score.getGroup() == null ? "is null" : "= ?")
					+ " and name=? and installation=? and points<=? and version=?");
			int p = 1;
			ps.setString(p++, score.getGame());
			if (score.getGroup() != null) {
				ps.setString(p++, score.getGroup());
			}
			ps.setString(p++, score.getName());
			ps.setLong(p++, score.getInstallation());
			ps.setInt(p++, score.getPoints());
			ps.setString(p++, score.getVersion());
			ps.executeUpdate();
		} finally {
			try {
				if (ps != null) {
					ps.close();
				}
			} catch (Exception e) {
			}
		}
	}

	private void checkBan(Connection conn, Score score) throws SQLException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			// Check for banned users
			ps = conn.prepareStatement("select 1 from appletban where installation=?");
			ps.setLong(1, score.getInstallation());
			rs = ps.executeQuery();
			if (rs.next()) {
				throw new SQLException("You have been banned. Please contact support@puppygames.net for assistance.");
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

	private void checkVersion(Connection conn, String game, String version) throws SQLException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = conn.prepareStatement("select currentversion from games where game=?");
			ps.setString(1, game);
			rs = ps.executeQuery();
			if (!rs.next()) {
				throw new SQLException("Game " + game + " not found!");
			}
			String currentVersion = rs.getString(1);
			if (!version.equals(currentVersion)) {
				throw new SQLException("You are running an old version of the game. Please go to www.puppygames.net and download the latest version.");
			}
		} finally {
			if (rs != null) { try { rs.close(); } catch (Exception e) {} }
			if (ps != null) { try { ps.close(); } catch (Exception e) {} }
		}
	}

	private boolean checkHiScore(Connection conn, Score score) throws SQLException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = conn.prepareStatement("select 1 from applethiscores where game=? and scoregroup "
					+ (score.getGroup() == null ? "is null" : "= ?")
					+ " and name=? and installation=? and points>? and version=?");
			int p = 1;
			ps.setString(p++, score.getGame());
			if (score.getGroup() != null) {
				ps.setString(p++, score.getGroup());
			}
			ps.setString(p++, score.getName());
			ps.setLong(p++, score.getInstallation());
			ps.setInt(p++, score.getPoints());
			ps.setString(p++, score.getVersion());
			rs = ps.executeQuery();
			if (!rs.next()) {
				return true;
			} else {
				return false;
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

	private void insertHiScore(Connection conn, Score score) throws SQLException {
		PreparedStatement ps = null;
		try {
			ps = conn
					.prepareStatement("insert into applethiscores (game, scoregroup, name, installation, points, medals, date, registered, version) values (?,?,?,?,?,?,?,?,?)");
			ps.setString(1, score.getGame());
			if (score.getGroup() == null) {
				ps.setNull(2, java.sql.Types.VARCHAR);
			} else {
				ps.setString(2, score.getGroup());
			}
			ps.setString(3, score.getName());
			ps.setLong(4, score.getInstallation());
			ps.setInt(5, score.getPoints());
			ps.setString(6, score.getMedals());
			ps.setDate(7, new java.sql.Date(new java.util.Date().getTime()));
			ps.setBoolean(8, score.isRegistered());
			ps.setString(9, score.getVersion());
			ps.executeUpdate();
		} finally {
			try {
				if (ps != null) {
					ps.close();
				}
			} catch (Exception e) {
			}
		}
	}

	private String clean(String in) {
		StringBuilder out = new StringBuilder(in.length());
		for (int i = 0; i < in.length(); i ++) {
			char c = in.charAt(i);
			if (Character.isDigit(c) || Character.isLetter(c) || Character.isWhitespace(c)) {
				out.append(c);
			}
		}
		return out.toString();
	}

	private String removeSpaces(String in) {
		StringBuilder out = new StringBuilder(in.length());
		for (int i = 0; i < in.length(); i ++) {
			char c = in.charAt(i);
			if (!Character.isWhitespace(c)) {
				out.append(c);
			}
		}
		return out.toString();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see genesis.HiscoreServer#submit(java.util.List)
	 */
	@Override
	public synchronized HiscoresReturn submit2(final Score score) throws Exception {
		System.out.println("Submitting Applet hiscore 2 from " + RemoteServer.getClientHost() + ": " + score);

		// Check game version
		new SQLExec("CheckVersion", server.getConnectionCache()) {
			@Override
			public Object exec(Connection conn) throws SQLException, Exception {
				checkVersion(conn, score.getGame(), score.getVersion());
				return null;
			}
		}.action();

		// Simple anti-cheat protection
		if (score.getPoints() % 5 != 0) {
			// Ban the fucker
			new SQLExec("BAN", server.getConnectionCache()) {
				@Override
				public Object exec(Connection conn) throws SQLException, Exception {
					if (!checkAlreadyBanned(conn, score.getInstallation())) {
						ban(conn, score.getGame(), score.getInstallation(), "CHEAT:"+score.getPoints());
					}
					return null;
				}
			}.action();
			throw new SQLException("Cheat! It is not possible to score "+score.getPoints()+" in "+score.getGame()+". You have been banned from the online hiscore table. Contact support@puppygames.net for assistance.");
		}

		// Remove punctuation
		score.setName(clean(score.getName()));

		// Check for sweary people and ban them too
		final String swearCheck = removeSpaces(score.getName()).toLowerCase();
		for (int i = 0; i < BAD_WORDS3.length; i ++) {
			if (swearCheck.contains(BAD_WORDS3[i])) {
				throw new SQLException("No swearing on the online hiscores table please.");
			}
		}

		new SQLExec("APPLET.submit", server.getConnectionCache()) {
			@Override
			public Object exec(Connection conn) throws SQLException, Exception {

				checkBan(conn, score);
				deleteHiScore(conn, score);
				if (checkHiScore(conn, score)) {
					insertHiScore(conn, score);
				}

				return null;
			}

		}.action();

		loadHiScores(score.getGame());

		HiscoresReturn ret;
		List newScores = getHiscores(score.getGame());
		// If the score we just submitted isn't in the list, it's because either it
		// wasn't good enough, or because we didn't beat our all-time hiscore
		if (score.getName().equals("c4st3st")) {
			ret = new HiscoresReturn(newScores, "DIDN'T BEAT YOUR\nALL-TIME BEST");
		} else if (!newScores.contains(score)) {
			// If this list is full, look at the last score to see if the incoming
			// score made the grade.
			if (newScores.size() == MAX_SCORES) {
				Score worstInList = (Score) newScores.get(MAX_SCORES - 1);
				if (worstInList.getPoints() >= score.getPoints()) {
					// Didn't make the grade :)
					ret = new HiscoresReturn(newScores, null);
				} else {
					// Old score not beaten
					ret = new HiscoresReturn(newScores, "DIDN'T BEAT YOUR\nALL-TIME BEST");
				}
			} else {
				// Old score not beaten
				ret = new HiscoresReturn(newScores, "DIDN'T BEAT YOUR\nALL-TIME BEST");
			}
		} else {
			// Got a hiscore!
			ret = new HiscoresReturn(newScores, null);
		}
		return ret;

	}

	@Override
	public List<Score> submit(final Score score) throws Exception {
		throw new SQLException("You are running an old version of the game. Please go to www.puppygames.net and download the latest version.");
	}

}
