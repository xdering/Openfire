/*
 * Copyright (C) 2017-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.spi;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.keystore.*;

import java.net.InetAddress;
import java.security.Security;
import java.util.*;

/**
 * Configuration for a socket connection.
 *
 * Instances of this class are thread-safe, with the exception of the internal state of the #bindAddress property.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ConnectionConfiguration
{
    private final boolean enabled;
    private final ConnectionType type;
    private final int maxThreadPoolSize;
    private final int maxBufferSize;
    private final Connection.ClientAuth clientAuth;
    private final InetAddress bindAddress;
    private final int port;
    private final Connection.TLSPolicy tlsPolicy;
    private final CertificateStoreConfiguration identityStoreConfiguration;
    private final CertificateStoreConfiguration trustStoreConfiguration;
    private final boolean acceptSelfSignedCertificates;
    private final boolean verifyCertificateValidity;
    private final boolean verifyCertificateRevocation;
    private final boolean strictCertificateValidation;
    private final Set<String> encryptionProtocols;
    private final Set<String> encryptionCipherSuites;
    private final Connection.CompressionPolicy compressionPolicy;

    // derived
    private final boolean isOcspEnabled;
    private final IdentityStore identityStore;
    private final TrustStore trustStore;

    /**
     * @param type the connection type
     * @param enabled is the connection enabled or disabled
     * @param maxThreadPoolSize The maximum number of threads that are to be used to processing network activity. Must be equal to or larger than one.
     * @param maxBufferSize The maximum amount of bytes of the read buffer that I/O processor allocates per each read, or a non-positive value to configure no maximum.
     * @param clientAuth specification if peers should be authenticated ('mutual authentication') (cannot be null).
     * @param bindAddress The network address on which connections are accepted, or null when any local address can be used.
     * @param port The TCP port number on which connections are accepted (must be a valid TCP port number).
     * @param tlsPolicy The TLS policy that is applied to connections (cannot be null).
     * @param identityStoreConfiguration the certificates the server identify as
     * @param trustStoreConfiguration the certificates the server trusts
     * @param acceptSelfSignedCertificates {@code true} to accept self-signed certificates, otherwise {@code false}
     * @param verifyCertificateValidity {@code true} to verify validity of certificates (based on their 'notBefore' and 'notAfter' property values), otherwise {@code false}
     * @param verifyCertificateRevocation {@code true} to check certificate revocation status, otherwise {@code false}
     * @param encryptionProtocols the set of protocols supported
     * @param encryptionCipherSuites the set of ciphers supported
     * @param compressionPolicy the compression policy
     * @param strictCertificateValidation {@code true} to abort connections if certificate validation fails, otherwise {@code false}
     */
    // TODO input validation
    public ConnectionConfiguration( ConnectionType type, boolean enabled, int maxThreadPoolSize, int maxBufferSize, Connection.ClientAuth clientAuth, InetAddress bindAddress, int port, Connection.TLSPolicy tlsPolicy, CertificateStoreConfiguration identityStoreConfiguration, CertificateStoreConfiguration trustStoreConfiguration, boolean acceptSelfSignedCertificates, boolean verifyCertificateValidity,  boolean verifyCertificateRevocation, Set<String> encryptionProtocols, Set<String> encryptionCipherSuites, Connection.CompressionPolicy compressionPolicy, boolean strictCertificateValidation )
    {
        if ( maxThreadPoolSize <= 0 ) {
            throw new IllegalArgumentException( "Argument 'maxThreadPoolSize' must be equal to or greater than one." );
        }
        if ( clientAuth == null ) {
            throw new IllegalArgumentException( "Argument 'clientAuth' cannot be null." );
        }

        this.enabled = enabled;
        this.tlsPolicy = tlsPolicy;
        this.type = type;
        this.maxThreadPoolSize = maxThreadPoolSize;
        this.maxBufferSize = maxBufferSize;
        this.clientAuth = clientAuth;
        this.bindAddress = bindAddress;
        this.port = port;
        this.identityStoreConfiguration = identityStoreConfiguration;
        this.trustStoreConfiguration = trustStoreConfiguration;
        this.acceptSelfSignedCertificates = acceptSelfSignedCertificates;
        this.verifyCertificateValidity = verifyCertificateValidity;
        this.verifyCertificateRevocation = verifyCertificateRevocation;
        this.encryptionProtocols = Collections.unmodifiableSet( encryptionProtocols );
        this.encryptionCipherSuites = Collections.unmodifiableSet( encryptionCipherSuites );
        this.compressionPolicy = compressionPolicy;
        this.strictCertificateValidation = strictCertificateValidation;

        this.isOcspEnabled = Boolean.parseBoolean(Security.getProperty("ocsp.enable"));
        final CertificateStoreManager certificateStoreManager = XMPPServer.getInstance().getCertificateStoreManager();
        this.identityStore = certificateStoreManager.getIdentityStore( type );
        this.trustStore = certificateStoreManager.getTrustStore( type );
    }

    public Connection.TLSPolicy getTlsPolicy()
    {
        return tlsPolicy;
    }

    public Connection.CompressionPolicy getCompressionPolicy()
    {
        return compressionPolicy;
    }

    public ConnectionType getType()
    {
        return type;
    }

    public int getMaxThreadPoolSize()
    {
        return maxThreadPoolSize;
    }

    public int getMaxBufferSize()
    {
        return maxBufferSize;
    }

    public Connection.ClientAuth getClientAuth()
    {
        return clientAuth;
    }

    public InetAddress getBindAddress()
    {
        return bindAddress;
    }

    public int getPort()
    {
        return port;
    }

    public CertificateStoreConfiguration getIdentityStoreConfiguration()
    {
        return identityStoreConfiguration;
    }

    public CertificateStoreConfiguration getTrustStoreConfiguration()
    {
        return trustStoreConfiguration;
    }

    /**
     * A boolean that indicates if self-signed peer certificates can be used to establish an encrypted connection.
     *
     * @return true when self-signed certificates are accepted, otherwise false.
     */
    public boolean isAcceptSelfSignedCertificates()
    {
        return acceptSelfSignedCertificates;
    }

    /**
     * A boolean that indicates if the current validity of certificates (based on their 'notBefore' and 'notAfter'
     * property values) is used when they are used to establish an encrypted connection.
     *
     * @return true when certificates are required to be valid to establish an encrypted connection, otherwise false.
     */
    public boolean isVerifyCertificateValidity()
    {
        return verifyCertificateValidity;
    }

    /**
     * A boolean that indicates if the revocation status of certificates is checked when they are used to establish an
     * encrypted connection.
     *
     * @return true when the revocation status of certificates is checked, otherwise false.
     */
    public boolean isVerifyCertificateRevocation()
    {
        return verifyCertificateRevocation;
    }

    /**
     * A collection of protocol names that can be used for encryption of connections.
     *
     * When non-empty, the list is intended to specify those protocols (from a larger collection of implementation-
     * supported protocols) that can be used to establish encryption.
     *
     * The order over which values are iterated in the result is equal to the order of values in the comma-separated
     * configuration string. This can, but is not guaranteed to, indicate preference.
     *
     * @return An (ordered) set of protocols, never null but possibly empty.
     */
    public Set<String> getEncryptionProtocols()
    {
        return encryptionProtocols;
    }

    /**
     * A collection of cipher suite names that can be used for encryption of connections.
     *
     * When non-empty, the list is intended to specify those cipher suites (from a larger collection of implementation-
     * supported cipher suites) that can be used to establish encryption.
     *
     * The order over which values are iterated in the result is equal to the order of values in the comma-separated
     * configuration string. This can, but is not guaranteed to, indicate preference.
     *
     * @return An (ordered) set of cipher suites, never null but possibly empty.
     */
    public Set<String> getEncryptionCipherSuites()
    {
        return encryptionCipherSuites;
    }

    public IdentityStore getIdentityStore()
    {
        return identityStore;
    }

    public TrustStore getTrustStore()
    {
        return trustStore;
    }

    /**
     * Indicates if client-driven Online Certificate Status Protocol (OCSP) is enabled.
     *
     * This is a prerequisite to enable client-driven OCSP, it has no effect unless revocation
     * checking is also enabled.
     *
     * @return true if client-driven OCSP is enabled, otherwise false.
     */
    public boolean isOcspEnabled()
    {
        return isOcspEnabled;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * A boolean that indicates if the connection should be aborted if certificate validation fails.
     * When true Openfire strictly follows RFC 6120, section 13.7.2
     *
     * @return true when connections are aborted if certificate validation fails, otherwise false.
     */
    public boolean isStrictCertificateValidation() {
        return strictCertificateValidation;
    }
}
