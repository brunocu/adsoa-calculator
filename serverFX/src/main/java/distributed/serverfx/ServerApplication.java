package distributed.serverfx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class ServerApplication extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("server-view.fxml"));
        Scene scene = new Scene(loader.load());
        ServerController controller = loader.getController();

        primaryStage.setTitle("Server");
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