module distributed.clientfx {
    requires javafx.controls;
    requires javafx.fxml;
    requires distributed.shared;

    opens distributed.clientfx to javafx.fxml;
    exports distributed.clientfx;
}
