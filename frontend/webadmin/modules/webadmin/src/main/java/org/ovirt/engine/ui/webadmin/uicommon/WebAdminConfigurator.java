package org.ovirt.engine.ui.webadmin.uicommon;

import java.util.ArrayList;

import org.ovirt.engine.core.compat.Version;
import org.ovirt.engine.ui.common.uicommon.ClientAgentType;
import org.ovirt.engine.ui.common.uicommon.DocumentationPathTranslator;
import org.ovirt.engine.ui.common.uicommon.model.UiCommonInitEvent;
import org.ovirt.engine.ui.common.uicommon.model.UiCommonInitEvent.UiCommonInitHandler;
import org.ovirt.engine.ui.uicommonweb.Configurator;
import org.ovirt.engine.ui.uicommonweb.models.vms.ISpice;
import org.ovirt.engine.ui.uicommonweb.models.vms.WANDisableEffects;
import org.ovirt.engine.ui.uicompat.Event;
import org.ovirt.engine.ui.uicompat.EventArgs;
import org.ovirt.engine.ui.uicompat.EventDefinition;
import org.ovirt.engine.ui.uicompat.IEventListener;

import com.google.gwt.event.shared.EventBus;
import com.google.inject.Inject;

public class WebAdminConfigurator extends Configurator implements IEventListener, UiCommonInitHandler {

    public static final String DOCUMENTATION_GUIDE_PATH = "Administration_Guide/index.html"; //$NON-NLS-1$

    public EventDefinition spiceVersionFileFetchedEvent_Definition =
            new EventDefinition("spiceVersionFileFetched", WebAdminConfigurator.class); //$NON-NLS-1$
    public Event spiceVersionFileFetchedEvent = new Event(spiceVersionFileFetchedEvent_Definition);

    public EventDefinition documentationFileFetchedEvent_Definition =
        new EventDefinition("documentationFileFetched", WebAdminConfigurator.class); //$NON-NLS-1$
    public Event documentationFileFetchedEvent = new Event(documentationFileFetchedEvent_Definition);

    private static final ClientAgentType clientAgentType = new ClientAgentType();

    @Inject
    public WebAdminConfigurator(EventBus eventBus) {
        super();
        eventBus.addHandler(UiCommonInitEvent.getType(), this);

        // This means that this is WebAdmin application.
        setIsAdmin(true);
        setSpiceAdminConsole(true);

        // Add event listeners
        spiceVersionFileFetchedEvent.addListener(this);
        documentationFileFetchedEvent.addListener(this);

        // Update Spice version if needed
        updateSpiceVersion();
    }

    @Override
    public void eventRaised(Event ev, Object sender, EventArgs args) {
        if (ev.matchesDefinition(spiceVersionFileFetchedEvent_Definition)) {
            Version spiceVersion = parseVersion(((FileFetchEventArgs) args).getFileContent());
            setSpiceVersion(spiceVersion);
        } else if (ev.matchesDefinition(documentationFileFetchedEvent_Definition)) {
            String documentationPathFileContent = ((FileFetchEventArgs) args).getFileContent();
            DocumentationPathTranslator.init(documentationPathFileContent);
        }
    }

    @Override
    public void configure(ISpice spice) {
        super.configure(spice);
        spice.setWANDisableEffects(new ArrayList<WANDisableEffects>());
        spice.setWanOptionsEnabled(false);

        spice.setSpiceProxyEnabled(isSpiceProxyDefined());
    }

    @Override
    protected Event getSpiceVersionFileFetchedEvent() {
        return spiceVersionFileFetchedEvent;
    }

    @Override
    public void onUiCommonInit(UiCommonInitEvent event) {
        fetchDocumentationFile();
    }

    @Override
    protected String clientBrowserType() {
        return clientAgentType.browser;
    }

    @Override
    protected String clientOsType() {
        return clientAgentType.os;
    }

    @Override
    protected String clientPlatformType() {
        return clientAgentType.getPlatform();
    }

    @Override
    public Float clientBrowserVersion() {
        return clientAgentType.version;
    }

    protected void fetchDocumentationFile() {
        fetchFile(getDocumentationBaseURL() + "DocumentationPath.csv", documentationFileFetchedEvent); //$NON-NLS-1$
    }

}
