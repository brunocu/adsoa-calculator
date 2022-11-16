package distributed.clientfx;

import javafx.fxml.FXML;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextFormatter;

import java.util.Arrays;
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
    private Spinner<Integer> spinnerTimeout;
    @FXML
    private Spinner<Integer> spinnerAck;

    public void initialize() {
        for (Spinner<Integer> spinner : Arrays.asList(spinnerTimeout, spinnerAck)) {
            spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 30));
            spinner.getEditor().setTextFormatter(
                    new TextFormatter<Integer>(INTEGER_FILTER)
            );
        }
    }
}
