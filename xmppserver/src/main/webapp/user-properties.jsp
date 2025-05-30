<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>

<%@ page import="org.jivesoftware.openfire.PresenceManager,
                 org.jivesoftware.openfire.admin.AdminManager,
                 org.jivesoftware.openfire.group.Group,
                 org.jivesoftware.openfire.user.User,
                 org.jivesoftware.openfire.user.UserManager,
                 org.jivesoftware.openfire.user.UserNotFoundException"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.util.JiveGlobals"%>
<%@ page import="org.jivesoftware.util.LocaleUtils"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.util.StringUtils"%>
<%@ page import="org.xmpp.packet.JID"%><%@ page import="org.xmpp.packet.Presence"%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.nio.charset.StandardCharsets" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    boolean password = request.getParameter("password") != null;
    String username = ParamUtils.getParameter(request,"username");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-summary.jsp");
        return;
    }

    // Handle a delete
    if (delete) {
        response.sendRedirect("user-delete.jsp?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8));
        return;
    }

    // Handle password change
    if (password) {
        response.sendRedirect("user-password.jsp?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8));
        return;
    }

    // Load the user object
    User user = null;
    try {
        user = webManager.getUserManager().getUser(username);
    }
    catch (UserNotFoundException unfe) {
    }

    PresenceManager presenceManager = webManager.getPresenceManager();
    boolean lockedOut = false;
    boolean pendingLockOut = false;
    if (webManager.getLockOutManager().getDisabledStatus(username) != null) {
        // User is locked out. Check if he is locket out now
        if (webManager.getLockOutManager().isAccountDisabled(username)) {
            lockedOut = true;
        }
        else {
            pendingLockOut = true;
        }
    }
    pageContext.setAttribute("username", username);
%>

<html>
    <head>
        <title><fmt:message key="user.properties.title"/></title>
        <meta name="subPageID" content="user-properties"/>
        <meta name="extraParams" content="<%= "username="+URLEncoder.encode(username, StandardCharsets.UTF_8) %>"/>
        <meta name="helpPage" content="edit_user_properties.html"/>
    </head>
    <body>

<p>
<fmt:message key="user.properties.info" />
</p>

<%  if (request.getParameter("success") != null) { %>

    <admin:infoBox type="success">
        <fmt:message key="user.properties.created" />
    </admin:infoBox>

<%  } else if (request.getParameter("locksuccess") != null) { %>

    <admin:infoBox type="success">
        <fmt:message key="user.properties.locksuccess" />
    </admin:infoBox>

<%  } else if (request.getParameter("unlocksuccess") != null) { %>

    <admin:infoBox type="success">
        <fmt:message key="user.properties.unlocksuccess" />
    </admin:infoBox>

<%  } else if (request.getParameter("editsuccess") != null) { %>

    <admin:infoBox type="success">
        <fmt:message key="user.properties.update" />
    </admin:infoBox>

<% } else if (user == null) { %>

    <admin:infoBox type="warning">
        <fmt:message key="error.specific_user_not_found">
            <fmt:param><c:out value="${username}"/></fmt:param>
        </fmt:message>
    </admin:infoBox>

<%  } %>

<div class="jive-table">
<table>
<thead>
    <tr>
        <th colspan="2">
            <fmt:message key="user.properties.title" />
        </th>
    </tr>
</thead>
<tbody>
    <% if (user == null) { %>
    <tr>
        <td colspan="2" style="text-align: center">
            <fmt:message key="error.requested_user_not_found" />
        </td>
    </tr>
    <% } else { %>
    <tr>
        <td class="c1">
            <fmt:message key="user.create.username" />:
        </td>
        <td>
            <%= StringUtils.escapeHTMLTags(JID.unescapeNode(user.getUsername())) %>
            <% if (lockedOut) { %><img src="/images/forbidden-16x16.gif" alt="<fmt:message key='user.properties.locked'/>" title="<fmt:message key='user.properties.locked'/>"/><% } %>
            <% if (pendingLockOut) { %><img src="/images/warning-16x16.gif" alt="<fmt:message key='user.properties.locked_set'/>" title="<fmt:message key='user.properties.locked_set'/>"/><% } %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.status" />:
        </td>
        <td>
            <%  if (presenceManager.isAvailable(user)) {
                    Presence presence = presenceManager.getPresence(user);
            %>
                <% if (presence.getShow() == null) { %>
                <img src="images/user-green-16x16.gif" title="<fmt:message key="user.properties.available" />" alt="<fmt:message key="user.properties.available" />">
                <% } %>
                <% if (presence.getShow() == Presence.Show.chat) { %>
                <img src="images/user-green-16x16.gif" title="<fmt:message key="session.details.chat_available" />" alt="<fmt:message key="session.details.chat_available" />">
                <% } %>
                <% if (presence.getShow() == Presence.Show.away) { %>
                <img src="images/user-yellow-16x16.gif" title="<fmt:message key="session.details.away" />" alt="<fmt:message key="session.details.away" />">
                <% } %>
                <% if (presence.getShow() == Presence.Show.xa) { %>
                <img src="images/user-yellow-16x16.gif" title="<fmt:message key="session.details.extended" />" alt="<fmt:message key="session.details.extended" />">
                <% } %>
                <% if (presence.getShow() == Presence.Show.dnd) { %>
                <img src="images/user-red-16x16.gif" title="<fmt:message key="session.details.not_disturb" />" alt="<fmt:message key="session.details.not_disturb" />">
                <% } %>

            <%  } else { %>

                <img src="images/user-clear-16x16.gif" alt="<fmt:message key="user.properties.offline" />">
                (<fmt:message key="user.properties.offline" />)

            <%  } %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="user.create.isadmin" />:
        </td>
        <td>
            <%= AdminManager.getInstance().isUserAdmin(user.getUsername(), true) ? LocaleUtils.getLocalizedString("global.yes") : LocaleUtils.getLocalizedString("global.no") %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="user.create.name" />:
        </td>
        <td>
            <%  if ("".equals(user.getName())) { %>
                <span style="color:#999">
                <i><fmt:message key="user.properties.not_set" /></i>
                </span>

            <%  } else { %>
                <%= StringUtils.escapeHTMLTags(user.getName()) %>

            <%  } %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="user.create.email" />:
        </td>
        <td>
            <%  if (user.getEmail() == null) { %>
                <span style="color:#999">
                <i><fmt:message key="user.properties.not_set" /></i>
                </span>

            <%  } else { %>
                <a href="mailto:<%= StringUtils.escapeForXML(user.getEmail()) %>"><%= StringUtils.escapeHTMLTags(user.getEmail()) %></a>

            <%  } %>
            &nbsp;
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="user.properties.registered" />:
        </td>
        <td>
            <%  if (user.getCreationDate() == null) { %>
            <span style="color:#999">
                <i><fmt:message key="user.properties.not_available" /></i>
                </span>

            <%  } else { %>
            <%= JiveGlobals.formatDate(user.getCreationDate()) %>
            <%  } %>
            &nbsp;
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="user.properties.groups" />:
        </td>
        <td>
            <%
                Collection<Group> groups = webManager.getGroupManager().getGroups(user);
                if (groups.isEmpty()) {
            %>
                <i><fmt:message key="user.properties.none" /></i>
            <%
                }
                else {
                    int count = 0;
                    for (Group group : groups) {
                        if (count != 0) {
                            out.print(", ");
                        }
                        out.print(StringUtils.escapeHTMLTags(group.getName()));
                        count++;
                    }
                }
            %>
        </td>
    </tr>
    <% } %>
</tbody>
</table>
</div>

<% if (user != null) { %>
    <br>
    <div class="jive-table">
        <table>
            <thead>
                <tr>
                    <th colspan="2"><fmt:message key="user.properties.additional_properties" /></th>
                </tr>
            </thead>
            <tbody>
                <% for(Map.Entry<String, String> properties : user.getProperties().entrySet()) { %>
                <tr>
                    <td class="c1"><%= StringUtils.escapeHTMLTags(properties.getKey()) %>:</td>
                    <td><%= StringUtils.escapeHTMLTags(properties.getValue()) %></td>
                </tr>
                <% } %>
            </tbody>
        </table>
    </div>


    <% if (!UserManager.getUserProvider().isReadOnly()) { %>

        <br><br>

        <form action="user-edit-form.jsp">
        <input type="hidden" name="username" value="<%= StringUtils.escapeForXML(user.getUsername()) %>">
        <input type="submit" value="<fmt:message key="global.edit_properties" />">
        </form>

    <% } %>
<% } %>

</body>
</html>
