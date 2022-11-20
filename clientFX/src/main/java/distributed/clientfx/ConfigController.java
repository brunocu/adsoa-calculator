package distributed.clientfx;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextFormatter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

public class ConfigController {
    private static final UnaryOperator<TextFormatter.Change> INTEGER_FILTER = change -> {
        String newText = change.getControlNewText();
        if (newText.matches("[0-9]*")) {
            return change;
        }
        return null;
    };
    @FXML
    private Spinner<Integer> spinnerMinPort;
    @FXML
    private Spinner<Integer> spinnerMaxPort;
    @FXML
    private Spinner<Integer> spinnerTimeout;
    @FXML
    private Spinner<Integer> spinnerAck;

    private void forEachSpinner(BiConsumer<String, Spinner<Integer>> action) {
        final Map<String, Spinner<Integer>> spinnerMap = Map.of(
                "MIN_PORT", spinnerMinPort,
                "MAX_PORT", spinnerMaxPort,
                "TIMEOUT", spinnerTimeout,
                "MIN_ACK", spinnerAck
        );
        spinnerMap.forEach(action);
    }

    public void initialize() {
        forEachSpinner((key, spinner) -> {
            spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE,
                    Integer.parseInt(ClientApplication.getProperty(key))
            ));
            spinner.getEditor().setTextFormatter(
                    new TextFormatter<Integer>(INTEGER_FILTER)
            );
        });
    }

    @FXML
    private void saveAction(ActionEvent event) throws IOException {
        Node node = (Node) event.getSource();

        forEachSpinner((key, spinner) -> {
            ClientApplication.setProperty(key, spinner.getValue().toString());
        });
        ClientApplication.storeProperties();
        node.getScene().getWindow().hide();
    }

    @FXML
    private void cancelAction(ActionEvent event) {
        Node node = (Node) event.getSource();
        node.getScene().getWindow().hide();
    }
}
