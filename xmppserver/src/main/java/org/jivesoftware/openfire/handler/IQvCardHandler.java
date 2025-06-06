/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2024 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.handler;

import java.util.Iterator;
import java.util.Locale;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

/**
 * Implements the TYPE_IQ vcard-temp protocol. Clients
 * use this protocol to set and retrieve the vCard information
 * associated with someone's account.
 * <p>
 * A 'get' query retrieves the vcard for the addressee.
 * A 'set' query sets the vcard information for the sender's account.
 * </p>
 * <p>
 * Currently an empty implementation to allow usage with normal
 * clients. Future implementation needed.
 * </p>
 * <h2>Assumptions</h2>
 * This handler assumes that the request is addressed to the server.
 * An appropriate TYPE_IQ tag matcher should be placed in front of this
 * one to route TYPE_IQ requests not addressed to the server to
 * another channel (probably for direct delivery to the recipient).
 * <h2>Warning</h2>
 * There should be a way of determining whether a session has
 * authorization to access this feature. I'm not sure it is a good
 * idea to do authorization in each handler. It would be nice if
 * the framework could assert authorization policies across channels.
 * <h2>Warning</h2>
 * I have noticed incompatibility between vCard XML used by Exodus and Psi.
 * There is a new vCard standard going through the JSF JEP process. We might
 * want to start either standardizing on clients (probably the most practical),
 * sending notices for non-conformance (useful),
 * or attempting to translate between client versions (not likely).
 *
 * @author Iain Shigeoka
 */
public class IQvCardHandler extends IQHandler {

    private static final Logger Log = LoggerFactory.getLogger(IQvCardHandler.class);

    private final IQHandlerInfo info;
    private XMPPServer server;
    private UserManager userManager;

    public IQvCardHandler() {
        super("XMPP vCard Handler");
        info = new IQHandlerInfo("vCard", "vcard-temp");
    }

    @Override
    public IQ handleIQ(IQ packet) throws UnauthorizedException, PacketException {
        IQ result = IQ.createResultIQ(packet);
        IQ.Type type = packet.getType();
        final Locale localeForSession = SessionManager.getInstance().getLocaleForSession(packet.getFrom());
        if (type.equals(IQ.Type.set)) {
            try {
                // OF-2838: Return an error when entity is trying to update another entity's VCard.
                if (packet.getTo() != null && !packet.getTo().asBareJID().equals(packet.getFrom().asBareJID())) {
                    result.setError(PacketError.Condition.forbidden);
                    result.getError().setText(LocaleUtils.getLocalizedString("vcard.forbidden_entity", localeForSession), localeForSession != null ? localeForSession.getLanguage() : null);
                    return result;
                }
                User user = userManager.getUser(packet.getFrom().getNode());
                Element vcard = packet.getChildElement();
                if (vcard != null) {
                    try {
                        VCardManager.getInstance().setVCard( user.getUsername(), vcard );
                    } catch ( UnsupportedOperationException e ) {
                        Log.debug( "Entity '{}' tried to set VCard, but the configured VCard provider is read-only. An IQ error will be returned to sender.", packet.getFrom() );
                        // VCards can include binary data. Let's not echo that back in the error.
                        // result.setChildElement( packet.getChildElement().createCopy() );

                        result.setError( PacketError.Condition.not_allowed );
                        result.getError().setText( LocaleUtils.getLocalizedString( "vcard.read_only", localeForSession ), localeForSession != null ? localeForSession.getLanguage() : null);
                    }
                }
            }
            catch (UserNotFoundException e) {
                // VCards can include binary data. Let's not echo that back in the error.
                // result.setChildElement( packet.getChildElement().createCopy() );

                result.setError(PacketError.Condition.item_not_found);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
                result.setError(PacketError.Condition.internal_server_error);
            }
        }
        else if (type.equals(IQ.Type.get)) {
            JID recipient = packet.getTo();
            // If no TO was specified then get the vCard of the sender of the packet
            if (recipient == null) {
                if (packet.getFrom() == null) {
                    Log.warn("Unable to process a stanza that has no 'to' and 'from' attribute values: {}", packet.toXML(), new Throwable());
                    result.setChildElement(packet.getChildElement().createCopy());
                    result.setError(PacketError.Condition.internal_server_error);
                    result.getError().setText("Unable to process a stanza that has no 'to' and 'from' attribute values.");
                    return result;
                }
                recipient = packet.getFrom();
            }
            // Only try to get the vCard values of non-anonymous users
            if (recipient.getNode() != null && server.isLocal(recipient)) {
                VCardManager vManager = VCardManager.getInstance();
                Element userVCard = vManager.getVCard(recipient.getNode());
                if (userVCard != null) {
                    // Check if the requester wants to ignore some vCard's fields
                    Element filter = packet.getChildElement()
                            .element(QName.get("filter", "vcard-temp-filter"));
                    if (filter != null) {
                        // Create a copy so we don't modify the original vCard
                        userVCard = userVCard.createCopy();
                        // Ignore fields requested by the user
                        for (Iterator<Element> toFilter = filter.elementIterator(); toFilter.hasNext();)
                        {
                            Element field = toFilter.next();
                            Element fieldToRemove = userVCard.element(field.getName());
                            if (fieldToRemove != null) {
                                fieldToRemove.detach();
                            }
                        }
                    }
                    result.setChildElement(userVCard);
                } else {
                    if (recipient.getNode().equals(packet.getFrom().getNode())) {
                        // When requesting your _own_ vcard, a valid response is either an empty card or an error (per XEP-0054 section 3.1). Openfire traditionally used an empty vcard.
                        result.setChildElement("vCard", "vcard-temp");
                    } else {
                        // OF-2839: When requesting another entities vcard, an error must be returned (per XEP-0054 section 3.3)
                        // RFC 6121 section 8.5.1 mandates '<service-unavailable/> while XEP-0054 suggests either <service-unavailable/> or <item-not-found/>. Let's go with the common term.
                        result = IQ.createResultIQ(packet);
                        result.setChildElement(packet.getChildElement().createCopy());
                        result.setError(PacketError.Condition.service_unavailable);
                    }
                }
            }
        }
        else {
            result.setChildElement(packet.getChildElement().createCopy());
            result.setError(PacketError.Condition.not_acceptable);
        }
        return result;
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        this.server = server;
        userManager = server.getUserManager();
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }
}
