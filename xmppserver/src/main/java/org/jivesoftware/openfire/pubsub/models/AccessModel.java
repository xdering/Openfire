/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2021 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.pubsub.models;

import org.dom4j.Element;
import org.jivesoftware.openfire.pubsub.Node;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.io.Serializable;

/**
 * Policy that defines who is allowed to subscribe and retrieve items.
 *
 * @author Matt Tucker
 */
public abstract class AccessModel implements Serializable {

    public static final AccessModel whitelist = new WhitelistAccess();
    public static final AccessModel open = new OpenAccess();
    public static final AccessModel authorize = new AuthorizeAccess();
    public static final AccessModel presence = new PresenceAccess();
    public static final AccessModel roster = new RosterAccess();

    /**
     * Returns the specific subclass of AccessModel as specified by the access
     * model name. If an unknown name is specified then an IllegalArgumentException
     * is going to be thrown.
     *
     * @param name the name of the subclass.
     * @return the specific subclass of AccessModel as specified by the access
     *         model name.
     */
    public static AccessModel valueOf(String name) {
        if ("open".equals(name)) {
            return open;
        }
        else if ("whitelist".equals(name)) {
            return whitelist;
        }
        else if ("authorize".equals(name)) {
            return authorize;
        }
        else if ("presence".equals(name)) {
            return presence;
        }
        else if ("roster".equals(name)) {
            return roster;
        }
        throw new IllegalArgumentException("Unknown access model: " + name);
    }

    /**
     * Returns the name as defined by the JEP-60 spec.
     *
     * @return the name as defined by the JEP-60 spec.
     */
    public abstract String getName();

    /**
     * Returns true if the entity is allowed to subscribe to the specified node.
     *
     * @param node       the node that the subscriber is trying to subscribe to.
     * @param owner      the JID of the owner of the subscription.
     * @param subscriber the JID of the subscriber.
     * @return true if the subscriber is allowed to subscribe to the specified node.
     */
    public abstract boolean canSubscribe(Node node, JID owner, JID subscriber);

    /**
     * Returns true if the entity is allowed to get the node published items.
     *
     * @param node       the node that the entity is trying to get the node's items.
     * @param owner      the JID of the owner of the subscription.
     * @param subscriber the JID of the subscriber.
     * @return true if the subscriber is allowed to get the node's published items.
     */
    public abstract boolean canAccessItems(Node node, JID owner, JID subscriber);

    /**
     * Returns the error condition that should be returned to the subscriber when
     * subscription is not allowed.
     *
     * @return the error condition that should be returned to the subscriber when
     *         subscription is not allowed.
     */
    public abstract PacketError.Condition getSubsriptionError();

    /**
     * Returns the error element that should be returned to the subscriber as
     * error detail when subscription is not allowed. The returned element is created
     * each time this message is sent so it is safe to include the returned element in
     * the parent element.
     *
     * @return the error element that should be returned to the subscriber as
     *         error detail when subscription is not allowed.
     */
    public abstract Element getSubsriptionErrorDetail();

    /**
     * Returns true if the new subscription should be authorized by a node owner.
     *
     * @return true if the new subscription should be authorized by a node owner.
     */
    public abstract boolean isAuthorizationRequired();
}
