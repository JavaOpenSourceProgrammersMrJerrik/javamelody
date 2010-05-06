/*
 * Copyright 2008-2010 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Java Melody is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Java Melody is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Java Melody.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.bull.javamelody;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Filter of monitoring JavaMelody with security check for JIRA system administrator.
 * @author Emeric Vernat
 */
public class JiraMonitoringFilter extends MonitoringFilter {
	// valeur de com.atlassian.jira.security.Permissions.SYSTEM_ADMIN
	private static final int SYSTEM_ADMIN = 44;
	// valeur de DefaultAuthenticator.LOGGED_IN_KEY
	private static final String LOGGED_IN_KEY = "seraph_defaultauthenticator_user";
	private boolean jira = true;

	/** {@inheritDoc} */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if (!(request instanceof HttpServletRequest)) {
			super.doFilter(request, response, chain);
			return;
		}
		final HttpServletRequest httpRequest = (HttpServletRequest) request;

		if (jira && httpRequest.getRequestURI().equals(getMonitoringUrl(httpRequest))) {
			try {
				// only the jira administrator can view the monitoring report
				final Object user = getUser(httpRequest);
				if (user == null) {
					// si non authentifié, on redirige vers la page de login en indiquant la page
					// d'origine (sans le contexte) à afficher après le login
					final HttpServletResponse httpResponse = (HttpServletResponse) response;
					final String destination = getMonitoringUrl(httpRequest).substring(
							httpRequest.getContextPath().length());
					httpResponse.sendRedirect("login.jsp?os_destination=" + destination);
					return;
				}
				if (!hasSystemAdminPermission(user)) {
					final HttpServletResponse httpResponse = (HttpServletResponse) response;
					// si authentifié mais sans la permission system admin, alors Forbidden
					httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden access");
					return;
				}
			} catch (final ClassNotFoundException e) {
				// apparemment ce n'est pas jira, mais bamboo ou confluence
				jira = false;
			}
		}
		super.doFilter(request, response, chain);
	}

	private static boolean hasSystemAdminPermission(Object user) throws ClassNotFoundException {
		if (user == null) {
			return false;
		}
		final Class<?> managerFactoryClass = Class.forName("com.atlassian.jira.ManagerFactory");
		final Class<?> userClass = Class.forName("com.opensymphony.user.User");
		try {
			// on travaille par réflexion car la compilation normale introduirait une dépendance
			// trop compliquée et trop lourde à télécharger pour maven
			final Object permissionManager = managerFactoryClass.getMethod("getPermissionManager")
					.invoke(null);
			final Boolean result = (Boolean) permissionManager.getClass().getMethod(
					"hasPermission", new Class[] { Integer.TYPE, userClass }).invoke(
					permissionManager, new Object[] { SYSTEM_ADMIN, user });
			return result;
		} catch (final SecurityException e) {
			throw new IllegalStateException(e);
		} catch (final IllegalAccessException e) {
			throw new IllegalStateException(e);
		} catch (final InvocationTargetException e) {
			throw new IllegalStateException(e);
		} catch (final NoSuchMethodException e) {
			throw new IllegalStateException(e);
		}
		//		return remoteUser != null
		//				&& com.atlassian.jira.ManagerFactory.getPermissionManager().hasPermission(
		//						SYSTEM_ADMIN, (com.opensymphony.user.User) remoteUser);
	}

	private static Object getUser(HttpServletRequest httpRequest) {
		final HttpSession session = httpRequest.getSession(false);
		if (session == null) {
			return null;
		}
		return session.getAttribute(LOGGED_IN_KEY);
	}
}