/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.commands.admin;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;

/**
 * Command that allows to retrieve the number of disabled users
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0133.html#get-disabled-users-num">XEP-0133 Service Administration: Get Number of Disabled Users</a>
 */
public class GetNumberDisabledUsers extends AdHocCommand {

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        //Do nothing since there are no stages
    }

    @Override
    public void execute(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.result);

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.getnumberdisabledusers.form.field.disabledusersnum.label", preferredLocale));
        field.setVariable("disabledusersnum");

        // TODO improve on this, as this is not efficient on systems with large amounts of users.
        int count = 0;
        final LockOutManager lockOutManager = LockOutManager.getInstance();
        for (final User user : UserManager.getInstance().getUsers()) {
            if (lockOutManager.isAccountDisabled(user.getUsername())) {
                count++;
            }
        }
        field.addValue(count);

        command.add(form.getElement());
    }

    @Override
    protected List<Action> getActions(@Nonnull final SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#get-disabled-users-num";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("commands.admin.getnumberdisabledusers.label");
    }

    @Override
    protected Action getExecuteAction(@Nonnull final SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

    @Override
    public int getMaxStages(@Nonnull final SessionData data) {
        return 0;
    }
}
