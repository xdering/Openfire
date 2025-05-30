/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.group;

import java.util.Collection;
import java.util.Map;

import org.jivesoftware.util.PersistableMap;
import org.xmpp.packet.JID;

/**
 * Provider interface for groups.
 *
 * As grouped entities are thought to represent end-user entities, a group can be thought of as a collection of
 * <em>bare</em> (not full) JIDs. The method of the Group class and its associated API will accept both bare and full
 * JIDs, but are expected to 'cast down' a full JID to a bare JID prior to processing it.
 *
 * Developers that wish to integrate with their own group system must implement this class and then register the
 * implementation with Openfire in the {@code openfire.xml} file. An entry in that file would look like the following:
 *
 * <pre>
 *   &lt;provider&gt;
 *     &lt;group&gt;
 *       &lt;className&gt;com.foo.auth.CustomGroupProvider&lt;/className&gt;
 *     &lt;/group&gt;
 *   &lt;/provider&gt;</pre>
 *
 * @see AbstractGroupProvider
 * 
 * @author Matt Tucker
 */
public interface GroupProvider {

    /**
     * Creates a group with the given name (optional operation).
     * <p>
     * The provider is responsible for setting the creation date and
     * modification date to the current date/time.
     * </p>
     *
     * @param name name of the group.
     * @return the newly created group.
     * @throws GroupAlreadyExistsException if a group with the same name already
     *      exists.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     * @throws GroupNameInvalidException if the provided new name is an unacceptable value.
     */
    Group createGroup(String name) throws GroupAlreadyExistsException, GroupNameInvalidException;

    /**
     * Deletes the group (optional operation).
     *
     * @param name the name of the group to delete.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     */
    void deleteGroup(String name) throws GroupNotFoundException;

    /**
     * Returns a group based on its name.
     *
     * @param name the name of the group.
     * @return the group with the given name.
     * @throws GroupNotFoundException If no group with that ID could be found
     */
    Group getGroup(String name) throws GroupNotFoundException;

    /**
     * Sets the name of a group to a new name.
     *
     * @param oldName the current name of the group.
     * @param newName the desired new name of the group.
     * @throws GroupAlreadyExistsException if a group with the same name already
     *      exists.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     * @throws GroupNotFoundException if the provided old name does not refer to an existing group.
     * @throws GroupNameInvalidException if the provided new name is an unacceptable value.
     */
    void setName(String oldName, String newName) throws GroupAlreadyExistsException, GroupNameInvalidException, GroupNotFoundException;

    /**
     * Updates the group's description.
     *
     * @param name the group name.
     * @param description the group description.
     * @throws GroupNotFoundException if no existing group could be found to update.
     */
    void setDescription(String name, String description) throws GroupNotFoundException;

    /**
     * Returns the number of groups in the system.
     *
     * @return the number of groups in the system.
     */
    int getGroupCount();

    /**
     * Returns the Collection of all group names in the system.
     *
     * @return the Collection of all groups.
     */
    Collection<String> getGroupNames();

    /**
     * Returns true if this GroupProvider allows group sharing. Shared groups
     * enable roster sharing.
     *
     * @return true if the group provider supports group sharing.
     */
    boolean isSharingSupported();

    /**
     * Returns an unmodifiable Collection of all shared groups in the system.
     *
     * @return unmodifiable Collection of all shared groups in the system.
     */
    Collection<String> getSharedGroupNames();
    
    /**
     * Returns an unmodifiable Collection of all shared groups in the system for a given user.
     *
     * Implementations should use the bare JID representation of the JID passed as an argument to this method.
     *
     * @param user The bare JID for the user (node@domain)
     * @return unmodifiable Collection of all shared groups in the system for a given user.
     */
    Collection<String> getSharedGroupNames(JID user);
    
    /**
     * Returns an unmodifiable Collection of all public shared groups in the system.
     *
     * @return unmodifiable Collection of all public shared groups in the system.
     */
    Collection<String> getPublicSharedGroupNames();
    
    /**
     * Returns an unmodifiable Collection of groups that are visible
     * to the members of the given group.
     * 
     * @param userGroup The given group
     * @return unmodifiable Collection of group names that are visible
     * to the given group.
     */
    Collection<String> getVisibleGroupNames(String userGroup);
    
