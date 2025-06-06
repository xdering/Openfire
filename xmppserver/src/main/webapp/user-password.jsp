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

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.user.*,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%><%@ page import="org.xmpp.packet.JID"%>
<%@ page import="org.jivesoftware.openfire.security.SecurityAuditManager" %>
<%@ page import="java.nio.charset.StandardCharsets" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="admin" uri="admin" %>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />
<% admin.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean update = request.getParameter("update") != null;
    String username = ParamUtils.getParameter(request,"username");
    String password = ParamUtils.getParameter(request,"password");
    String passwordConfirm = ParamUtils.getParameter(request,"passwordConfirm");
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-properties.jsp?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8));
        return;
    }

    // Load the user object
    User user = admin.getUserManager().getUser(username);

    // Handle a password update:
    boolean errors = false;
    boolean unsupported = false;
    if (update) {
        // Validate the passwords:
        if (password != null && password.equals(passwordConfirm)) {
            try {
                user.setPassword(password);
                if (!SecurityAuditManager.getSecurityAuditProvider().blockUserEvents()) {
                    // Log the event
                    admin.logEvent("set password for user "+username, null);
                }
                // Done, so redirect
                response.sendRedirect("user-password.jsp?success=true&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8));
                return;
            }
            catch (UnsupportedOperationException uoe) {
                unsupported = true;
            }
        }
        else {
            errors = true;
        }
    }
%>

<html>
    <head>
        <title><fmt:message key="user.password.title"/></title>
        <meta name="subPageID" content="user-password"/>
        <meta name="extraParams" content="<%= "username="+URLEncoder.encode(username, StandardCharsets.UTF_8) %>"/>
        <meta name="helpPage" content="change_a_user_password.html"/>
    </head>
    <body>

<% if (UserManager.getUserProvider().isReadOnly()) { %>
<div class="error">
    <fmt:message key="user.read_only"/>
</div>
<% } %>

<%  if (errors) { %>

    <admin:infoBox type="error">
        <fmt:message key="user.password.error_set_pwd" />
    </admin:infoBox>

<%  } else if (unsupported) { %>

    <admin:infoBox type="error">
        <fmt:message key="user.password.error_set_pwd_unsupp" />
    </admin:infoBox>

<%  } else if (request.getParameter("success") != null) { %>

    <admin:infoBox type="success">
        <fmt:message key="user.password.update" />
    </admin:infoBox>

<%  } %>

<p>
<fmt:message key="user.password.info" />
</p>

<form action="user-password.jsp" name="passform" method="post" autocomplete="off">
<input type="hidden" name="username" value="<%=StringUtils.escapeForXML(username) %>">
    <input type="hidden" name="csrf" value="${csrf}">

<fieldset>
    <legend><fmt:message key="user.password.change" /></legend>
    <div>
    <table style="width: 100%">
    <tbody>
        <tr>
            <td class="c1">
                <fmt:message key="user.create.username" />:
            </td>
            <td class="c2">
                <%= StringUtils.escapeHTMLTags(JID.unescapeNode(user.getUsername())) %>
            </td>
        </tr>
        <tr>
            <td class="c1">
                <label for="password"><fmt:message key="user.password.new_pwd" />:</label>
            </td>
            <td class="c2">
                <input type="password" id="password" name="password" value="" size="20" maxlength="50" autocomplete="new-password">
            </td>
        </tr>
        <tr>
            <td class="c1">
                <label for="passwordConfirm"><fmt:message key="user.password.confirm_new_pwd" />:</label>
            </td>
            <td class="c2">
                <input type="password" id="passwordConfirm" name="passwordConfirm" value="" size="20" maxlength="50" autocomplete="new-password">
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" value="<fmt:message key="user.password.update_pwd" />" name="update">
<input type="submit" value="<fmt:message key="global.cancel" />" name="cancel">
</form>

<script>
document.passform.password.focus();
</script>

    <%  // Disable the form if a read-only user provider.
    if (UserManager.getUserProvider().isReadOnly()) { %>

<script>
  function disable() {
    let limit = document.forms[0].elements.length;
    for (let i=0;i<limit;i++) {
      document.forms[0].elements[i].disabled = true;
    }
  }
  disable();
</script>
    <% } %>

    </body>
</html>
