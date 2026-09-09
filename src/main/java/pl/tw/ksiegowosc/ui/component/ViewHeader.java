package pl.tw.ksiegowosc.ui.component;

import pl.tw.ksiegowosc.ui.util.Aura;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Header;

public class ViewHeader extends Header {

    public ViewHeader(Component... children) {
        super(children);
        addClassName(Aura.VIEW_HEADER);
    }
}
