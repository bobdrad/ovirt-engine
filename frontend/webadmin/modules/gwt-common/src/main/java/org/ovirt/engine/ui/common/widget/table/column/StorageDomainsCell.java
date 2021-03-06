package org.ovirt.engine.ui.common.widget.table.column;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;

public class StorageDomainsCell extends TextCellWithTooltip {

    private String title = ""; //$NON-NLS-1$

    public StorageDomainsCell() {
        super(TextCellWithTooltip.UNLIMITED_LENGTH);
    }

    @Override
    public void onBrowserEvent(Cell.Context context, Element parent,
            String value, NativeEvent event, ValueUpdater<String> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);

        // Ignore events other than 'mouseover'
        if (!"mouseover".equals(event.getType())) { //$NON-NLS-1$
            return;
        }

        parent.setTitle(title);
    }

    public void setTitle(String title) {
        this.title = title != null ? title : ""; //$NON-NLS-1$
    }
}
