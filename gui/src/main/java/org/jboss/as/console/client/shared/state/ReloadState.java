package org.jboss.as.console.client.shared.state;

import com.google.gwt.core.client.GWT;
import com.google.web.bindery.event.shared.EventBus;
import org.jboss.as.console.client.Console;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Heiko Braun
 * @date 12/14/11
 */
@Singleton
public class ReloadState {

    private Map<String, ServerState> serverStates = new HashMap<String, ServerState>();
    private int lastFiredSize = 0;


    public boolean isStaleModel() {
        return serverStates.size()>0;
    }

    public void reset() {
        serverStates.clear();
        lastFiredSize = 0;
    }

    public void setReloadRequired(String name, boolean willBeRequired) {

        if(willBeRequired)
        {
            ServerState state = new ServerState(name);
            state.setReloadRequired(true);
            serverStates.put(name, state);
        }
    }

    public void setRestartRequired(String name, boolean willBeRequired) {

        if(willBeRequired)
        {
            ServerState state = new ServerState(name);
            state.setRestartRequired(true);
            serverStates.put(name, state);
        }
    }

    public void propagateChanges() {
        if(isStaleModel() && lastFiredSize<serverStates.size())
        {
            lastFiredSize = serverStates.size();

            StringBuffer sb = new StringBuffer();
            sb.append(Console.CONSTANTS.server_instance_servers_needRestart());
            sb.append("<ul>");
            for(ServerState server : serverStates.values())
            {
                sb.append("<li>").append(server.getName());
            }
            sb.append("</ul>");

            // state update, fire notification
            Console.warning(Console.CONSTANTS.server_instance_reloadRequired(), sb.toString(), true);
        }
    }

    public void resetServer(String name) {
        serverStates.remove(name);
        lastFiredSize--;
    }
}