    /**
     * Returns the Collection of all groups in the system.
     *
     * @param startIndex start index in results.
     * @param numResults number of results to return.
     * @return the Collection of all group names given the
     *      {@code startIndex} and {@code numResults}.
     */
    Collection<String> getGroupNames(int startIndex, int numResults);

    /**
     * Returns the Collection of group names that an entity belongs to.
     *
     * Implementations should use the bare JID representation of the JID passed as an argument to this method.
     *
     * @param user the (bare) JID of the entity.
     * @return the Collection of group names that the user belongs to.
     */
    Collection<String> getGroupNames(JID user);

    /**
     * Adds an entity to a group (optional operation).
     *
     * Implementations should use the bare JID representation of the JID passed as an argument to this method.
     *
     * @param groupName the group to add the member to
     * @param user the (bare) JID of the entity to add
     * @param administrator True if the member is an administrator of the group
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     * @throws GroupNotFoundException if the provided group name does not refer to an existing group.
     */
    void addMember(String groupName, JID user, boolean administrator) throws GroupNotFoundException;

    /**
     * Updates the privileges of an entity in a group.
     *
     * Implementations should use the bare JID representation of the JID passed as an argument to this method.
     *
     * @param groupName the group where the change happened
     * @param user the (bare) JID of the entity with new privileges
     * @param administrator True if the member is an administrator of the group
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     * @throws GroupNotFoundException if the provided group name does not refer to an existing group.
     */
    void updateMember(String groupName, JID user, boolean administrator) throws GroupNotFoundException;

    /**
     * Deletes an entity from a group (optional operation).
     *
     * Implementations should use the bare JID representation of the JID passed as an argument to this method.
     *
     * @param groupName the group name.
     * @param user the (bare) JID of the entity to delete.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     */
    void deleteMember(String groupName, JID user);

    /**
     * Returns true if this GroupProvider is read-only. When read-only,
     * groups can not be created, deleted, or modified.
     *
     * @return true if the user provider is read-only.
     */
    boolean isReadOnly();

    /**
     * Returns the group names that match a search. The search is over group names and
     * implicitly uses wildcard matching (although the exact search semantics are left
     * up to each provider implementation). For example, a search for "HR" should match
     * the groups "HR", "HR Department", and "The HR People".<p>
     *
     * Before searching or showing a search UI, use the {@link #isSearchSupported} method
     * to ensure that searching is supported.
     *
     * @param query the search string for group names.
     * @return all groups that match the search.
     */
    Collection<String> search(String query);

    /**
     * Returns the group names that match a search given a start index and desired number of results.
     * The search is over group names and implicitly uses wildcard matching (although the
     * exact search semantics are left up to each provider implementation). For example, a
     * search for "HR" should match the groups "HR", "HR Department", and "The HR People".<p>
     *
     * Before searching or showing a search UI, use the {@link #isSearchSupported} method
     * to ensure that searching is supported.
     *
     * @param query the search string for group names.
     * @param startIndex start index in results.
     * @param numResults number of results to return.
     * @return all groups that match the search.
     */
    Collection<String> search(String query, int startIndex, int numResults);

    /**
     * Returns the names of groups that have a property matching the given
     * key/value pair. This provides a simple extensible search mechanism
     * for providers with differing property sets and storage models.
     * 
     * The semantics of the key/value matching (wildcard support, scoping, etc.) 
     * are unspecified by the interface and may vary for each implementation.
     * 
     * Before searching or showing a search UI, use the {@link #isSearchSupported} method
     * to ensure that searching is supported.
     *
     * @param key The name of a group property (e.g. "sharedRoster.showInRoster")
     * @param value The value to match for the given property
     * @return unmodifiable Collection of group names that match the
     * 			given key/value pair.
     */
    Collection<String> search(String key, String value);
    
    /**
     * Returns true if group searching is supported by the provider.
     *
     * @return true if searching is supported.
     */
    boolean isSearchSupported();
    
    /**
     * Loads the group properties (if any) from the backend data store. If
     * the properties can be changed, the provider implementation must ensure
     * that updates to the resulting {@link Map} are persisted to the
     * backend data store. Otherwise, if a mutator method is called, the
     * implementation should throw an {@link UnsupportedOperationException}.
     * 
     * If there are no corresponding properties for the given group, or if the
     * provider does not support group properties, this method should return
     * an empty Map rather than null.
     * 
     * @param group The target group
     * @return The properties for the given group
     */
    PersistableMap<String,String> loadProperties(Group group);
}
