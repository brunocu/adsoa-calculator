package distributed.clientfx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ClientApplication extends Application {
    private static final Properties PROPERTIES;
    private static final Path USER_CONFIG_PATH;

    static {
        try {
            USER_CONFIG_PATH = Path.of(ClientApplication.class.getProtectionDomain()
                                                              .getCodeSource()
                                                              .getLocation()
                                                              .toURI()).getParent().resolve("config.xml");
            PROPERTIES = new Properties();
            PROPERTIES.loadFromXML(
                    ClientApplication.class.getResourceAsStream("config.xml")
            );
            if (Files.exists(USER_CONFIG_PATH)) {
                PROPERTIES.loadFromXML(
                        new FileInputStream(USER_CONFIG_PATH.toFile())
                );
            }
            storeProperties();
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public static Object setProperty(String key, String value) {
        return PROPERTIES.setProperty(key, value);
    }

    public static String getProperty(String key) {
        return PROPERTIES.getProperty(key);
    }

    public static void storeProperties() throws IOException {
        PROPERTIES.storeToXML(
                new FileOutputStream(USER_CONFIG_PATH.toFile()), "App configuration"
        );
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("client-scene.fxml"));
        Parent root = loader.load();
        ClientController controller = loader.getController();

        Scene scene = new Scene(root);

        primaryStage.setTitle("Calculadora");
        primaryStage.setScene(scene);
        primaryStage.setOnHidden(e -> {
            controller.shutdown();
            Platform.exit();
        });
        primaryStage.show();
    }
}