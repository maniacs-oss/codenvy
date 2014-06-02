/*
 * CODENVY CONFIDENTIAL
 * __________________
 *
 *  [2012] - [2014] Codenvy, S.A.
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package com.codenvy.workspace.websocket;

import com.codenvy.commons.env.EnvironmentContext;
import com.codenvy.workspace.WsActivitySender;

import org.everrest.websockets.WSConnection;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.servlet.http.HttpSession;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Max Shaposhnik (mshaposhnik@codenvy.com) on 6/27/13.
 * @version $Id: $
 */

@Listeners(value = {MockitoTestNGListener.class})
public class WorkspaceWebsocketConnectioListenerTest {

    @Mock
    private HttpSession httpSession;

    @Mock
    private WSConnection wsConnection;

    @Mock
    private WsActivitySender wsActivitySender;

    @Mock
    private EnvironmentContext environmentContext;

    private String wsId = "wsId";

    @Test
    public void shouldRegisterListener() {

        WorkspaceWebsocketConnectionListener listener = new WorkspaceWebsocketConnectionListener(wsActivitySender);
        when(wsConnection.getHttpSession()).thenReturn(httpSession);
        when(httpSession.getAttribute(anyString())).thenReturn(environmentContext);
        when(environmentContext.getWorkspaceId()).thenReturn(wsId);

        listener.onOpen(wsConnection);
        verify(wsConnection).registerMessageReceiver(any(WorkspaceWebsocketMessageReceiver.class));
    }

    @Test
    public void shouldNotRegisterListenerWhenNoEnvContext() {

        WorkspaceWebsocketConnectionListener listener = new WorkspaceWebsocketConnectionListener(wsActivitySender);
        when(wsConnection.getHttpSession()).thenReturn(httpSession);
        when(httpSession.getAttribute(anyString())).thenReturn(null);

        listener.onOpen(wsConnection);
        verify(wsConnection, never()).registerMessageReceiver(any(WorkspaceWebsocketMessageReceiver.class));
    }
}
