package org.ovirt.engine.ui.uicommonweb.models.vms;

import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.VMStatus;
import org.ovirt.engine.core.common.queries.HasAdElementReconnectPermissionParameters;
import org.ovirt.engine.core.common.queries.VdcQueryReturnValue;
import org.ovirt.engine.core.common.queries.VdcQueryType;
import org.ovirt.engine.ui.frontend.AsyncQuery;
import org.ovirt.engine.ui.frontend.Frontend;
import org.ovirt.engine.ui.frontend.INewAsyncCallback;
import org.ovirt.engine.ui.frontend.utils.BaseContextPathData;
import org.ovirt.engine.ui.uicommonweb.BaseCommandTarget;
import org.ovirt.engine.ui.uicommonweb.UICommand;
import org.ovirt.engine.ui.uicommonweb.models.ConfirmationModel;
import org.ovirt.engine.ui.uicommonweb.models.EntityModel;
import org.ovirt.engine.ui.uicommonweb.models.Model;
import org.ovirt.engine.ui.uicompat.ConstantsManager;
import org.ovirt.engine.ui.uicompat.Event;
import org.ovirt.engine.ui.uicompat.EventDefinition;
import org.ovirt.engine.ui.uicompat.PropertyChangedEventArgs;

import com.google.gwt.dom.client.FormElement;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteEvent;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteHandler;
import com.google.gwt.user.client.ui.NamedFrame;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextArea;

public abstract class ConsoleModel extends EntityModel {
    public static final String EjectLabel = "[" + ConstantsManager.getInstance().getConstants().eject() + "]"; //$NON-NLS-1$ //$NON-NLS-2$
    public static final String GET_ATTACHMENT_SERVLET_URL = BaseContextPathData.getInstance().getPath()
            + "services/attachment/"; //$NON-NLS-1$

    public static EventDefinition ErrorEventDefinition;
    private Event privateErrorEvent;

    /**
     * The user have selected this model in the edit console dialog
     */
    private boolean userSelected;

    public boolean isUserSelected() {
        return userSelected;
    }

    private ConsoleSelectionContext selectionContext;

    public ConsoleSelectionContext getSelectionContext() {
        return selectionContext;
    }

    public void setSelectionContext(ConsoleSelectionContext selectionContext) {
        this.selectionContext = selectionContext;
    }

    public void setUserSelected(boolean userSelected) {
        this.userSelected = userSelected;
    }

    public Event getErrorEvent()
    {
        return privateErrorEvent;
    }

    private void setErrorEvent(Event value)
    {
        privateErrorEvent = value;
    }

    private UICommand privateConnectCommand;

    public UICommand getConnectCommand()
    {
        return privateConnectCommand;
    }

    private void setConnectCommand(UICommand value)
    {
        privateConnectCommand = value;
    }

    private boolean isConnected;

    public boolean getIsConnected()
    {
        return isConnected;
    }

    public void setIsConnected(boolean value)
    {
        if (isConnected != value)
        {
            isConnected = value;
            onPropertyChanged(new PropertyChangedEventArgs("IsConnected")); //$NON-NLS-1$
        }
    }

    private boolean forceVmStatusUp;

    public boolean getForceVmStatusUp()
    {
        return forceVmStatusUp;
    }

    public void setForceVmStatusUp(boolean value)
    {
        if (forceVmStatusUp != value)
        {
            forceVmStatusUp = value;
            onPropertyChanged(new PropertyChangedEventArgs("ForceVmStatusUp")); //$NON-NLS-1$
        }
    }

    @Override
    public VM getEntity()
    {
        return (VM) super.getEntity();
    }

    public void setEntity(VM value)
    {
        super.setEntity(value);
    }

    /**
     * This attribute is a workaround for displaying popup dialogs
     * in console models.
     */
    protected Model parentModel;

    public void setParentModel(Model parentModel) {
        this.parentModel = parentModel;
    }

    static
    {
        ErrorEventDefinition = new EventDefinition("Error", ConsoleModel.class); //$NON-NLS-1$
    }

    protected ConsoleModel()
    {
        setErrorEvent(new Event(ErrorEventDefinition));

        setConnectCommand(new UICommand("Connect", this)); //$NON-NLS-1$
    }

    protected abstract void connect();

    @Override
    protected void onEntityChanged()
    {
        super.onEntityChanged();

        updateActionAvailability();
    }

    @Override
    protected void entityPropertyChanged(Object sender, PropertyChangedEventArgs e)
    {
        super.entityPropertyChanged(sender, e);

        if (e.PropertyName.equals("status")) //$NON-NLS-1$
        {
            updateActionAvailability();
        }
    }

    protected void updateActionAvailability()
    {
    }

    @Override
    public void executeCommand(UICommand command)
    {
        super.executeCommand(command);

        if (command == getConnectCommand())
        {
            connect();
        }
    }

    public boolean isVmConnectReady()
    {
        if (getForceVmStatusUp())
        {
            return getEntity().getStatus() == VMStatus.Up;
        }

        return isVmUp();
    }

    public boolean isVmUp()
    {
        switch (getEntity().getStatus())
        {
        case PoweringUp:
        case Up:
        case RebootInProgress:
        case PoweringDown:
        case Paused:
            return true;

        default:
            return false;
        }
    }

