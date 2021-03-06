package org.ovirt.engine.core.bll.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.naming.AuthenticationException;

import org.apache.commons.lang.StringUtils;
import org.ovirt.engine.core.bll.Backend;
import org.ovirt.engine.core.bll.interfaces.BackendInternal;
import org.ovirt.engine.core.common.config.Config;
import org.ovirt.engine.core.common.config.ConfigValues;
import org.ovirt.engine.core.common.queries.ServerParameters;
import org.ovirt.engine.core.common.queries.VdcQueryReturnValue;
import org.ovirt.engine.core.common.queries.VdcQueryType;
import org.ovirt.engine.core.utils.XmlUtils;
import org.ovirt.engine.core.utils.log.Log;
import org.ovirt.engine.core.utils.log.LogFactory;
import org.ovirt.engine.core.utils.ssh.ConstraintByteArrayOutputStream;
import org.ovirt.engine.core.utils.ssh.SSHClient;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class GlusterUtil {
    private static GlusterUtil instance = new GlusterUtil();
    private Log log = LogFactory.getLog(getClass());
    private static final int SSH_PORT = 22;
    private static final String PEER = "peer";
    private static final String HOST_NAME = "hostname";
    private static final String STATE = "state";
    private static final int PEER_IN_CLUSTER = 3;

    private GlusterUtil() {

    }

    public static GlusterUtil getInstance() {
        return instance;
    }

    /**
     * Fetches gluster peers of the given server
     *
     * @param server
     *            Server whose peers are to be fetched
     * @param password
     *            Root password of the server
     * @return Set of peers of the server
     * @throws AuthenticationException
     *             If SSH authentication with given root password fails
     */
    public Set<String> getPeers(String server, String username, String password) throws AuthenticationException, IOException {

        try (final SSHClient client = getSSHClient()) {
            connect(client, server);
            authenticate(client, username, password);
            String serversXml = executePeerStatusCommand(client);
            return extractServers(serversXml);
        }
    }

    /**
     * Given an SSHClient (already connected and authenticated), execute the "gluster peer status" command, and return
     * the set of the peers returned by the command. Note that this method does <b>not</b> close the connection, and it
     * is the responsibility of the calling code to do the same.
     *
     * @param client
     *            The already connected and authenticated SSHClient object
     * @return Set of peers of the server
     */
    public Set<String> getPeers(SSHClient client) {
        String serversXml = executePeerStatusCommand(client);
        return extractServers(serversXml);
    }

    /**
     * Fetches gluster peers of the given server
     *
     * @param server
     *            Server whose peers are to be fetched
     * @param username
     *            Privilege username to authenticate with server
     * @param password
     *            password of the server
     * @param fingerprint
     *            pre-approved fingerprint of the server. This is validated against the server before attempting
     *            authentication using the root password.
     * @return Map of peers of the server with key = peer name and value = SSH fingerprint of the peer
     * @throws AuthenticationException
     *             If SSH authentication with given root password fails
     */
    public Map<String, String> getPeers(String server, String username, String password, String fingerprint)
            throws AuthenticationException, IOException {
        try (final SSHClient client = getSSHClient()) {
            connect(client, server);
            authenticate(client, username, password);
            String serversXml = executePeerStatusCommand(client);
            return getFingerprints(extractServers(serversXml));
        }
    }

    protected void connect(SSHClient client, String serverName) {
        Integer timeout = Config.<Integer> GetValue(ConfigValues.ConnectToServerTimeoutInSeconds) * 1000;
        client.setHardTimeout(timeout);
        client.setSoftTimeout(timeout);
        client.setHost(serverName, SSH_PORT);
        try {
            client.connect();
        } catch (Exception e) {
            log.debug(String.format("Could not connect to server %1$s: %2$s", serverName, e.getMessage()));
            throw new RuntimeException(e);
        }
    }

    protected void authenticate(SSHClient client, String userId, String password) throws AuthenticationException {
        client.setUser(userId);
        client.setPassword(password);
        try {
            client.authenticate();
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.errorFormat("Exception during authentication!", e);
            throw new RuntimeException(e);
        }
    }

    protected String executePeerStatusCommand(SSHClient client) {
        ByteArrayOutputStream out = new ConstraintByteArrayOutputStream(500);
        String command = Config.<String> GetValue(ConfigValues.GlusterPeerStatusCommand);
        try {
            client.executeCommand(command, null, out, null);
            return new String(out.toByteArray(), "UTF-8");
        } catch (Exception e) {
            log.errorFormat("Error while executing command {0} on server {1}!", command, client.getHost(), e);
            throw new RuntimeException(e);
        }
    }

    private Set<String> getServers(NodeList listOfPeers) {
        Set<String> servers = new HashSet<String>();
        for (int i = 0; i < listOfPeers.getLength(); i++) {
            Node firstPeer = listOfPeers.item(i);
            if (firstPeer.getNodeType() == Node.ELEMENT_NODE) {
                Element firstHostElement = (Element) firstPeer;
                int state = XmlUtils.getIntValue(firstHostElement, STATE);
                // Add the server only if the state is 3
                if (state == PEER_IN_CLUSTER) {
                    servers.add(XmlUtils.getTextValue(firstHostElement, HOST_NAME));
                }
            }
        }

        return servers;
    }

    protected Map<String, String> getFingerprints(Set<String> servers) {
        VdcQueryReturnValue returnValue;
        Map<String, String> fingerprints = new HashMap<String, String>();
        for (String server : servers) {
            returnValue = getBackendInstance().
                    runInternalQuery(VdcQueryType.GetServerSSHKeyFingerprint,
                                     new ServerParameters(server));
            if (returnValue != null && returnValue.getSucceeded() && returnValue.getReturnValue() != null) {
                fingerprints.put(server, returnValue.getReturnValue().toString());
            } else {
                fingerprints.put(server, null);
            }
        }
        return fingerprints;
    }

    protected Set<String> extractServers(String serversXml) {
        if (StringUtils.isEmpty(serversXml)) {
            throw new RuntimeException("Could not get the peer list!");
        }

        try {
            return getServers(XmlUtils.loadXmlDoc(serversXml).getElementsByTagName(PEER));
        } catch (Exception e) {
            log.errorFormat("Error while parsing peer list xml [{0}]!", serversXml, e);
            throw new RuntimeException(e);
        }
    }

    public BackendInternal getBackendInstance() {
        return Backend.getInstance();
    }

    protected SSHClient getSSHClient() {
        return new EngineSSHClient();
    }
}
