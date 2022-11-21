module distributed.serviceinjector {
    requires javafx.controls;
    requires javafx.fxml;
    requires distributed.shared;

    opens distributed.serviceinjector to javafx.fxml;
    exports distributed.serviceinjector;
}