    /**
     * Executes given command. The confirmation dialog is displayed when it's
     * not safe to take over the console, which is when
     *  - allow console reconnect is disabled AND
     *  - there is an active console user who is different from current portal user and has not reconnect permissions AND
     *  - current portal user has not reconnect permissions (this is to prevent extra information dialog. backend
     *    validation will not allow connecting this user and frontend will display warning message anyway)
     *
     * @param command
     */
    protected void executeCommandWithConsoleSafenessWarning(final UICommand command) {
        VM vm = getEntity();
        if (vm.getAllowConsoleReconnect() || vm.getConsoleCurentUserName() == null ||
            Frontend.getInstance().getLoggedInUser().getId().equals(vm.getConsoleUserId())) {
            command.execute();
            return;
        }

        //now we ask if the currently connected user has permission to reconnect (async)
        final HasAdElementReconnectPermissionParameters consoleUserReconnectPermParams =
                new HasAdElementReconnectPermissionParameters(vm.getConsoleUserId(),
                        vm.getId());

        final HasAdElementReconnectPermissionParameters portalUserReconnectPermParams =
                new HasAdElementReconnectPermissionParameters(Frontend.getInstance().getLoggedInUser().getId(),
                        vm.getId());

        final AsyncQuery portalUserReconnectPermissionQuery = new AsyncQuery();
        portalUserReconnectPermissionQuery.setModel(this);
        portalUserReconnectPermissionQuery.asyncCallback = new INewAsyncCallback() {
            @Override
            public void onSuccess(Object model, Object result)
            {
                boolean returnValue = ((VdcQueryReturnValue)result).getReturnValue();
                if (returnValue) {
                    displayConsoleConnectConfirmPopup(command);
                } else {
                    command.execute(); //user will be stopped by backend validation
                }
            }
        };

        final AsyncQuery consoleUserReconnectPermissionQuery = new AsyncQuery();
        consoleUserReconnectPermissionQuery.setModel(this);
        consoleUserReconnectPermissionQuery.asyncCallback = new INewAsyncCallback() {
            @Override
            public void onSuccess(Object model, Object result)
            {
                boolean returnValue = ((VdcQueryReturnValue)result).getReturnValue();
                if (returnValue) {
                    command.execute();
                } else {
                    Frontend.RunQuery(VdcQueryType.HasAdElementReconnectPermission, portalUserReconnectPermParams, portalUserReconnectPermissionQuery);
                }
            }
        };

        Frontend.RunQuery(VdcQueryType.HasAdElementReconnectPermission, consoleUserReconnectPermParams, consoleUserReconnectPermissionQuery);
    }

    private void displayConsoleConnectConfirmPopup(final UICommand onConfirmCommand) {
        ConfirmationModel model = new ConfirmationModel();
        parentModel.setWindow(model);
        model.setTitle(ConstantsManager.getInstance().getConstants().confirmConsoleConnect());
        model.setHashName("confirm_console_connect"); //$NON-NLS-1$
        model.setMessage(ConstantsManager.getInstance().getConstants().confirmConsoleConnectMessage());

        UICommand confirmAndCloseCommand = new UICommand("Confirm", new BaseCommandTarget() { //$NON-NLS-1$
            @Override
            public void executeCommand(UICommand uiCommand) {
                onConfirmCommand.execute();
                parentModel.setWindow(null);
            }
        });
        confirmAndCloseCommand.setTitle(ConstantsManager.getInstance().getConstants().ok());
        confirmAndCloseCommand.setIsDefault(true);
        model.getCommands().add(confirmAndCloseCommand);

        UICommand cancelCommand = new UICommand("Cancel", new BaseCommandTarget() { //$NON-NLS-1$
            @Override
            public void executeCommand(UICommand uiCommand) {
                parentModel.setWindow(null);
            }
        });
        cancelCommand.setTitle(ConstantsManager.getInstance().getConstants().cancel());
        cancelCommand.setIsCancel(true);
        model.getCommands().add(cancelCommand);
    }

    public static void makeConsoleConfigRequest(String fileName, String contentType, String configFileContent) {
        // open form always in a new window
        final FormPanel formPanel = new FormPanel(new NamedFrame("_blank")); //$NON-NLS-1$
        formPanel.setMethod(FormPanel.METHOD_POST);

        final FlowPanel innerPanel = new FlowPanel();
        innerPanel.add(buildTextArea("contenttype", contentType));//$NON-NLS-1$
        innerPanel.add(buildTextArea("content", configFileContent));//$NON-NLS-1$
        innerPanel.add(buildTextArea("encodingtype", "plain"));//$NON-NLS-1$ $NON-NLS-2$

        formPanel.setWidget(innerPanel);
        formPanel.setAction(GET_ATTACHMENT_SERVLET_URL + fileName);
        formPanel.setVisible(false);

        final FormElement form = FormElement.as(formPanel.getElement());
        formPanel.setEncoding(FormPanel.ENCODING_URLENCODED);
        RootPanel.getBodyElement().appendChild(form);

        // clean-up after form submit
        formPanel.addSubmitCompleteHandler(new SubmitCompleteHandler() {
            @Override
            public void onSubmitComplete(SubmitCompleteEvent event) {
                RootPanel.getBodyElement().removeChild(form);
            }
        });

        form.submit();
    }

    private static TextArea buildTextArea(String name, String value) {
        TextArea textArea = new TextArea();
        textArea.setName(name);
        textArea.setValue(value);

        return textArea;
    }

}
