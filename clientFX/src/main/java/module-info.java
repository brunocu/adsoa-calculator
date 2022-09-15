module distributed.clientfx {
    requires javafx.controls;
    requires javafx.fxml;

    opens distributed.clientfx to javafx.fxml;
    exports distributed.clientfx;
}
