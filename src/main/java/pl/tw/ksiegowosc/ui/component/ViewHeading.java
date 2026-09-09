package pl.tw.ksiegowosc.ui.component;

import pl.tw.ksiegowosc.ui.util.Aura;
import com.vaadin.flow.component.html.Div;

public class ViewHeading extends Div {

    public ViewHeading(String text) {
        super(text);
        addClassName(Aura.VIEW_HEADING);
    }
}
