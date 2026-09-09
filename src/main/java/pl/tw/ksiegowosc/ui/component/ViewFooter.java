package pl.tw.ksiegowosc.ui.component;

import pl.tw.ksiegowosc.ui.util.Aura;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Footer;

public class ViewFooter extends Footer {

    public ViewFooter(Component... children) {
        super(children);
        addClassName(Aura.VIEW_FOOTER);
    }
}
