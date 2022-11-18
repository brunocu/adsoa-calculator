module distributed.serverfx {
    requires java.desktop;
    requires javafx.controls;
    requires javafx.fxml;
    requires distributed.shared;

    opens distributed.serverfx to javafx.fxml;
    exports distributed.serverfx;
}