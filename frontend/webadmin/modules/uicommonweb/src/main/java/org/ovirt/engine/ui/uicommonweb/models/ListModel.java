package org.ovirt.engine.ui.uicommonweb.models;

import java.util.List;
import org.ovirt.engine.ui.uicommonweb.validation.IValidation;
import org.ovirt.engine.ui.uicommonweb.validation.ValidationResult;
import org.ovirt.engine.ui.uicompat.Event;
import org.ovirt.engine.ui.uicompat.EventArgs;
import org.ovirt.engine.ui.uicompat.EventDefinition;
import org.ovirt.engine.ui.uicompat.IProvideCollectionChangedEvent;
import org.ovirt.engine.ui.uicompat.IProvidePropertyChangedEvent;
import org.ovirt.engine.ui.uicompat.NotifyCollectionChangedEventArgs;
import org.ovirt.engine.ui.uicompat.PropertyChangedEventArgs;
import org.ovirt.engine.ui.uicompat.ProvideCollectionChangedEvent;
import org.ovirt.engine.ui.uicompat.ProvidePropertyChangedEvent;

public class ListModel<T> extends EntityModel<T>
{

    public static EventDefinition selectedItemChangedEventDefinition;
    private Event privateSelectedItemChangedEvent;

    public Event getSelectedItemChangedEvent()
    {
        return privateSelectedItemChangedEvent;
    }

    private void setSelectedItemChangedEvent(Event value)
    {
        privateSelectedItemChangedEvent = value;
    }

    public static EventDefinition SelectedItemsChangedEventDefinition;
    private Event privateSelectedItemsChangedEvent;

    public Event getSelectedItemsChangedEvent()
    {
        return privateSelectedItemsChangedEvent;
    }

    private void setSelectedItemsChangedEvent(Event value)
    {
        privateSelectedItemsChangedEvent = value;
    }

    public static EventDefinition ItemsChangedEventDefinition;
    private Event privateItemsChangedEvent;

    public Event getItemsChangedEvent()
    {
        return privateItemsChangedEvent;
    }

    private void setItemsChangedEvent(Event value)
    {
        privateItemsChangedEvent = value;
    }

    protected List<T> selectedItems;

    public List<T> getSelectedItems()
    {
        return selectedItems;
    }

    public void setSelectedItems(List<T> value)
    {
        if (selectedItems != value)
        {
            selectedItemsChanging(value, selectedItems);
            selectedItems = value;
            selectedItemsChanged();
            getSelectedItemsChangedEvent().raise(this, EventArgs.Empty);
            onPropertyChanged(new PropertyChangedEventArgs("SelectedItems")); //$NON-NLS-1$
        }
    }

    protected T selectedItem;

    public T getSelectedItem()
    {
        return selectedItem;
    }

    public void setSelectedItem(T value)
    {
        if (selectedItem != value)
        {
            onSelectedItemChanging(value, selectedItem);
            selectedItem = value;
            onSelectedItemChanged();
            getSelectedItemChangedEvent().raise(this, EventArgs.Empty);
            onPropertyChanged(new PropertyChangedEventArgs("SelectedItem")); //$NON-NLS-1$
        }
    }

    protected Iterable<T> items;

    public Iterable<T> getItems()
    {
        return items;
    }

    public void setItems(Iterable<T> value)
    {
        if (items != value)
        {
            itemsChanging(value, items);
            items = value;
            itemsChanged();
            getItemsChangedEvent().raise(this, EventArgs.Empty);
            onPropertyChanged(new PropertyChangedEventArgs("Items")); //$NON-NLS-1$
        }
    }

    private boolean isEmpty;

    /**
     * Gets or sets the value indicating whether this model is empty. Notice, that this value is not updated
     * automatically.
     */
    public boolean getIsEmpty()
    {
        return isEmpty;
    }

    public void setIsEmpty(boolean value)
    {
        if (isEmpty != value)
        {
            isEmpty = value;
            onPropertyChanged(new PropertyChangedEventArgs("IsEmpty")); //$NON-NLS-1$
        }
    }

    /**
     * Override this property and return true in order to receive property change notifications for any item but not
     * only for selected ones. Pay attention, when property change occurs either SelectedItemPropertyChanged or
     * ItemPropertyChanged will be called but not both of them.
     */
    protected boolean getNotifyPropertyChangeForAnyItem()
    {
        return false;
    }

    static
    {
        selectedItemChangedEventDefinition = new EventDefinition("SelectedItemChanged", ListModel.class); //$NON-NLS-1$
        SelectedItemsChangedEventDefinition = new EventDefinition("SelectedItemsChanged", ListModel.class); //$NON-NLS-1$
        ItemsChangedEventDefinition = new EventDefinition("ItemsChanged", ListModel.class); //$NON-NLS-1$
    }

    public ListModel()
    {
        setSelectedItemChangedEvent(new Event(selectedItemChangedEventDefinition));
        setSelectedItemsChangedEvent(new Event(SelectedItemsChangedEventDefinition));
        setItemsChangedEvent(new Event(ItemsChangedEventDefinition));
    }

