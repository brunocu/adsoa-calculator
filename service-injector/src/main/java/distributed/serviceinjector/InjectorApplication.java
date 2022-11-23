package distributed.serviceinjector;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class InjectorApplication extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(InjectorApplication.class.getResource("injector-view.fxml"));
        Parent root = loader.load();
        InjectorController controller = loader.getController();

        Scene scene = new Scene(root);

        primaryStage.setTitle("Inyector");
        primaryStage.setScene(scene);
        primaryStage.setOnHidden(e -> {
            controller.shutdown();
            Platform.exit();
        });
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}