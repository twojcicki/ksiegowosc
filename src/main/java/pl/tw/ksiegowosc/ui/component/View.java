package pl.tw.ksiegowosc.ui.component;

import pl.tw.ksiegowosc.ui.util.Aura;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Main;

public class View extends Main {

    public View(Component... components) {
        super(components);
        addClassName(Aura.VIEW);
    }
}
