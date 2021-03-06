package org.ovirt.engine.ui.uicommonweb.models.storage;

import org.ovirt.engine.ui.uicommonweb.models.EntityModel;
import org.ovirt.engine.ui.uicommonweb.models.ListModel;
import org.ovirt.engine.ui.uicommonweb.models.Model;
import org.ovirt.engine.ui.uicompat.Event;
import org.ovirt.engine.ui.uicompat.EventArgs;

@SuppressWarnings("unused")
public class RemoveStorageModel extends Model
{

    private ListModel privateHostList;

    public ListModel getHostList()
    {
        return privateHostList;
    }

    private void setHostList(ListModel value)
    {
        privateHostList = value;
    }

    private EntityModel privateFormat;

    public EntityModel getFormat()
    {
        return privateFormat;
    }

    private void setFormat(EntityModel value)
    {
        privateFormat = value;
    }

    public RemoveStorageModel()
    {
        setHostList(new ListModel());

        setFormat(new EntityModel());
        getFormat().getEntityChangedEvent().addListener(this);
        getFormat().getPropertyChangedEvent().addListener(this);
        getFormat().setEntity(false);
    }

    @Override
    public void eventRaised(Event ev, Object sender, EventArgs args)
    {
        super.eventRaised(ev, sender, args);

        if (sender == getFormat()) {
            format_Changed(sender, args);
        }
    }

    private void format_Changed(Object sender, EventArgs args) {
        getHostList().setIsChangable(!getFormat().getIsAvailable() || Boolean.TRUE.equals(getFormat().getEntity()));
    }

    public boolean validate()
    {
        getHostList().setIsValid(getHostList().getSelectedItem() != null);

        return getHostList().getIsValid();
    }
}