    protected void onSelectedItemChanging(T newValue, T oldValue)
    {
    }

    protected void onSelectedItemChanged()
    {
    }

    protected void selectedItemsChanged()
    {
    }

    protected void selectedItemsChanging(List<T> newValue, List<T> oldValue)
    {
        // Skip this method when notifying on property change for any
        // item but not only for selected ones is requested.
        // Subscribtion to the event will be done in ItemsCollectionChanged method.
        if (getNotifyPropertyChangeForAnyItem())
        {
            return;
        }

        unsubscribeList(oldValue);
        subscribeList(newValue);
    }

    @Override
    public void eventRaised(Event ev, Object sender, EventArgs args)
    {
        super.eventRaised(ev, sender, args);

        if (ev.matchesDefinition(ProvidePropertyChangedEvent.Definition))
        {
            if (getNotifyPropertyChangeForAnyItem())
            {
                // If notification on property change for any item was requested,
                // check whether the event was sent by a selected item or not.
                boolean anyOfSelectedItem = false;
                if (getSelectedItems() != null)
                {
                    for (T item : getSelectedItems())
                    {
                        if (item == sender)
                        {
                            anyOfSelectedItem = true;
                            break;
                        }
                    }
                }

                if (anyOfSelectedItem)
                {
                    selectedItemPropertyChanged(sender, (PropertyChangedEventArgs) args);
                }
                else
                {
                    itemPropertyChanged(sender, (PropertyChangedEventArgs) args);
                }
            }
            else
            {
                // In this case a sender always will be a one of selected item.
                selectedItemPropertyChanged(sender, (PropertyChangedEventArgs) args);
            }
        }
        else if (ev.matchesDefinition(ProvideCollectionChangedEvent.Definition))
        {
            itemsCollectionChanged(sender, (NotifyCollectionChangedEventArgs<T>) args);
        }
    }

    /**
     * Invoked whenever some property of any selected item was changed.
     */
    protected void selectedItemPropertyChanged(Object sender, PropertyChangedEventArgs e)
    {
    }

    /**
     * Invoked whenever some property of any item was changed. For performance considerations, in order to get this
     * method called, override NotifyPropertyChangeForAnyItem property and return true.
     */
    protected void itemPropertyChanged(Object sender, PropertyChangedEventArgs e)
    {
    }

    protected void itemsChanged()
    {
        // if Items are updated, SelectedItem and SelectedItems become irrelevant:
        setSelectedItem(null);
        setSelectedItems(null);
    }

    protected void itemsChanging(Iterable<T> newValue, Iterable<T> oldValue)
    {
        IProvideCollectionChangedEvent notifier =
                (IProvideCollectionChangedEvent) ((oldValue instanceof IProvideCollectionChangedEvent) ? oldValue
                        : null);
        if (notifier != null)
        {
            notifier.getCollectionChangedEvent().removeListener(this);
        }

        notifier =
                (IProvideCollectionChangedEvent) ((newValue instanceof IProvideCollectionChangedEvent) ? newValue
                        : null);
        if (notifier != null)
        {
            notifier.getCollectionChangedEvent().addListener(this);
        }

        // Unsure subscribing to the property change notification for all items.
        unsubscribeList(oldValue);
        subscribeList(newValue);
    }

    /**
     * Invoked whenever items collection was changed, i.e. some items was added or removed.
     */
    protected void itemsCollectionChanged(Object sender, NotifyCollectionChangedEventArgs<T> e)
    {
        if (!getNotifyPropertyChangeForAnyItem())
        {
            return;
        }

        // Track property change on all items as necessary.
        unsubscribeList(e.OldItems);
        subscribeList(e.NewItems);
    }

    public void validateSelectedItem(IValidation[] validations)
    {
        setIsValid(true);

        if (!getIsAvailable() || !getIsChangable())
        {
            return;
        }

        for (IValidation validation : validations)
        {
            ValidationResult result = validation.validate(getSelectedItem());
            if (!result.getSuccess())
            {
                for (String reason : result.getReasons())
                {
                    getInvalidityReasons().add(reason);
                }
                setIsValid(false);

                break;
            }
        }
    }

    private void subscribeList(Iterable<T> list)
    {
        if (list == null)
        {
            return;
        }

        for (T a : list)
        {
            IProvidePropertyChangedEvent notifier =
                    (IProvidePropertyChangedEvent) ((a instanceof IProvidePropertyChangedEvent) ? a : null);
            if (notifier != null)
            {
                notifier.getPropertyChangedEvent().addListener(this);
            }
        }
    }

    private void unsubscribeList(Iterable<T> list)
    {
        if (list == null)
        {
            return;
        }

        for (T a : list)
        {
            IProvidePropertyChangedEvent notifier =
                    (IProvidePropertyChangedEvent) ((a instanceof IProvidePropertyChangedEvent) ? a : null);
            if (notifier != null)
            {
                notifier.getPropertyChangedEvent().removeListener(this);
            }
        }
    }

}
