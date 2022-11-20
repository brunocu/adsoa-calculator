module distributed.datafieldfx {
    requires java.desktop;
    requires javafx.controls;
    requires javafx.fxml;
    requires distributed.shared;

    opens distributed.datafieldfx to javafx.fxml;
    exports distributed.datafieldfx;
}