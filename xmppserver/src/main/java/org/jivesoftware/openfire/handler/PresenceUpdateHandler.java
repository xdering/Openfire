/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.ClusteredCacheEntryListener;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * Implements the presence protocol. Clients use this protocol to
 * update presence and roster information.
 * <p>
 * The handler must properly detect the presence type, update the user's roster,
 * and inform presence subscribers of the session's updated presence
 * status. Presence serves many purposes in Jabber so this handler will
 * likely be the most complex of all handlers in the server.
 * </p>
 * <p>
 * There are four basic types of presence updates:
 * </p>
 * <ul>
 * <li>Simple presence updates - addressed to the server (or to address), these updates
 * are properly addressed by the server, and multicast to
 * interested subscribers on the user's roster. An empty, missing,
 * or "unavailable" type attribute indicates a simple update (there
 * is no "available" type although it should be accepted by the server.
 * <li>Directed presence updates - addressed to particular jabber entities,
 * these presence updates are properly addressed and directly delivered
 * to the entity without broadcast to roster subscribers. Any update type
 * is possible except those reserved for subscription requests.
 * <li>Subscription requests - these updates request presence subscription
 * status changes. Such requests always affect the roster.  The server must:
 * <ul>
 * <li>update the roster with the proper subscriber info
 * <li>push the roster changes to the user
 * <li>forward the update to the correct parties.
 * </ul>
 * The valid types include "subscribe", "subscribed", "unsubscribed",
 * and "unsubscribe".
 * <li>XMPPServer probes - Provides a mechanism for servers to query the presence
 * status of users on another server. This allows users to immediately
 * know the presence status of users when they come online rather than way
 * for a presence update broadcast from the other server or tracking them
 * as they are received.  Requires S2S capabilities.
 * </ul>
 *
 * @author Iain Shigeoka
 */
public class PresenceUpdateHandler extends BasicModule implements ChannelHandler<Presence>, ClusterEventListener {

    private static final Logger Log = LoggerFactory.getLogger(PresenceUpdateHandler.class);

    public static final String PRESENCE_CACHE_NAME = "Directed Presences";

    /**
     * Keeps track of entities that sent directed presences to other entities. In this map
     * we keep track of every directed presence no matter if the recipient was hosted in
     * this JVM or another cluster node.
     *
     * Key: sender, Value: list of DirectedPresences
     */
    private Cache<String, ConcurrentLinkedQueue<DirectedPresence>> directedPresencesCache;

    /**
     * Same as the directedPresencesCache but only keeps directed presences sent from
     * users connected to this JVM.
     */
    private final Map<String, ConcurrentLinkedQueue<DirectedPresence>> localDirectedPresences;

    /**
     * A map that, for all nodes in the cluster except for the local one, tracks what directed presences a particular
     * user has sent, as tracked in #directedPresencesCache. Every key of the inner map corresponds to a key in that
     * cache (which is the JID of the sender of the direct presence). Every String in the collection that is the mapped
     * value corresponds to the recipient of the DirectedPresence as maintained in the cache.
     *
     * Whenever any cluster node adds or removes an entry to {@link #directedPresencesCache}, this map, on
     * <em>every</em> cluster node, will receive a corresponding update. This ensures that every cluster node has a
     * complete overview of all cache entries (or at least the most important details of each entry - we should avoid
     * duplicating the entire cache, as that somewhat defaults the purpose of having the cache).
     *
     * This map is to be used when a cluster node unexpectedly leaves the cluster. As the cache implementation uses a
     * distributed data structure that gives no guarantee that all data is visible to all cluster nodes at any given
     * time, the cache cannot be trusted to 'locally' contain all information that was added to it by the disappeared
     * node (nor can that node be contacted to retrieve the missing data, because it has already disappeared).
     */
    private final Map<NodeID, Map<String /* jid of sender */, Collection<String /* jid of recipients */>>> directedPresenceAddressingByClusterNode = new ConcurrentHashMap<>();

    private RoutingTable routingTable;
    private RosterManager rosterManager;
    private XMPPServer localServer;
    private PresenceManager presenceManager;
    private PacketDeliverer deliverer;
    private OfflineMessageStore messageStore;
    private SessionManager sessionManager;
    private UserManager userManager;

    public PresenceUpdateHandler() {
        super("Presence update handler");
        localDirectedPresences = new ConcurrentHashMap<>();
    }

    private void process(Presence presence, ClientSession session) throws UnauthorizedException, PacketException {
        try {
            Presence.Type type = presence.getType();
            // Available
            if (type == null) {
                if (session != null && session.getStatus() == Session.Status.CLOSED) {
                    Log.warn("Rejected available presence: " + presence + " - " + session);
                    return;
                }

                if (session != null) {
                    session.setPresence(presence);
                }

                broadcastUpdate(presence.createCopy());

                if (session != null && !session.isInitialized()) {
                    initSession(session);
                    session.setInitialized(true);
                }

                // Notify the presence manager that the user is now available. The manager may
                // remove the last presence status sent by the user when he went offline.
                presenceManager.userAvailable(presence);
            }
            else if (Presence.Type.unavailable == type) {
                if (session != null) {
                    session.setPresence(presence);
                }
                broadcastUpdate(presence.createCopy());
                broadcastUnavailableForDirectedPresences(presence);
                // Notify the presence manager that the user is now unavailable. The manager may
                // save the last presence status sent by the user and keep track when the user
                // went offline.
                presenceManager.userUnavailable(presence);
            }
            else {
                presence = presence.createCopy();
                if (session != null) {
                    presence.setFrom(new JID(null, session.getServerName(), null, true));
                    presence.setTo(session.getAddress());
                }
                else {
                    JID sender = presence.getFrom();
                    presence.setFrom(presence.getTo());
                    presence.setTo(sender);
                }
                presence.setError(PacketError.Condition.bad_request);
                deliverer.deliver(presence);
            }

        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error") + ". Triggered by packet: " + presence, e);
        }
    }

    /**
     * Handle presence updates that affect roster subscriptions.
     *
     * @param presence The presence presence to handle
     * @throws PacketException if the packet is null or the packet could not be routed.
     */
    @Override
    public void process(Presence presence) throws PacketException {
        try {
            process(presence, sessionManager.getSession(presence.getFrom()));
        }
        catch (UnauthorizedException e) {
            try {
                LocalSession session = (LocalSession) sessionManager.getSession(presence.getFrom());
                presence = presence.createCopy();
                if (session != null) {
                    presence.setFrom(new JID(null, session.getServerName(), null, true));
                    presence.setTo(session.getAddress());
                }
                else {
                    JID sender = presence.getFrom();
                    presence.setFrom(presence.getTo());
                    presence.setTo(sender);
                }
                presence.setError(PacketError.Condition.not_authorized);
                deliverer.deliver(presence);
            }
            catch (Exception err) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), err);
            }
        }
    }

    /**
     * A session that has transitioned to available status must be initialized.
     * This includes:
     * <ul>
     * <li>Sending all offline presence subscription requests</li>
     * <li>Sending offline messages</li>
     * </ul>
     *
     * @param session The session being updated
     * @throws UserNotFoundException If the user being updated does not exist
     */
    private void initSession(ClientSession session) throws UserNotFoundException {

        // Only user sessions need to be authenticated
        if (userManager.isRegisteredUser(session.getAddress(), false)) {
            String username = session.getAddress().getNode();

            // Send pending subscription requests to user if roster service is enabled
            if (RosterManager.isRosterServiceEnabled()) {
                Roster roster = rosterManager.getRoster(username);
                for (RosterItem item : roster.getRosterItems()) {
                    if (item.getRecvStatus() == RosterItem.RecvType.SUBSCRIBE) {
                        Presence presence = item.getSubscribeStanza();
                        presence.setTo(session.getAddress().asBareJID());
                        session.process(presence);
                    } else if (item.getRecvStatus() == RosterItem.RecvType.UNSUBSCRIBE) {
                        session.process(createSubscribePresence(item.getJid(),
                                session.getAddress().asBareJID(), false));
                    }
                    if (item.getSubStatus() == RosterItem.SUB_TO
                            || item.getSubStatus() == RosterItem.SUB_BOTH) {
                        presenceManager.probePresence(session.getAddress(), item.getJid());
                    }
                }
            }
            if (session.canFloodOfflineMessages()) {
                // deliver offline messages if any
                Collection<OfflineMessage> messages = messageStore.getMessages(username, true);
                for (Message message : messages) {
                    session.process(message);
                }
            }
        }
    }

    public Presence createSubscribePresence(JID senderAddress, JID targetJID, boolean isSubscribe) {
        Presence presence = new Presence();
        presence.setFrom(senderAddress);
        presence.setTo(targetJID);
        if (isSubscribe) {
            presence.setType(Presence.Type.subscribe);
        }
        else {
            presence.setType(Presence.Type.unsubscribe);
        }
        return presence;
    }

    /**
     * Broadcast the given update to all subscribers. We need to:
     * <ul>
     * <li>Query the roster table for subscribers</li>
     * <li>Iterate through the list and send the update to each subscriber</li>
     * </ul>
     * <p/>
     * Is there a safe way to cache the query results while maintaining
     * integrity with roster changes?
     *
     * @param update The update to broadcast
     */
    private void broadcastUpdate(Presence update) {
        if (update.getFrom() == null) {
            return;
        }
        if (localServer.isLocal(update.getFrom())) {
            // Do nothing if roster service is disabled
            if (!RosterManager.isRosterServiceEnabled()) {
                return;
            }
            // Local updates can simply run through the roster of the local user
            String name = update.getFrom().getNode();
            try {
                if (name != null && !name.isEmpty()) {
                    Roster roster = rosterManager.getRoster(name);
                    roster.broadcastPresence(update);
                }
            }
            catch (UserNotFoundException e) {
                Log.warn("Presence being sent from unknown user " + name, e);
            }
            catch (PacketException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
        else {
            // Foreign updates will do a reverse lookup of entries in rosters
            // on the server
            Log.warn("Presence requested from server "
                    + localServer.getServerInfo().getXMPPDomain()
                    + " by unknown user: " + update.getFrom());
        }
    }

    /**
     * Notification method sent to this handler when a user has sent a directed
     * presence to an entity. If the sender of the presence is local (to this server)
     * and the target entity does not belong to the user's roster then update the
     * registry of sent directed presences by the user.
     *
     * @param update  the directed Presence sent by the user to an entity.
     * @param handlerJID the JID of the handler that will receive/handle/process the sent packet.
     * @param jid     the recipient specified in the packet to handle.
     */
    public void directedPresenceSent(Presence update, JID handlerJID, String jid) {
        if (update.getFrom() == null) {
            return;
        }
        if (localServer.isLocal(update.getFrom())) {
            boolean keepTrack = false;
            String name = update.getFrom().getNode();
            if (name != null && !name.isEmpty()) {
                // Keep track of all directed presences if roster service is disabled
                if (!RosterManager.isRosterServiceEnabled()) {
                    keepTrack = true;
                }
                else {
                    try {
                        Roster roster = rosterManager.getRoster(name);
                        // If the directed presence was sent to an entity that is not in the user's
                        // roster, keep a registry of this so that when the user goes offline we
                        // will be able to send the unavailable presence to the entity
                        RosterItem rosterItem = null;
                        try {
                            rosterItem = roster.getRosterItem(update.getTo());
                        }
                        catch (UserNotFoundException e) {
                            // Ignore
                        }
                        if (rosterItem == null ||
                                RosterItem.SUB_NONE == rosterItem.getSubStatus() ||
                                RosterItem.SUB_TO == rosterItem.getSubStatus()) {
                            keepTrack = true;
                        }
                    }
                    catch (UserNotFoundException e) {
                        Log.warn("Presence being sent from unknown user " + name, e);
                    }
                    catch (PacketException e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    }
                }
            }
            else if (update.getFrom().getResource() != null){
                // Keep always track of anonymous users directed presences
                keepTrack = true;
            }
            if (keepTrack) {
                String sender = update.getFrom().toString();
                Lock lock = directedPresencesCache.getLock(sender);
                lock.lock();
                try {
                    ConcurrentLinkedQueue<DirectedPresence> directedPresences = directedPresencesCache.get(sender);
                    if (Presence.Type.unavailable.equals(update.getType())) {
                        if (directedPresences != null) {
                            // It's a directed unavailable presence
                            for (DirectedPresence directedPresence : directedPresences) {
                                if (directedPresence.getHandler().equals(handlerJID)) {
                                    directedPresence.removeReceiver(jid);
                                    if (directedPresence.isEmpty()) {
                                        directedPresences.remove(directedPresence);
                                    }
                                    break;
                                }
                            }
                            if (directedPresences.isEmpty()) {
                                // Remove the user from the registry since the list of directed
                                // presences is empty
                                directedPresencesCache.remove(sender);
                                localDirectedPresences.remove(sender);
                            }
                            else {
                                directedPresencesCache.put(sender, directedPresences);
                                localDirectedPresences.put(sender, directedPresences);
                            }
                        }
                    }
                    else {
                        if (directedPresences == null) {
                            // We are using a set to avoid duplicate jids in case the user
                            // sends several directed presences to the same handler. The Map also
                            // ensures that if the user sends several presences to the same handler
                            // we will have only one entry in the Map
                            directedPresences = new ConcurrentLinkedQueue<>();
                        }
                        // Add the handler to the list of handler that processed the directed
                        // presence sent by the user. This handler will be used to send
                        // the unavailable presence when the user goes offline
                        DirectedPresence affectedDirectedPresence = null;
                        for (DirectedPresence directedPresence : directedPresences) {
                            if (directedPresence.getHandler().equals(handlerJID)) {
                                affectedDirectedPresence = directedPresence;
                                break;
                            }
                        }

                        if (affectedDirectedPresence == null) {
                            affectedDirectedPresence = new DirectedPresence(handlerJID);
                            directedPresences.add(affectedDirectedPresence);
                        }
                        affectedDirectedPresence.addReceiver(jid);

                        directedPresencesCache.put(sender, directedPresences);
                        localDirectedPresences.put(sender, directedPresences);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * Sends an unavailable presence to the entities that sent a directed (available) presence
     * to other entities.
     *
     * @param update the unavailable presence sent by the user.
     */
    private void broadcastUnavailableForDirectedPresences(Presence update) {
        JID from = update.getFrom();
        if (from == null) {
            return;
        }
        if (localServer.isLocal(from)) {
            // Remove the registry of directed presences of this user
            Collection<DirectedPresence> directedPresences;
            
            Lock lock = directedPresencesCache.getLock(from.toString());
            lock.lock();
            try {
                directedPresences = directedPresencesCache.remove(from.toString());
            } finally {
                lock.unlock();
            }
            
            if (directedPresences != null) {
                // Iterate over all the entities that the user sent a directed presence
                for (DirectedPresence directedPresence : directedPresences) {
                    for (String receiver : directedPresence.getReceivers()) {
                        Presence presence = update.createCopy();
                        presence.setTo(receiver);
                        localServer.getPresenceRouter().route(presence);
                    }
                }
                localDirectedPresences.remove(from.toString());
            }
        }
    }

    public boolean hasDirectPresence(JID ownerJID, JID recipientJID) {
        if (recipientJID == null) {
            return false;
        }
        Collection<DirectedPresence> directedPresences = directedPresencesCache.get(ownerJID.toString());
        if (directedPresences != null) {
            String recipient = recipientJID.toBareJID();
            for (DirectedPresence directedPresence : directedPresences) {
                for (String receiver : directedPresence.getReceivers()) {
                    if (receiver.contains(recipient)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void removeDirectPresence(JID ownerJID, JID recipientJID) {
        if (recipientJID == null) {
            return;
        }
        Collection<DirectedPresence> directedPresences = directedPresencesCache.get(ownerJID.toString());
        if (directedPresences != null) {
            String recipient = recipientJID.toBareJID();
            for (DirectedPresence directedPresence : directedPresences) {
                directedPresence.removeReceiver(recipient);
            }
        }
    }

    /**
     * Removes directed presences sent to entities that are no longer available.
     */
    public void removedExpiredPresences() {
        Map<String, Collection<DirectedPresence>> copy =
                new HashMap<>(localDirectedPresences);
        for (Map.Entry<String, Collection<DirectedPresence>> entry : copy.entrySet()) {
            for (DirectedPresence directedPresence : entry.getValue()) {
                if (!routingTable.hasClientRoute(directedPresence.getHandler()) &&
                        !routingTable.hasComponentRoute(directedPresence.getHandler())) {
                    Collection<DirectedPresence> presences = localDirectedPresences.get(entry.getKey());
                    presences.remove(directedPresence);
                    if (presences.isEmpty()) {
                        localDirectedPresences.remove(entry.getKey());
                    }
                }
            }
        }
    }

    /**
     * Checks if the packet is a presence stanza that intends to reflect an update back to the client from which the update originated.
     *
     * @param packet The stanza (cannot be null.
     * @return true if the packet is presence reflection, otherwise false.
     */
    public static boolean isPresenceUpdateReflection( final Packet packet ) {
        return packet instanceof Presence && packet.getTo() != null && packet.getTo().equals( packet.getFrom() );
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        localServer = server;
        rosterManager = server.getRosterManager();
        presenceManager = server.getPresenceManager();
        deliverer = server.getPacketDeliverer();
        messageStore = server.getOfflineMessageStore();
        sessionManager = server.getSessionManager();
        userManager = server.getUserManager();
        routingTable = server.getRoutingTable();
        directedPresencesCache = CacheFactory.createCache(PRESENCE_CACHE_NAME);
        // TODO Add as route listener (to remove direct presences info for removed routes). Mainly for c2s sessions which is uncommon.
        // Listen to cluster events
        ClusterManager.addListener(this, 20);
    }

    @Override
    public void joinedCluster() {
        // The local node joined a cluster.
        //
        // Upon joining a cluster, clustered caches are reset to their clustered equivalent (by the swap from the local
        // cache implementation to the clustered cache implementation that's done in the implementation of
        // org.jivesoftware.util.cache.CacheFactory.joinedCluster). This means that they now hold data that's
        // available on all other cluster nodes. Data that's available on the local node needs to be added again.
        restoreCacheContent();

        final DirectedPresenceListener listener = new DirectedPresenceListener();

        Log.debug("Simulate 'entryAdded' for all data that already exists elsewhere in the cluster.");
        directedPresencesCache.entrySet().stream()
            // this filter isn't needed if we do this before restoreCacheContent.
            .filter(entry -> !entry.getValue().isEmpty() && !Arrays.equals(entry.getValue().peek().getNodeID(), XMPPServer.getInstance().getNodeID().toByteArray()))
            .forEach(entry -> {
                assert entry.getValue().peek() != null; // should be impossible given the filter above.
                // We are assuming that the nodeID for every directed presence in the collection is equal.
                final NodeID nodeID = NodeID.getInstance(entry.getValue().peek().getNodeID());
                listener.entryAdded(entry.getKey(), entry.getValue(), nodeID);
            });

        // Add the entry listener to the cache. Note that, when #joinedCluster() fired, the cache will _always_ have been replaced,
        // meaning that it won't have old event listeners. When #leaveCluster() fires, the cache will be destroyed. This
        // takes away the need to explicitly deregister the listener in that case.
        final boolean includeValues = true; // This event handler needs to operate on cache values. We can't reduce overhead by suppressing value transmission.
        directedPresencesCache.addClusteredCacheEntryListener(listener, includeValues, false);
    }

    @Override
    public void joinedCluster(byte[] nodeID) {
        // Another node joined a cluster that we're already part of. It is expected that
        // the implementation of #joinedCluster() as executed on the cluster node that just
        // joined will synchronize all relevant data. This method need not do anything.
    }

    @Override
    public void leftCluster() {
        // The local cluster node left the cluster.
        if (XMPPServer.getInstance().isShuttingDown()) {
            // Do not put effort in restoring the correct state if we're shutting down anyway.
            return;
        }

        // Upon leaving a cluster, clustered caches are reset to their local equivalent (by the swap from the clustered
        // cache implementation to the default cache implementation that's done in the implementation of
        // org.jivesoftware.util.cache.CacheFactory.leftCluster). This means that they now hold no data (as a new cache
        // has been created). Data that's available on the local node needs to be added again.
        restoreCacheContent();

        for (NodeID nodeID : new HashSet<>(directedPresenceAddressingByClusterNode.keySet())) {
            // Clean up directed presences sent from entities hosted in the leaving node to local entities
            // Clean up directed presences sent to entities hosted in the leaving node from local entities
            cleanupDirectedPresences(nodeID);
        }
    }

    @Override
    public void leftCluster(byte[] nodeID) {
        // Another node left the cluster.

        // Clean up directed presences sent from entities hosted in the leaving node to local entities
        // Clean up directed presences sent to entities hosted in the leaving node from local entities
        cleanupDirectedPresences(NodeID.getInstance(nodeID));
    }

    @Override
    public void markedAsSeniorClusterMember() {
        // Do nothing
    }

    /**
     * When the local node is joining or leaving a cluster, {@link org.jivesoftware.util.cache.CacheFactory} will swap
     * the implementation used to instantiate caches. This causes the cache content to be 'reset': it will no longer
     * contain the data that's provided by the local node. This method restores data that's provided by the local node
     * in the cache. It is expected to be invoked right after joining ({@link #joinedCluster()} or leaving
     * ({@link #leftCluster()} a cluster.
     */
    private void restoreCacheContent() {
        Log.trace( "Restoring cache content for cache '{}' by adding all directed presences that are provided by the local cluster node.", directedPresencesCache.getName() );

        // Populate directedPresencesCache with local content since when not in a cluster
        // we could still send directed presences to entities that when connected to a cluster
        // they will be replicated. An example would be MUC rooms.
        for (Map.Entry<String, ConcurrentLinkedQueue<DirectedPresence>> entry : localDirectedPresences.entrySet()) {
            if (entry.getValue().isEmpty()) {
                Log.warn("Skipping empty directed presences when joining cluster for sender: {}", entry.getKey());
                continue;
            }

            // TODO perhaps we should not lock for every entry. Instead, lock it
            // once (using a LOCK_ALL global key), and handle iterations in
            // one go. We should first make sure that this doesn't lead to
            // deadlocks though! The tryLock() mechanism could be used to first
            // try one approach, but fall back on the other approach.
            Lock lock = directedPresencesCache.getLock(entry.getKey());
            lock.lock();
            try {
                directedPresencesCache.put(entry.getKey(), entry.getValue());
            } finally {
                lock.unlock();
            }
        }

    }

    private void cleanupDirectedPresences(final NodeID nodeID) {
        Log.debug("Remove traces of directed presences sent from node '{}' that is gone to entities hosted in this JVM.", nodeID);
        final Map<String, Collection<String>> senders = directedPresenceAddressingByClusterNode.remove(nodeID);
        if (senders != null) {
            for (final Map.Entry<String, Collection<String>> entry : senders.entrySet()) {
                final String sender = entry.getKey();
                final Collection<String> receivers = entry.getValue();
                for (final String receiver : receivers) {
                    try {
                        final Presence presence = new Presence(Presence.Type.unavailable);
                        presence.setFrom(sender);
                        presence.setTo(receiver);
                        XMPPServer.getInstance().getPresenceRouter().route(presence);
                    }
                    catch (final PacketException e) {
                        Log.error("Failed to cleanup directed presences", e);
                    }
                }
            }
        }
    }

    /**
     * EntryListener implementation tracks events for caches of c2s sessions.
     */
    private class DirectedPresenceListener implements ClusteredCacheEntryListener<String, ConcurrentLinkedQueue<DirectedPresence>> {

        @Override
        public void entryAdded(@Nonnull final String sender, @Nullable final ConcurrentLinkedQueue<DirectedPresence> newValue, @Nonnull final NodeID nodeID) {
            if (newValue == null) {
                throw new IllegalStateException("Null value detected. This listener was probably registered using a configuration that suppresses values. This listener requires values, so it can't properly function in this configuration.");
            }

            // Check if the directed presence was sent to an entity hosted by this JVM
            final RoutingTable routingTable = XMPPServer.getInstance().getRoutingTable();
            final Set<String> handlers = newValue
                .stream()
                .filter(dp -> routingTable.isLocalRoute(dp.getHandler()))
                .flatMap(dp -> dp.getReceivers().stream())
                .collect(Collectors.toSet());

            if (!handlers.isEmpty()) {
                Map<String, Collection<String>> senders = directedPresenceAddressingByClusterNode.get(nodeID);
                if (senders == null) {
                    senders = new ConcurrentHashMap<>();
                    directedPresenceAddressingByClusterNode.put(nodeID, senders);
                }
                senders.put(sender, handlers);
            }
        }

        @Override
        public void entryUpdated(@Nonnull final String sender, @Nullable final ConcurrentLinkedQueue<DirectedPresence> oldValue, @Nullable final ConcurrentLinkedQueue<DirectedPresence> newValue, @Nonnull final NodeID nodeID) {
            if (newValue == null) {
                throw new IllegalStateException("Null value detected. This listener was probably registered using a configuration that suppresses values. This listener requires values, so it can't properly function in this configuration.");
            }

            // Check if the directed presence was sent to an entity hosted by this JVM
            final RoutingTable routingTable = XMPPServer.getInstance().getRoutingTable();

            final Set<String> handlers = newValue
                .stream()
                .filter(dp -> routingTable.isLocalRoute(dp.getHandler()))
                .flatMap(dp -> dp.getReceivers().stream())
                .collect(Collectors.toSet());

            Map<String, Collection<String>> senders = directedPresenceAddressingByClusterNode.get(nodeID);
            if (senders == null) {
                senders = new ConcurrentHashMap<>();
                directedPresenceAddressingByClusterNode.put(nodeID, senders);
            }
            if (!handlers.isEmpty()) {
                senders.put(sender, handlers);
            } else {
                // Remove any traces of the sender since no directed presence was sent to this JVM
                senders.remove(sender);
            }
        }

        @Override
        public void entryRemoved(@Nonnull final String sender, @Nullable final ConcurrentLinkedQueue<DirectedPresence> oldValue, @Nonnull final NodeID nodeID) {
            if (oldValue != null) { // Otherwise there is nothing to remove
                final Map<String, Collection<String>> entry = directedPresenceAddressingByClusterNode.get(nodeID);
                if (entry != null) {
                    entry.remove(sender);
                }
            }
        }

        @Override
        public void entryEvicted(@Nonnull final String sender, @Nullable final ConcurrentLinkedQueue<DirectedPresence> oldValue, @Nonnull final NodeID nodeID) {
            entryRemoved(sender, oldValue, nodeID);
        }

        @Override
        public void mapEvicted(@Nonnull final NodeID nodeID) {
            final Map<String, Collection<String>> entry = directedPresenceAddressingByClusterNode.get(nodeID);
            if (entry != null) {
                entry.clear();
            }
        }

        @Override
        public void mapCleared(@Nonnull final NodeID nodeID) {
            mapEvicted(nodeID);
        }
    }
}
