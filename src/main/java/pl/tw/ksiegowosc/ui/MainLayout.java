package pl.tw.ksiegowosc.ui;

import java.util.Map;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.ScrollerVariant;
import com.vaadin.flow.component.page.ColorScheme;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.sidenav.SideNavVariant;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.PermitAll;
import pl.tw.ksiegowosc.service.AppUserService;
import pl.tw.ksiegowosc.ui.component.ViewFooter;
import pl.tw.ksiegowosc.ui.component.ViewHeader;
import pl.tw.ksiegowosc.ui.component.ViewHeading;
import pl.tw.ksiegowosc.ui.util.Lucide;
import pl.tw.ksiegowosc.ui.util.Theme;

@Layout
@PermitAll
public class MainLayout extends AppLayout {

    private final transient AuthenticationContext authenticationContext;
    private final transient AppUserService appUserService;

    public MainLayout(AuthenticationContext authenticationContext, AppUserService appUserService) {
        this.authenticationContext = authenticationContext;
        this.appUserService = appUserService;
        addClassName("main-layout");
        setPrimarySection(Section.DRAWER);
        initDrawer();
    }

    private void initDrawer() {
        addToDrawer(createHeader(), createScroller(), createFooter());
    }

    private ViewHeader createHeader() {
        Avatar appLogo = new Avatar();
        appLogo.addThemeNames(Theme.AVATAR_VAADIN, Theme.AVATAR_SQUARE);
        appLogo.addThemeVariants(AvatarVariant.AURA_FILLED, AvatarVariant.XSMALL);
        appLogo.addClassName("app-logo");

        ViewHeading appName = new ViewHeading("Księgowość");

        DrawerToggle toggle = new DrawerToggle();
        toggle.addThemeNames(Theme.DRAWER_TOGGLE_PERMANENT);
        toggle.addThemeVariants(ButtonVariant.TERTIARY);

        return new ViewHeader(appLogo, appName, toggle);
    }

    private Scroller createScroller() {
        SideNav nav = new SideNav();
        nav.addThemeVariants(SideNavVariant.AURA_FILLED);
        nav.addItem(new SideNavItem("Faktury", InvoiceListView.class, Lucide.RECEIPT.create()));
        nav.addItem(new SideNavItem("Allegro", AllegroView.class, Lucide.SHOPPING_BAG.create()));
        nav.addItem(new SideNavItem("Ustawienia API", ApiSettingsView.class, Lucide.SETTINGS.create()));

        Scroller scroller = new Scroller();
        scroller.addThemeVariants(ScrollerVariant.OVERFLOW_INDICATORS);
        scroller.getElement().appendChild(nav.getElement(), new Hr().getElement());
        return scroller;
    }

    private ViewFooter createFooter() {
        String username = authenticationContext.getAuthenticatedUser(org.springframework.security.core.userdetails.UserDetails.class)
                .map(org.springframework.security.core.userdetails.UserDetails::getUsername)
                .orElse("Użytkownik");

        Avatar avatar = new Avatar(username);
        avatar.addThemeVariants(AvatarVariant.AURA_FILLED, AvatarVariant.XSMALL);
        avatar.setAbbreviation(username.isBlank() ? "?" : username.substring(0, 1).toUpperCase());

        MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY);

        MenuItem user = menuBar.addItem(avatar);
        user.add(new Text(username));
        SubMenu userMenu = user.getSubMenu();

        createThemeItems(createMenuItem(userMenu, "Motyw", Lucide.PALETTE).getSubMenu());
        userMenu.addSeparator();
        MenuItem changePassword = createMenuItem(userMenu, "Zmień hasło", Lucide.KEY_ROUND);
        changePassword.addClickListener(e -> new ChangePasswordDialog(appUserService).open());
        MenuItem logout = createMenuItem(userMenu, "Wyloguj", Lucide.LOG_OUT);
        logout.addClickListener(e -> authenticationContext.logout());

        return new ViewFooter(menuBar);
    }

    private void createThemeItems(SubMenu themeMenu) {
        MenuItem system = createMenuItem(themeMenu, "System", Lucide.MONITOR_SMARTPHONE, true);
        MenuItem light = createMenuItem(themeMenu, "Jasny", Lucide.SUN, true);
        MenuItem dark = createMenuItem(themeMenu, "Ciemny", Lucide.MOON, true);

        var schemes = Map.of(
                system, ColorScheme.Value.SYSTEM,
                light, ColorScheme.Value.LIGHT,
                dark, ColorScheme.Value.DARK
        );

        UI.getCurrent().getPage().setColorScheme(ColorScheme.Value.SYSTEM);
        system.setChecked(true);

        schemes.forEach((item, scheme) -> item.addClickListener(e -> {
            UI.getCurrent().getPage().setColorScheme(scheme);
            schemes.keySet().forEach(t -> t.setChecked(t == item));
        }));
    }

    private MenuItem createMenuItem(SubMenu subMenu, String label, Lucide icon) {
        return createMenuItem(subMenu, label, icon, false);
    }

    private MenuItem createMenuItem(SubMenu subMenu, String label, Lucide icon, boolean checkable) {
        SvgIcon svgIcon = icon.create();
        svgIcon.addClassNames("main-layout", "menu-item-icon");

        MenuItem item = subMenu.addItem(label);
        item.addComponentAsFirst(svgIcon);
        item.setCheckable(checkable);
        if (!checkable) {
            item.getStyle().set("--vaadin-item-checkmark-display", "none");
        }
        return item;
    }
}
