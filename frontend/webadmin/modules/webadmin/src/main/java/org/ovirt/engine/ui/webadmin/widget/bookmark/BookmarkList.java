package org.ovirt.engine.ui.webadmin.widget.bookmark;

import org.ovirt.engine.core.common.businessentities.bookmarks;
import org.ovirt.engine.ui.uicommonweb.UICommand;
import org.ovirt.engine.ui.webadmin.ApplicationTemplates;
import org.ovirt.engine.ui.webadmin.gin.ClientGinjectorProvider;
import org.ovirt.engine.ui.webadmin.uicommon.model.BookmarkModelProvider;
import org.ovirt.engine.ui.webadmin.widget.action.AbstractActionStackPanelItem;
import org.ovirt.engine.ui.webadmin.widget.action.SimpleActionPanel;
import org.ovirt.engine.ui.webadmin.widget.action.UiCommandButtonDefinition;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.google.gwt.event.dom.client.ScrollHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.CellList;
import com.google.gwt.user.cellview.client.HasKeyboardPagingPolicy.KeyboardPagingPolicy;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.Range;

public class BookmarkList extends AbstractActionStackPanelItem<bookmarks, CellList<bookmarks>> {

    interface WidgetUiBinder extends UiBinder<Widget, BookmarkList> {
        WidgetUiBinder uiBinder = GWT.create(WidgetUiBinder.class);
    }

    @UiField
    ScrollPanel scrollPanel;

    public BookmarkList(BookmarkModelProvider modelProvider) {
        super(getBookmarkDisplayWidget(modelProvider), getActionPanel(modelProvider));
        initWidget(WidgetUiBinder.uiBinder.createAndBindUi(this));
        addScrollEventHandler(scrollPanel);
    }

    static CellList<bookmarks> getBookmarkDisplayWidget(BookmarkModelProvider modelProvider) {
        ApplicationTemplates templates = ClientGinjectorProvider.instance().getApplicationTemplates();

        CellList<bookmarks> display = new CellList<bookmarks>(new BookmarkListItemCell(templates));
        display.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.BOUND_TO_SELECTION);
        display.setKeyboardPagingPolicy(KeyboardPagingPolicy.INCREASE_RANGE);

        modelProvider.addDataDisplay(display);

        return display;
    }

    static SimpleActionPanel<bookmarks> getActionPanel(final BookmarkModelProvider modelProvider) {
        SimpleActionPanel<bookmarks> actionPanel = new SimpleActionPanel<bookmarks>(
                modelProvider, modelProvider.getSelectionModel());

        actionPanel.addActionButton(new UiCommandButtonDefinition<bookmarks>("New") {
            @Override
            protected UICommand resolveCommand() {
                return modelProvider.getModel().getNewCommand();
            }
        });

        actionPanel.addActionButton(new UiCommandButtonDefinition<bookmarks>("Edit") {
            @Override
            protected UICommand resolveCommand() {
                return modelProvider.getModel().getEditCommand();
            }
        });

        actionPanel.addActionButton(new UiCommandButtonDefinition<bookmarks>("Remove") {
            @Override
            protected UICommand resolveCommand() {
                return modelProvider.getModel().getRemoveCommand();
            }
        });

        return actionPanel;
    }

    void addScrollEventHandler(final ScrollPanel scrollPanel) {
        scrollPanel.addScrollHandler(new ScrollHandler() {
            @Override
            public void onScroll(ScrollEvent event) {
                int currentScrollPosition = scrollPanel.getVerticalScrollPosition();
                int maxScrollPosition = scrollPanel.getMaximumVerticalScrollPosition();

                if (currentScrollPosition >= maxScrollPosition) {
                    int pageSize = getDataDisplayWidget().getPageSize();

                    // Extend the visible range of data display widget
                    Range visibleRange = getDataDisplayWidget().getVisibleRange();
                    getDataDisplayWidget().setVisibleRange(
                            visibleRange.getStart(),
                            visibleRange.getLength() + pageSize);
                }
            }
        });
    }

}
